package algobench.engine;

import algobench.compare.OutputComparator;
import algobench.domain.Problem;
import algobench.result.BenchmarkResult;
import algobench.result.ResultLogger;
import algobench.solution.Solution;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 풀이×문제를 비동기 채점하는 엔진. {@code ExecutorService} 스레드 풀에 {@link GradingTask}를 submit한다.
 *
 * <p><b>스레드 안전 (NFR-05):</b> 채점은 병렬로 실행하되, {@link ResultLogger} 호출은
 * {@link #evaluateAll}이 <b>메인 스레드에서 제출 순서대로 순차</b> 수행한다(결과 섞임 방지).
 *
 * <p>(G4) {@link #shutdown()}은 {@code shutdown()} → {@code awaitTermination(짧게)} → 미종료 시
 * {@code shutdownNow()}로 지연 task를 회수해 JVM 종료를 막지 않는다.
 */
public final class JudgeEngine {

    private static final long SHUTDOWN_WAIT_SECONDS = 2;

    private final ExecutorService threadPool;
    private final ResultLogger logger;

    public JudgeEngine(ResultLogger logger) {
        this(logger, Math.max(1, Runtime.getRuntime().availableProcessors()));
    }

    public JudgeEngine(ResultLogger logger, int threads) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.threadPool = Executors.newFixedThreadPool(Math.max(1, threads));
    }

    /** 모든 풀이를 thread pool에 submit하고 Future 목록을 제출 순서로 반환(블로킹 없음). */
    public List<Future<BenchmarkResult>> evaluateAllAsync(Problem problem, List<Solution> solutions,
                                                          OutputComparator comparator) {
        Objects.requireNonNull(problem, "problem");
        Objects.requireNonNull(solutions, "solutions");
        Objects.requireNonNull(comparator, "comparator");
        List<Future<BenchmarkResult>> futures = new ArrayList<>();
        for (Solution s : solutions) {
            futures.add(threadPool.submit(new GradingTask(problem, s, comparator)));
        }
        return futures;
    }

    /**
     * 모든 풀이를 채점하고, 결과를 제출 순서로 수집하면서 <b>메인 스레드에서 순차 로깅</b>한다(NFR-05).
     *
     * @return 제출 순서의 벤치마크 결과 목록
     */
    public List<BenchmarkResult> evaluateAll(Problem problem, List<Solution> solutions,
                                             OutputComparator comparator) {
        List<Future<BenchmarkResult>> futures = evaluateAllAsync(problem, solutions, comparator);
        List<BenchmarkResult> results = new ArrayList<>();
        for (Future<BenchmarkResult> f : futures) {
            BenchmarkResult result;
            try {
                result = f.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ExecutionException e) {
                // GradingTask가 내부 예외를 격리하므로 정상 경로에선 도달하지 않음 — 방어적 처리
                System.err.println("채점 task 실패: " + e.getCause());
                continue;
            }
            results.add(result);
            try {
                logger.log(result);
            } catch (IOException e) {
                System.err.println("로깅 실패(" + result.getSolutionName() + "): " + e.getMessage());
            }
        }
        return results;
    }

    /** (G4) 풀을 정리한다 — graceful shutdown 후 미종료 시 강제 회수. */
    public void shutdown() {
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
