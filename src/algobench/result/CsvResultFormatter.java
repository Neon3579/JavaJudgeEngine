package algobench.result;

import java.util.Locale;

/**
 * CSV 한 행 포매터 (RFC 4180 escaping).
 *
 * <p>컬럼: {@code solutionName,allPassed,passedCount,totalCount,totalExecutionTimeMs,
 * failedCaseIndexes,firstErrorMessage}.
 * <ul>
 *   <li>(G2) {@code totalExecutionTimeMs}는 {@code toNanos()/1_000_000.0} 소수 ms — 마이크로초대 풀이가 0으로 뭉개지지 않음.</li>
 *   <li>{@code failedCaseIndexes}는 실패 케이스 번호를 {@code ;}로 결합.</li>
 *   <li>{@code firstErrorMessage}는 첫 실패 사유의 첫 줄 200자.</li>
 *   <li>comma/quote/newline 포함 셀은 {@code "}로 감싸고 내부 {@code "} → {@code ""}.</li>
 * </ul>
 */
public final class CsvResultFormatter implements ResultFormatter {

    private static final int ERROR_MAX = 200;

    @Override
    public String header() {
        return "solutionName,allPassed,passedCount,totalCount,totalExecutionTimeMs,failedCaseIndexes,firstErrorMessage";
    }

    @Override
    public String format(BenchmarkResult r) {
        double ms = r.getTotalExecutionTime().toNanos() / 1_000_000.0;

        StringBuilder failed = new StringBuilder();
        String firstError = "";
        for (TestCaseResult c : r.getCaseResults()) {
            if (!c.isPassed()) {
                if (failed.length() > 0) {
                    failed.append(';');
                }
                failed.append(c.getTestCaseIndex());
            }
            if (firstError.isEmpty() && !c.getErrorMessage().isEmpty()) {
                firstError = firstLine(c.getErrorMessage());
            }
        }

        return String.join(",",
                escape(r.getSolutionName()),
                String.valueOf(r.isAllPassed()),
                String.valueOf(r.getPassedCount()),
                String.valueOf(r.getTotalCount()),
                String.format(Locale.ROOT, "%.3f", ms),
                escape(failed.toString()),
                escape(firstError));
    }

    private static String firstLine(String s) {
        s = s.replace("\r", "");
        int nl = s.indexOf('\n');
        if (nl >= 0) {
            s = s.substring(0, nl);
        }
        if (s.length() > ERROR_MAX) {
            s = s.substring(0, ERROR_MAX);
        }
        return s;
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
