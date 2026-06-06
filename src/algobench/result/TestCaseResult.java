package algobench.result;

import java.time.Duration;
import java.util.Objects;

/**
 * 단일 테스트 케이스 채점 결과 (불변).
 *
 * <p>{@code expectedOutput}/{@code actualOutput}/{@code errorMessage}는 {@code null}을 빈 문자열로 보정한다.
 * 통과 시 {@code errorMessage}는 "". 실패 사유 토큰: TIMEOUT / RUNTIME_ERROR / WRONG_ANSWER / EXCEPTION.
 */
public final class TestCaseResult {

    private final int testCaseIndex;
    private final boolean passed;
    private final String expectedOutput;
    private final String actualOutput;
    private final Duration executionTime;
    private final String errorMessage;

    public TestCaseResult(int testCaseIndex, boolean passed, String expectedOutput,
                          String actualOutput, Duration executionTime, String errorMessage) {
        this.testCaseIndex = testCaseIndex;
        this.passed = passed;
        this.expectedOutput = expectedOutput == null ? "" : expectedOutput;
        this.actualOutput = actualOutput == null ? "" : actualOutput;
        this.executionTime = Objects.requireNonNull(executionTime, "executionTime");
        this.errorMessage = errorMessage == null ? "" : errorMessage;
    }

    public int getTestCaseIndex() {
        return testCaseIndex;
    }

    public boolean isPassed() {
        return passed;
    }

    public String getExpectedOutput() {
        return expectedOutput;
    }

    public String getActualOutput() {
        return actualOutput;
    }

    public Duration getExecutionTime() {
        return executionTime;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
