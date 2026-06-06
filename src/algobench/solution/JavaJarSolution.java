package algobench.solution;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * 같은 JVM 안에서 {@code .class}/{@code .jar} 풀이를 실행한다 (semi-trusted, 협조적 Java 코드용).
 *
 * <p>per-call {@link URLClassLoader}로 클래스를 매번 새로 로드(static 상태 누수 방지)하고
 * {@code main(String[])}을 리플렉션으로 호출한다.
 *
 * <p><b>주의 (설계 노트):</b>
 * <ul>
 *   <li>{@code System.in/out/err}은 JVM 전역이라 {@link #STREAM_LOCK}으로 실행 구간을 직렬화한다(NFR-05).</li>
 *   <li>(G3) 순서: <b>스트림 스왑 → 클래스 로드(static init 출력도 캡처, 타이머 밖) → 타이머 시작 → main invoke</b>.</li>
 *   <li>타임아웃은 데몬 스레드 + {@code Future.get(timeout)} best-effort. interrupt 무시 무한 루프는
 *       강제 종료 불가 → 데몬으로 두어 메인 종료를 막지 않는다. {@code System.exit()}는 AlgoBench JVM 전체를 종료시킨다.</li>
 * </ul>
 */
public final class JavaJarSolution implements Solution {

    /** System.in/out/err 전역 스트림 교체 구간 직렬화용 락. */
    private static final Object STREAM_LOCK = new Object();

    private final Path filePath;
    private final String name;
    private final URL classpathRoot;
    private final String className;

    public JavaJarSolution(Path filePath) {
        this(filePath, null);
    }

    public JavaJarSolution(Path filePath, String name) {
        this.filePath = Objects.requireNonNull(filePath, "filePath").toAbsolutePath();
        if (!Files.exists(this.filePath)) {
            throw new IllegalArgumentException("파일이 없습니다: " + this.filePath);
        }
        String fileName = this.filePath.getFileName().toString();
        try {
            if (fileName.endsWith(".class")) {
                this.className = fileName.substring(0, fileName.length() - ".class".length());
                this.classpathRoot = this.filePath.getParent().toUri().toURL();
            } else if (fileName.endsWith(".jar")) {
                this.className = readMainClass(this.filePath);
                this.classpathRoot = this.filePath.toUri().toURL();
            } else {
                throw new IllegalArgumentException(".class/.jar 만 지원합니다: " + fileName);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("풀이 로드 실패: " + e.getMessage(), e);
        }
        this.name = (name == null || name.isBlank()) ? className : name;
        validateMain();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public ExecutionResult execute(String input, Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        synchronized (STREAM_LOCK) {
            InputStream originalIn = System.in;
            PrintStream originalOut = System.out;
            PrintStream originalErr = System.err;
            ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
            ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
            PrintStream outPs = new PrintStream(outBuf, true, StandardCharsets.UTF_8);
            PrintStream errPs = new PrintStream(errBuf, true, StandardCharsets.UTF_8);
            ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "javajar-" + name);
                t.setDaemon(true);
                return t;
            });
            URLClassLoader loader = null;
            long startNs = 0;
            try {
                // 1) 스트림 스왑 (입력 주입 + 출력 캡처)
                byte[] inBytes = input == null ? new byte[0] : input.getBytes(StandardCharsets.UTF_8);
                System.setIn(new ByteArrayInputStream(inBytes));
                System.setOut(outPs);
                System.setErr(errPs);

                // 2) (G3) 스왑 후 로드 → static initializer 출력도 캡처(타이머 밖)
                loader = newLoader();
                Class<?> clazz = Class.forName(className, true, loader);
                Method main = clazz.getMethod("main", String[].class);

                // 3) 타이머 시작 → 데몬 워커에서 main invoke
                startNs = System.nanoTime();
                Future<?> future = worker.submit(() -> {
                    main.invoke(null, (Object) new String[0]);
                    return null;
                });
                try {
                    future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
                    long elapsed = System.nanoTime() - startNs;
                    return result(outBuf, errBuf, outPs, errPs, 0, elapsed, false, null);
                } catch (TimeoutException te) {
                    future.cancel(true);
                    long elapsed = System.nanoTime() - startNs;
                    return result(outBuf, errBuf, outPs, errPs, -1, elapsed, true,
                            "[TIMEOUT] 시간 제한 초과");
                } catch (ExecutionException ee) {
                    long elapsed = System.nanoTime() - startNs;
                    Throwable cause = ee.getCause();
                    if (cause instanceof InvocationTargetException && cause.getCause() != null) {
                        cause = cause.getCause();
                    }
                    return result(outBuf, errBuf, outPs, errPs, 1, elapsed, false, stackTrace(cause));
                }
            } catch (Exception e) {
                long elapsed = startNs == 0 ? 0 : System.nanoTime() - startNs;
                return result(outBuf, errBuf, outPs, errPs, 1, elapsed, false,
                        "실행 준비 실패: " + e);
            } finally {
                // 4) 스트림 복구 → 워커 정리 → 로더 닫기
                System.setIn(originalIn);
                System.setOut(originalOut);
                System.setErr(originalErr);
                worker.shutdownNow();
                if (loader != null) {
                    try {
                        loader.close();
                    } catch (IOException ignore) {
                        // 닫기 실패는 무시
                    }
                }
            }
        }
    }

    private static ExecutionResult result(ByteArrayOutputStream outBuf, ByteArrayOutputStream errBuf,
                                          PrintStream outPs, PrintStream errPs,
                                          int exitCode, long elapsedNs, boolean timedOut,
                                          String extraErr) {
        outPs.flush();
        errPs.flush();
        String stdout = outBuf.toString(StandardCharsets.UTF_8);
        String stderr = errBuf.toString(StandardCharsets.UTF_8);
        if (extraErr != null && !extraErr.isEmpty()) {
            stderr = stderr.isEmpty() ? extraErr : stderr + "\n" + extraErr;
        }
        return new ExecutionResult(stdout, stderr, exitCode, Duration.ofNanos(elapsedNs), timedOut);
    }

    private URLClassLoader newLoader() {
        // 플랫폼 클래스로더를 부모로 → 풀이 클래스는 매번 새로 로드(static 상태 격리), java.base만 공유
        return new URLClassLoader(new URL[]{classpathRoot}, ClassLoader.getPlatformClassLoader());
    }

    /** 생성 시점에 main 시그니처를 1회 검증한다(인스턴스 캐시 금지 — 검증용 로더는 즉시 닫음). */
    private void validateMain() {
        try (URLClassLoader cl = newLoader()) {
            Class<?> c = Class.forName(className, false, cl);
            Method m = c.getMethod("main", String[].class);
            int mod = m.getModifiers();
            if (!Modifier.isStatic(mod) || !Modifier.isPublic(mod)) {
                throw new IllegalArgumentException("public static void main(String[])가 필요합니다: " + className);
            }
        } catch (IOException | ClassNotFoundException | NoSuchMethodException e) {
            throw new IllegalArgumentException("main 검증 실패(" + className + "): " + e.getMessage(), e);
        }
    }

    private static String readMainClass(Path jar) throws IOException {
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            Manifest manifest = jarFile.getManifest();
            String mainClass = manifest == null ? null
                    : manifest.getMainAttributes().getValue("Main-Class");
            if (mainClass == null || mainClass.isBlank()) {
                throw new IOException("JAR manifest에 Main-Class가 없습니다: " + jar);
            }
            return mainClass.trim();
        }
    }

    private static String stackTrace(Throwable t) {
        if (t == null) {
            return "알 수 없는 오류";
        }
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
