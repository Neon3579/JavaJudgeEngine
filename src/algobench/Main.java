package algobench;

import algobench.compare.ExactOutputComparator;
import algobench.compare.OutputComparator;
import algobench.domain.Problem;
import algobench.engine.JudgeEngine;
import algobench.loader.ProblemLoader;
import algobench.result.BenchmarkResult;
import algobench.result.BenchmarkSummaryPrinter;
import algobench.result.CompositeResultLogger;
import algobench.result.ConsoleResultLogger;
import algobench.result.CsvResultFormatter;
import algobench.result.CsvResultLogger;
import algobench.result.ResultLogger;
import algobench.result.TestCaseResult;
import algobench.solution.ExecutionResult;
import algobench.solution.ExternalProcessSolution;
import algobench.solution.JavaJarSolution;
import algobench.solution.Solution;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * CLI 진입점 / 데모 러너.
 *
 * <pre>java -cp out algobench.Main &lt;problemFile&gt; &lt;solution...&gt;</pre>
 *
 * <p>풀이 인자 타입을 확장자로 판별한다: {@code .class}/{@code .jar} → {@link JavaJarSolution},
 * 그 외(예: {@code "python sol.py"}) → {@link ExternalProcessSolution}.
 *
 * <p>흐름: 문제 로드 → 이름 유일화 → 채점(콘솔+CSV 로깅) → 풀이 간 비교 요약(G1) → 풀 정리.
 */
public final class Main {

    private static final Path CSV_PATH = Path.of("reports", "result.csv");

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("사용법: java -cp out algobench.Main <problemFile> <solution...>");
            System.out.println("  solution: *.class / *.jar  → 같은 JVM 실행(JavaJarSolution)");
            System.out.println("            그 외(예: \"python sol.py\") → 외부 프로세스(ExternalProcessSolution)");
            return;
        }

        ProblemLoader loader = new ProblemLoader();
        Problem problem = loader.loadProblem(args[0]);
        System.out.printf("문제: %s  (케이스 %d개, 시간 제한 %dms)%n",
                problem.getTitle(), problem.getTestCases().size(), problem.getTimeLimit().toMillis());

        List<Solution> solutions = new ArrayList<>();
        Map<String, Integer> nameCounts = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            Solution s = createSolution(args[i]);
            solutions.add(new UniqueNamedSolution(s, uniqueName(s.getName(), nameCounts)));
        }

        OutputComparator comparator = new ExactOutputComparator();
        ResultLogger logger = new CompositeResultLogger(List.of(
                new ConsoleResultLogger(),
                new CsvResultLogger(CSV_PATH, new CsvResultFormatter())));

        JudgeEngine engine = new JudgeEngine(logger);
        try {
            List<BenchmarkResult> results = engine.evaluateAll(problem, solutions, comparator);
            new BenchmarkSummaryPrinter().printComparison(results, System.out);
            if (isVerbose()) {
                printVerbose(results);
            }
            System.out.println();
            System.out.println("CSV 리포트: " + CSV_PATH);
        } finally {
            engine.shutdown();
        }
    }

    /** {@code -Dalgobench.verbose=true} 디버그 플래그. */
    private static boolean isVerbose() {
        return Boolean.parseBoolean(System.getProperty("algobench.verbose", "false"));
    }

    /** 디버그 모드 — 통과 케이스 포함 전 케이스의 stdin 결과 상세를 출력한다. */
    private static void printVerbose(List<BenchmarkResult> results) {
        System.out.println();
        System.out.println("====== 상세 (verbose) ======");
        for (BenchmarkResult br : results) {
            System.out.println("풀이: " + br.getSolutionName());
            for (TestCaseResult c : br.getCaseResults()) {
                System.out.printf(Locale.ROOT, "  케이스 %d: %s  (%.3f ms)%n",
                        c.getTestCaseIndex(), c.isPassed() ? "PASS" : "FAIL",
                        c.getExecutionTime().toNanos() / 1_000_000.0);
                System.out.println("    기대: " + inline(c.getExpectedOutput()));
                System.out.println("    실제: " + inline(c.getActualOutput()));
                if (!c.getErrorMessage().isEmpty()) {
                    System.out.println("    사유: " + inline(c.getErrorMessage()));
                }
            }
        }
    }

    private static String inline(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\r", "").replace("\n", "\\n");
    }

    private static Solution createSolution(String arg) {
        String lower = arg.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".class") || lower.endsWith(".jar")) {
            return new JavaJarSolution(Path.of(arg));
        }
        return new ExternalProcessSolution(arg);
    }

    /** 같은 이름이 둘 이상이면 {@code name#2}, {@code name#3} … 으로 유일화. */
    private static String uniqueName(String base, Map<String, Integer> counts) {
        int n = counts.merge(base, 1, Integer::sum);
        return n == 1 ? base : base + "#" + n;
    }

    /** 표시 이름만 교체하고 실행은 위임하는 wrapper (리포트에서 풀이 구분용). */
    private static final class UniqueNamedSolution implements Solution {
        private final Solution delegate;
        private final String displayName;

        UniqueNamedSolution(Solution delegate, String displayName) {
            this.delegate = delegate;
            this.displayName = displayName;
        }

        @Override
        public String getName() {
            return displayName;
        }

        @Override
        public ExecutionResult execute(String input, Duration timeout) {
            return delegate.execute(input, timeout);
        }
    }
}
