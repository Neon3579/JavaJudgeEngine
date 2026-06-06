package algobench.result;

import java.io.IOException;

/**
 * 채점 결과 출력 정책. (NFR-03 확장점 — 콘솔/CSV 등 구현체 교체 가능)
 *
 * <p>로깅은 {@code JudgeEngine}이 메인 스레드에서 순차 호출한다(NFR-05).
 */
public interface ResultLogger {

    /**
     * 한 풀이의 벤치마크 결과를 출력한다.
     *
     * @param result 출력할 결과
     * @throws IOException 출력 대상(파일 등) 입출력 오류
     */
    void log(BenchmarkResult result) throws IOException;
}
