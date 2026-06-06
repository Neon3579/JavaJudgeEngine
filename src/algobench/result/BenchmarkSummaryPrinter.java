package algobench.result;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 여러 풀이의 벤치마크 결과를 나란히 비교하는 순위 요약 출력 (G1).
 *
 * <p>AlgoBench의 본질 산출물 — PDF 1.3 "여러 풀이의 실행 시간·실패 원인 비교"를 직접 충족한다.
 * 정렬: <b>전체 통과 우선 → 총 실행 시간 오름차순</b>. 가장 빠른 정답 풀이를 {@code ★}로 강조한다.
 *
 * <p>결과 객체만 읽는 순수 함수라 스레드 안전하다.
 */
public final class BenchmarkSummaryPrinter {

    private static final int NAME_WIDTH = 24;

    /**
     * 전 풀이를 비교 표로 출력한다.
     *
     * @param results 풀이별 벤치마크 결과(원본 불변경 — 내부에서 복사 후 정렬)
     * @param out     출력 스트림
     */
    public void printComparison(List<BenchmarkResult> results, PrintStream out) {
        Objects.requireNonNull(out, "out");
        out.println();
        out.println("============================ 벤치마크 비교 요약 ============================");
        if (results == null || results.isEmpty()) {
            out.println("(비교할 결과가 없습니다)");
            return;
        }

        List<BenchmarkResult> sorted = new ArrayList<>(results);
        sorted.sort(Comparator
                .comparing(BenchmarkResult::isAllPassed, Comparator.reverseOrder()) // 통과 우선
                .thenComparingLong(r -> r.getTotalExecutionTime().toNanos()));       // 빠른 순

        BenchmarkResult fastestCorrect = null;
        for (BenchmarkResult r : sorted) {
            if (r.isAllPassed()) {
                fastestCorrect = r;
                break;
            }
        }

        out.printf("%-4s %-24s %-6s %-9s %12s  %s%n",
                "순위", "풀이", "결과", "통과", "총시간(ms)", "비고");
        out.println("---------------------------------------------------------------------------");

        int rank = 1;
        for (BenchmarkResult r : sorted) {
            double ms = r.getTotalExecutionTime().toNanos() / 1_000_000.0;
            String status = r.isAllPassed() ? "PASS" : "FAIL";
            String passes = "(" + r.getPassedCount() + "/" + r.getTotalCount() + ")";
            String note = (r == fastestCorrect) ? "★ 최속 정답" : firstFailReason(r);
            out.printf(Locale.ROOT, "%-4d %-24s %-6s %-9s %12.3f  %s%n",
                    rank, fit(r.getSolutionName()), status, passes, ms, note);
            rank++;
        }
        out.println("===========================================================================");
    }

    private static String firstFailReason(BenchmarkResult r) {
        if (r.isAllPassed()) {
            return "";
        }
        for (TestCaseResult c : r.getCaseResults()) {
            if (!c.isPassed() && !c.getErrorMessage().isEmpty()) {
                String msg = c.getErrorMessage().replace("\r", "");
                int nl = msg.indexOf('\n');
                return nl >= 0 ? msg.substring(0, nl) : msg;
            }
        }
        return "오답";
    }

    private static String fit(String name) {
        if (name == null) {
            return "";
        }
        if (name.length() > NAME_WIDTH) {
            return name.substring(0, NAME_WIDTH - 1) + "…";
        }
        return name;
    }
}
