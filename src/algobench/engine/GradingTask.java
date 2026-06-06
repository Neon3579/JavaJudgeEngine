package algobench.engine;

import algobench.compare.OutputComparator;
import algobench.domain.Problem;
import algobench.domain.TestCase;
import algobench.result.BenchmarkResult;
import algobench.result.TestCaseResult;
import algobench.solution.ExecutionResult;
import algobench.solution.Solution;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * 한 풀이를 한 문제 전체 케이스에 대해 채점하는 단위 작업 (설계도 «CallableResult» = {@code Callable<BenchmarkResult>}).
 *
 * <p>케이스마다 {@code solution.execute(input, timeLimit)} → 판정 → {@link TestCaseResult} 수집.
 * 판정 우선순위: <b>TIMEOUT → RUNTIME_ERROR(exitCode≠0) → WRONG_ANSWER → 통과</b>.
 *
 * <p><b>예외 격리 (NFR-04):</b> 어떤 케이스에서 미캡슐 예외가 나도 잡아서 {@code errorMessage}에 기록하고
 * 다음 케이스로 계속 진행한다. 풀이 1개의 크래시가 전체 벤치마크를 깨지 않는다.
 */
public final class GradingTask implements Callable<BenchmarkResult> {

    private static final int STDERR_SUMMARY_MAX = 200;

    private final Problem problem;
    private final Solution solution;
    private final OutputComparator comparator;

    public GradingTask(Problem problem, Solution solution, OutputComparator comparator) {
        this.problem = Objects.requireNonNull(problem, "problem");
        this.solution = Objects.requireNonNull(solution, "solution");
        this.comparator = Objects.requireNonNull(comparator, "comparator");
    }

    @Override
    public BenchmarkResult call() {
        List<TestCaseResult> caseResults = new ArrayList<>();
        Duration total = Duration.ZERO;
        boolean allPassed = true;
        Duration timeLimit = problem.getTimeLimit();

        for (TestCase tc : problem.getTestCases()) {
            TestCaseResult result;
            try {
                ExecutionResult exec = solution.execute(tc.getInput(), timeLimit);
                total = total.plus(exec.getExecutionTime());
                result = judge(tc, exec);
            } catch (RuntimeException e) {
                // 미캡슐 예외 격리 — 케이스 단위로 기록 후 계속
                result = new TestCaseResult(tc.getIndex(), false, tc.getExpectedOutput(), "",
                        Duration.ZERO, "EXCEPTION: " + e);
            }
            if (!result.isPassed()) {
                allPassed = false;
            }
            caseResults.add(result);
        }
        return new BenchmarkResult(solution.getName(), allPassed, total, caseResults);
    }

    private TestCaseResult judge(TestCase tc, ExecutionResult exec) {
        String actual = exec.getStdout();
        Duration t = exec.getExecutionTime();
        if (exec.isTimedOut()) {
            return new TestCaseResult(tc.getIndex(), false, tc.getExpectedOutput(), actual, t,
                    "TIMEOUT: 시간 제한 " + problem.getTimeLimit().toMillis() + "ms 초과");
        }
        if (exec.getExitCode() != 0) {
            return new TestCaseResult(tc.getIndex(), false, tc.getExpectedOutput(), actual, t,
                    "RUNTIME_ERROR: exitCode=" + exec.getExitCode() + summarizeStderr(exec.getStderr()));
        }
        if (!tc.matches(actual, comparator)) {
            return new TestCaseResult(tc.getIndex(), false, tc.getExpectedOutput(), actual, t,
                    "WRONG_ANSWER");
        }
        return new TestCaseResult(tc.getIndex(), true, tc.getExpectedOutput(), actual, t, "");
    }

    private static String summarizeStderr(String stderr) {
        if (stderr == null || stderr.isBlank()) {
            return "";
        }
        String first = stderr.strip();
        int nl = first.indexOf('\n');
        if (nl >= 0) {
            first = first.substring(0, nl);
        }
        if (first.length() > STDERR_SUMMARY_MAX) {
            first = first.substring(0, STDERR_SUMMARY_MAX);
        }
        return " | " + first;
    }
}
