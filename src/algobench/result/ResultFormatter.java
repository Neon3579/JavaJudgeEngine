package algobench.result;

/**
 * 벤치마크 결과를 한 행 문자열로 직렬화하는 포매터. (NFR-03 확장점)
 *
 * <p>{@code CsvResultLogger}가 헤더 1회 + 결과당 1행으로 사용한다.
 */
public interface ResultFormatter {

    /** 파일 첫 줄에 1회 기록할 헤더 행. */
    String header();

    /** 한 결과를 한 행으로 직렬화. */
    String format(BenchmarkResult result);
}
