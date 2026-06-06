package algobench.solution;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 외부 프로세스로 풀이를 실행한다 ({@code ProcessBuilder}). C/C++/Python 등 — 자체 OS 프로세스라 완전 병렬.
 *
 * <p>인코딩: stdin/stdout/stderr UTF-8, env {@code PYTHONIOENCODING=utf-8}.
 * 첫 토큰이 {@code java}/{@code java.exe}면 자식 JVM으로 보고 {@code -Dstdout.encoding}/{@code -Dstderr.encoding}이
 * 없을 때 {@code =UTF-8}을 삽입한다(Windows-Korean 파이프 출력 깨짐 방지).
 *
 * <p>타임아웃: {@code waitFor(timeout)} 초과 시 손자 프로세스까지 {@code destroyForcibly}로 확실히 종료.
 * stdout/stderr는 별도 reader 스레드로 동시 판독하고, 타임아웃 후엔 짧은 grace로 부분 캡처한다.
 */
public final class ExternalProcessSolution implements Solution {

    private static final long READER_GRACE_MS = 500;
    private static final long KILL_WAIT_MS = 200;

    private final String name;
    private final List<String> commandParts;

    public ExternalProcessSolution(String command) {
        this(command, deriveName(command));
    }

    public ExternalProcessSolution(String command, String name) {
        Objects.requireNonNull(command, "command");
        this.commandParts = withEncodingArgs(tokenize(command));
        this.name = (name == null || name.isBlank()) ? command : name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public ExecutionResult execute(String input, Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        long startNs = System.nanoTime();
        ExecutorService readers = Executors.newFixedThreadPool(2, daemonFactory("extproc-reader"));
        Process process = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(commandParts);
            pb.environment().put("PYTHONIOENCODING", "utf-8");
            process = pb.start();

            // stdin 주입 후 닫기
            try (OutputStream os = process.getOutputStream()) {
                if (input != null && !input.isEmpty()) {
                    os.write(input.getBytes(StandardCharsets.UTF_8));
                }
            } catch (IOException ignore) {
                // 프로세스가 입력을 받기 전에 종료했을 수 있음 — 무시하고 진행
            }

            Process p = process;
            Future<String> outF = readers.submit(() -> readAll(p.getInputStream()));
            Future<String> errF = readers.submit(() -> readAll(p.getErrorStream()));

            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                killTree(process);
                Duration elapsed = Duration.ofNanos(System.nanoTime() - startNs);
                return new ExecutionResult(grace(outF), grace(errF), -1, elapsed, true);
            }
            int exit = process.exitValue();
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startNs);
            return new ExecutionResult(grace(outF), grace(errF), exit, elapsed, false);

        } catch (IOException e) {
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startNs);
            return new ExecutionResult("", "프로세스 실행 실패: " + e.getMessage(), -1, elapsed, false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) {
                killTree(process);
            }
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startNs);
            return new ExecutionResult("", "인터럽트됨: " + e.getMessage(), -1, elapsed, true);
        } finally {
            readers.shutdownNow();
        }
    }

    // --- 프로세스 트리 종료 ---

    private static void killTree(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        try {
            process.waitFor(KILL_WAIT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // 남은 손자 프로세스 재조회 후 정리
        process.descendants().forEach(ProcessHandle::destroyForcibly);
    }

    private static String readAll(InputStream in) {
        try {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static String grace(Future<String> f) {
        try {
            return f.get(READER_GRACE_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        } catch (ExecutionException | TimeoutException e) {
            f.cancel(true);
            return "";
        }
    }

    private static ThreadFactory daemonFactory(String prefix) {
        return r -> {
            Thread t = new Thread(r, prefix);
            t.setDaemon(true);
            return t;
        };
    }

    // --- 명령 토크나이저 / 인코딩 인자 ---

    /** 공백 구분 + double-quote 묶음. backslash escape·중첩 따옴표 미지원. unmatched quote는 예외. */
    static List<String> tokenize(String command) {
        List<String> tokens = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        boolean hasToken = false;
        for (int i = 0; i < command.length(); i++) {
            char c = command.charAt(i);
            if (c == '"') {
                inQuote = !inQuote;
                hasToken = true;
            } else if (!inQuote && Character.isWhitespace(c)) {
                if (hasToken) {
                    tokens.add(cur.toString());
                    cur.setLength(0);
                    hasToken = false;
                }
            } else {
                cur.append(c);
                hasToken = true;
            }
        }
        if (inQuote) {
            throw new IllegalArgumentException("따옴표가 닫히지 않았습니다: " + command);
        }
        if (hasToken) {
            tokens.add(cur.toString());
        }
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("빈 명령입니다");
        }
        return tokens;
    }

    /** 첫 토큰이 java면 stdout/stderr.encoding=UTF-8을 (없을 때) java 토큰 바로 뒤에 삽입. */
    static List<String> withEncodingArgs(List<String> tokens) {
        String base = Path.of(tokens.get(0)).getFileName().toString().toLowerCase(Locale.ROOT);
        boolean isJava = base.equals("java") || base.equals("java.exe");
        if (!isJava) {
            return tokens;
        }
        boolean hasOut = tokens.stream().anyMatch(t -> t.startsWith("-Dstdout.encoding="));
        boolean hasErr = tokens.stream().anyMatch(t -> t.startsWith("-Dstderr.encoding="));
        List<String> result = new ArrayList<>(tokens);
        if (!hasErr) {
            result.add(1, "-Dstderr.encoding=UTF-8");
        }
        if (!hasOut) {
            result.add(1, "-Dstdout.encoding=UTF-8");
        }
        return result;
    }

    private static String deriveName(String command) {
        List<String> tokens = tokenize(command);
        String last = tokens.get(tokens.size() - 1);
        try {
            String fileName = Path.of(last).getFileName().toString();
            if (!fileName.isBlank()) {
                return fileName;
            }
        } catch (RuntimeException ignore) {
            // 경로로 해석 불가 → 전체 명령을 이름으로
        }
        return command;
    }
}
