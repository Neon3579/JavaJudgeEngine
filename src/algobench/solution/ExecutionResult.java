package algobench.solution;

import java.time.Duration;
import java.util.Objects;

/**
 * 풀이 1회 실행 결과 (불변, non-null 보장).
 *
 * <p>{@code stdout}/{@code stderr}는 {@code null}을 빈 문자열로 보정한다.
 * {@code executionTime}은 나노초 정밀도를 보존한다(G2 — 정수 ms truncate 금지).
 *
 * <p>exitCode 관례: 정상 0 / Java 풀이 예외 1 / 외부 프로세스 비정상 실제 코드 / 타임아웃 -1({@code timedOut=true}).
 */
public final class ExecutionResult {

    private final String stdout;
    private final String stderr;
    private final int exitCode;
    private final Duration executionTime;
    private final boolean timedOut;

    public ExecutionResult(String stdout, String stderr, int exitCode,
                           Duration executionTime, boolean timedOut) {
        this.stdout = stdout == null ? "" : stdout;
        this.stderr = stderr == null ? "" : stderr;
        this.exitCode = exitCode;
        this.executionTime = Objects.requireNonNull(executionTime, "executionTime");
        this.timedOut = timedOut;
    }

    /** 정상 종료(exitCode 0)이고 타임아웃이 아니면 성공. */
    public boolean isSuccess() {
        return exitCode == 0 && !timedOut;
    }

    public String getStdout() {
        return stdout;
    }

    public String getStderr() {
        return stderr;
    }

    public int getExitCode() {
        return exitCode;
    }

    public Duration getExecutionTime() {
        return executionTime;
    }

    public boolean isTimedOut() {
        return timedOut;
    }
}
