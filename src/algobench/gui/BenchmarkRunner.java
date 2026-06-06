package algobench.gui;

import algobench.compare.OutputComparator;
import algobench.domain.Problem;
import algobench.engine.JudgeEngine;
import algobench.gui.SubmissionsPanel.PanelSpec;
import algobench.result.BenchmarkResult;
import algobench.result.CompositeResultLogger;
import algobench.result.ConsoleResultLogger;
import algobench.result.CsvResultFormatter;
import algobench.result.CsvResultLogger;
import algobench.result.ResultLogger;
import algobench.solution.ExecutionResult;
import algobench.solution.Solution;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.SwingWorker;

/**
 * 채점 실행기 — {@link SwingWorker}로 빌드(컴파일)와 {@link JudgeEngine#evaluateAll} 호출을
 * 백그라운드 스레드에서 수행하고, 결과는 {@code done()}에서 EDT로 콜백한다.
 *
 * <p>EDT에서 절대 블로킹하지 않는다. 풀이별 빌드 실패는 격리해 해당 패널 상태에만 반영하고
 * 나머지 풀이 채점은 계속 진행한다(코어 NFR-04 정신).
 */
final class BenchmarkRunner extends SwingWorker<BenchmarkRunner.Outcome, Void> {

    /** GUI 갱신 훅(모두 EDT에서 호출됨). */
    interface Hooks {
        void panelStatus(SubmissionPanel panel, String message, boolean error);

        void finished(List<BenchmarkResult> results);

        void failed(String message);
    }

    private static final Path CSV_PATH = Path.of("reports", "result.csv");

    private final Problem problem;
    private final List<PanelSpec> specs;
    private final OutputComparator comparator;
    private final Hooks hooks;

    BenchmarkRunner(Problem problem, List<PanelSpec> specs, OutputComparator comparator, Hooks hooks) {
        this.problem = problem;
        this.specs = specs;
        this.comparator = comparator;
        this.hooks = hooks;
    }

    @Override
    protected Outcome doInBackground() {
        List<BuildOutcome> builds = new ArrayList<>();
        List<Solution> solutions = new ArrayList<>();
        Map<String, Integer> nameCounts = new HashMap<>();

        for (PanelSpec ps : specs) {
            String unique = uniqueName(ps.spec().name, nameCounts);
            try {
                Solution s = ps.spec().buildSolution();
                solutions.add(new NamedSolution(s, unique));
                builds.add(new BuildOutcome(ps.panel(), null));
            } catch (Exception ex) {
                builds.add(new BuildOutcome(ps.panel(), errorMessage(ex)));
            }
        }

        List<BenchmarkResult> results = List.of();
        if (!solutions.isEmpty()) {
            JudgeEngine engine = new JudgeEngine(logger());
            try {
                results = engine.evaluateAll(problem, solutions, comparator);
            } finally {
                engine.shutdown();
            }
        }
        return new Outcome(builds, results, solutions.size());
    }

    @Override
    protected void done() {
        Outcome o;
        try {
            o = get();
        } catch (Exception e) {
            hooks.failed("채점 중 오류: " + errorMessage(e));
            return;
        }
        for (BuildOutcome b : o.builds) {
            if (b.error == null) {
                hooks.panelStatus(b.panel, "빌드 OK", false);
            } else {
                hooks.panelStatus(b.panel, b.error, true);
            }
        }
        if (o.builtCount == 0) {
            hooks.failed("빌드된 풀이가 없습니다 — 각 풀이 탭의 상태 메시지를 확인하세요.");
        } else {
            hooks.finished(o.results);
        }
    }

    private static ResultLogger logger() {
        // Main과 동일: 콘솔 + CSV 리포트 유지
        return new CompositeResultLogger(List.of(
                new ConsoleResultLogger(),
                new CsvResultLogger(CSV_PATH, new CsvResultFormatter())));
    }

    private static String uniqueName(String base, Map<String, Integer> counts) {
        int n = counts.merge(base, 1, Integer::sum);
        return n == 1 ? base : base + "#" + n;
    }

    private static String errorMessage(Throwable ex) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        String msg = cause.getMessage();
        return (msg == null || msg.isBlank()) ? cause.toString() : msg;
    }

    /** 빌드 결과 — 성공이면 error==null. */
    private static final class BuildOutcome {
        final SubmissionPanel panel;
        final String error;

        BuildOutcome(SubmissionPanel panel, String error) {
            this.panel = panel;
            this.error = error;
        }
    }

    /** 백그라운드 산출물. */
    static final class Outcome {
        final List<BuildOutcome> builds;
        final List<BenchmarkResult> results;
        final int builtCount;

        Outcome(List<BuildOutcome> builds, List<BenchmarkResult> results, int builtCount) {
            this.builds = builds;
            this.results = results;
            this.builtCount = builtCount;
        }
    }

    /** 표시 이름만 교체하고 실행은 위임 — 같은 이름 풀이 구분용(Main.UniqueNamedSolution과 동일 역할). */
    private static final class NamedSolution implements Solution {
        private final Solution delegate;
        private final String displayName;

        NamedSolution(Solution delegate, String displayName) {
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
