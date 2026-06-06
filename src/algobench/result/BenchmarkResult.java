package algobench.result;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * 한 풀이를 한 문제 전체 케이스에 대해 채점한 결과 (불변).
 *
 * <p>케이스 결과 목록은 {@link List#copyOf}로 방어적 복사해 불변성을 보장한다(NFR-05).
 */
public final class BenchmarkResult {

    private final String solutionName;
    private final boolean allPassed;
    private final Duration totalExecutionTime;
    private final List<TestCaseResult> caseResults;

    public BenchmarkResult(String solutionName, boolean allPassed,
                           Duration totalExecutionTime, List<TestCaseResult> caseResults) {
        Objects.requireNonNull(solutionName, "solutionName");
        if (solutionName.isBlank()) {
            throw new IllegalArgumentException("solutionName must be non-empty");
        }
        this.solutionName = solutionName;
        this.allPassed = allPassed;
        this.totalExecutionTime = Objects.requireNonNull(totalExecutionTime, "totalExecutionTime");
        Objects.requireNonNull(caseResults, "caseResults");
        this.caseResults = List.copyOf(caseResults); // 방어적 복사 → 불변
    }

    public String getSolutionName() {
        return solutionName;
    }

    public boolean isAllPassed() {
        return allPassed;
    }

    public Duration getTotalExecutionTime() {
        return totalExecutionTime;
    }

    /** 불변 리스트 반환. */
    public List<TestCaseResult> getCaseResults() {
        return caseResults;
    }

    public int getPassedCount() {
        int count = 0;
        for (TestCaseResult r : caseResults) {
            if (r.isPassed()) {
                count++;
            }
        }
        return count;
    }

    public int getTotalCount() {
        return caseResults.size();
    }
}
