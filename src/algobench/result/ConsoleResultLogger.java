package algobench.result;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 사람이 읽기 좋은 풀이별 콘솔 출력 (NFR-06).
 *
 * <p>풀이명, 통과 N/M, (G2) 총 실행 시간 소수 ms, 실패 케이스 번호·첫 실패 사유,
 * 기대/실제 출력 요약(truncate)을 표시한다.
 */
public final class ConsoleResultLogger implements ResultLogger {

    private static final int SUMMARY_MAX = 80;

    private final PrintStream out;

    public ConsoleResultLogger() {
        this(System.out);
    }

    public ConsoleResultLogger(PrintStream out) {
        this.out = Objects.requireNonNull(out, "out");
    }

    @Override
    public void log(BenchmarkResult r) {
        double ms = r.getTotalExecutionTime().toNanos() / 1_000_000.0;
        out.println("────────────────────────────────────────────────────────────");
        out.printf("풀이: %s%n", r.getSolutionName());
        out.printf("  결과: %s  (%d/%d 통과)%n",
                r.isAllPassed() ? "ALL PASS" : "FAIL", r.getPassedCount(), r.getTotalCount());
        out.printf(Locale.ROOT, "  총 실행 시간: %.3f ms%n", ms);

        List<TestCaseResult> failed = new ArrayList<>();
        for (TestCaseResult c : r.getCaseResults()) {
            if (!c.isPassed()) {
                failed.add(c);
            }
        }
        if (!failed.isEmpty()) {
            StringBuilder idxs = new StringBuilder();
            for (TestCaseResult c : failed) {
                if (idxs.length() > 0) {
                    idxs.append(", ");
                }
                idxs.append(c.getTestCaseIndex());
            }
            out.printf("  실패 케이스: %s%n", idxs);
            TestCaseResult first = failed.get(0);
            out.printf("  첫 실패(케이스 %d): %s%n",
                    first.getTestCaseIndex(), oneLine(first.getErrorMessage()));
            out.printf("    기대: %s%n", summarize(first.getExpectedOutput()));
            out.printf("    실제: %s%n", summarize(first.getActualOutput()));
        }
    }

    private static String oneLine(String s) {
        if (s == null || s.isEmpty()) {
            return "(사유 없음)";
        }
        s = s.replace("\r", "");
        int nl = s.indexOf('\n');
        return nl >= 0 ? s.substring(0, nl) : s;
    }

    private static String summarize(String s) {
        if (s == null) {
            s = "";
        }
        String flat = s.replace("\r", "").replace("\n", "\\n");
        if (flat.length() > SUMMARY_MAX) {
            flat = flat.substring(0, SUMMARY_MAX) + "…";
        }
        return flat;
    }
}
