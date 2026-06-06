package algobench.result;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * 여러 로거에 같은 결과를 등록 순서대로 위임하는 합성 로거.
 *
 * <p>한 로거가 실패해도 나머지는 계속 호출한다. 예외는 {@code System.err} 경고로만 남기고
 * 재throw하지 않는다(콘솔 출력은 되는데 CSV 쓰기만 실패하는 상황 등에서 전체가 멈추지 않게).
 */
public final class CompositeResultLogger implements ResultLogger {

    private final List<ResultLogger> loggers;

    public CompositeResultLogger(List<ResultLogger> loggers) {
        Objects.requireNonNull(loggers, "loggers");
        this.loggers = List.copyOf(loggers);
    }

    @Override
    public void log(BenchmarkResult result) {
        for (ResultLogger logger : loggers) {
            try {
                logger.log(result);
            } catch (IOException | RuntimeException e) {
                System.err.println("로거 실패(" + logger.getClass().getSimpleName() + "): " + e.getMessage());
            }
        }
    }
}
