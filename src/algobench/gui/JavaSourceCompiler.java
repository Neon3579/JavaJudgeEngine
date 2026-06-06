package algobench.gui;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * 에디터/첨부 Java 소스를 JDK 내장 컴파일러({@code javax.tools})로 임시 디렉토리에 컴파일한다.
 *
 * <p>순수 Java SE — 외부 {@code javac} 프로세스 없이 in-process 컴파일. JDK(JRE 아님) 필요.
 * 컴파일 산출물 디렉토리와 {@code main}을 가진 클래스의 FQN을 반환하며, 호출측은 이를
 * {@code java -cp <classesDir> <fqn>} 외부 프로세스로 격리 실행한다.
 *
 * <p>생성한 임시 디렉토리는 JVM 종료 시 재귀 삭제한다(shutdown hook).
 */
final class JavaSourceCompiler {

    private JavaSourceCompiler() {
    }

    /** 컴파일 결과 — 클래스패스 루트 + 실행 FQN. */
    record CompileResult(Path classesDir, String mainClassFqn) {
    }

    /** 컴파일 실패(진단 메시지 포함). */
    static final class CompileException extends Exception {
        CompileException(String message) {
            super(message);
        }
    }

    private static final Pattern PKG = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
    private static final Pattern PUBLIC_TYPE = Pattern.compile(
            "(?m)\\bpublic\\s+(?:final\\s+|abstract\\s+|sealed\\s+|strictfp\\s+)*"
                    + "(?:class|interface|enum|record)\\s+(\\w+)");
    private static final Pattern ANY_TYPE = Pattern.compile(
            "(?m)\\b(?:class|interface|enum|record)\\s+(\\w+)");

    private static final Set<Path> TEMP_DIRS = ConcurrentHashMap.newKeySet();
    private static volatile boolean hookRegistered = false;

    /** 에디터에 입력한 소스 문자열을 컴파일한다. */
    static CompileResult compileSource(String source) throws CompileException {
        if (source == null || source.isBlank()) {
            throw new CompileException("소스 코드가 비어 있습니다.");
        }
        String className = detectTopType(source);
        String pkg = detectPackage(source);
        String fqn = pkg.isEmpty() ? className : pkg + "." + className;

        Path tmp = newTempDir();
        Path srcFile = tmp.resolve(className + ".java"); // public 클래스명 = 파일명 규칙
        try {
            Files.writeString(srcFile, source, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new CompileException("임시 소스 작성 실패: " + e.getMessage());
        }
        compile(tmp, List.of(srcFile));
        return new CompileResult(tmp, fqn);
    }

    /** 첨부한 {@code .java} 파일을 컴파일한다. */
    static CompileResult compileFile(Path javaFile) throws CompileException {
        String source;
        try {
            source = Files.readString(javaFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new CompileException(".java 읽기 실패: " + e.getMessage());
        }
        String className = detectTopType(source);
        String pkg = detectPackage(source);
        String fqn = pkg.isEmpty() ? className : pkg + "." + className;

        Path tmp = newTempDir();
        compile(tmp, List.of(javaFile));
        return new CompileResult(tmp, fqn);
    }

    // ── 내부 ──

    private static void compile(Path outDir, List<Path> sources) throws CompileException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new CompileException(
                    "JDK 컴파일러를 찾을 수 없습니다 (JRE로 실행 중일 수 있음 — JDK 필요).");
        }
        DiagnosticCollector<JavaFileObject> diags = new DiagnosticCollector<>();
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(diags, null, StandardCharsets.UTF_8)) {
            List<java.io.File> files = new ArrayList<>();
            for (Path p : sources) {
                files.add(p.toFile());
            }
            Iterable<? extends JavaFileObject> units = fm.getJavaFileObjectsFromFiles(files);
            List<String> options = List.of("-encoding", "UTF-8", "-d", outDir.toString());
            boolean ok = compiler.getTask(null, fm, diags, options, null, units).call();
            if (!ok || hasError(diags)) {
                throw new CompileException(formatDiagnostics(diags));
            }
        } catch (IOException e) {
            throw new CompileException("컴파일 입출력 오류: " + e.getMessage());
        }
    }

    private static boolean hasError(DiagnosticCollector<JavaFileObject> diags) {
        for (Diagnostic<? extends JavaFileObject> d : diags.getDiagnostics()) {
            if (d.getKind() == Diagnostic.Kind.ERROR) {
                return true;
            }
        }
        return false;
    }

    private static String formatDiagnostics(DiagnosticCollector<JavaFileObject> diags) {
        StringBuilder sb = new StringBuilder("컴파일 오류:");
        for (Diagnostic<? extends JavaFileObject> d : diags.getDiagnostics()) {
            if (d.getKind() != Diagnostic.Kind.ERROR && d.getKind() != Diagnostic.Kind.WARNING) {
                continue;
            }
            sb.append('\n').append(d.getKind() == Diagnostic.Kind.ERROR ? "  [오류] " : "  [경고] ");
            if (d.getLineNumber() > 0) {
                sb.append("L").append(d.getLineNumber()).append(": ");
            }
            sb.append(d.getMessage(Locale.getDefault()));
        }
        return sb.toString();
    }

    private static String detectPackage(String source) {
        Matcher m = PKG.matcher(stripLineComments(source));
        return m.find() ? m.group(1) : "";
    }

    private static String detectTopType(String source) throws CompileException {
        String cleaned = stripLineComments(source);
        Matcher pub = PUBLIC_TYPE.matcher(cleaned);
        if (pub.find()) {
            return pub.group(1);
        }
        Matcher any = ANY_TYPE.matcher(cleaned);
        if (any.find()) {
            return any.group(1);
        }
        throw new CompileException("클래스 선언을 찾을 수 없습니다 (public class … 필요).");
    }

    /** {@code //} 한 줄 주석 제거 — 주석 속 'class' 오탐 방지(간이). 문자열 리터럴은 무시. */
    private static String stripLineComments(String source) {
        StringBuilder sb = new StringBuilder(source.length());
        for (String line : source.split("\n", -1)) {
            int idx = line.indexOf("//");
            sb.append(idx >= 0 ? line.substring(0, idx) : line).append('\n');
        }
        return sb.toString();
    }

    private static Path newTempDir() throws CompileException {
        try {
            Path dir = Files.createTempDirectory("algobench-gui-");
            TEMP_DIRS.add(dir);
            registerCleanupHook();
            return dir;
        } catch (IOException e) {
            throw new CompileException("임시 디렉토리 생성 실패: " + e.getMessage());
        }
    }

    private static synchronized void registerCleanupHook() {
        if (hookRegistered) {
            return;
        }
        hookRegistered = true;
        Runtime.getRuntime().addShutdownHook(new Thread(JavaSourceCompiler::cleanup, "gui-tmp-cleanup"));
    }

    private static void cleanup() {
        for (Path dir : TEMP_DIRS) {
            deleteRecursive(dir);
        }
    }

    private static void deleteRecursive(Path dir) {
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignore) {
                    // best-effort
                }
            });
        } catch (IOException | UncheckedIOException ignore) {
            // best-effort
        }
    }
}
