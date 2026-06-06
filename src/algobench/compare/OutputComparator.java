package algobench.compare;

/**
 * 기대 출력과 실제 출력의 일치 여부를 판정하는 채점 정책. (NFR-03 확장점)
 *
 * <p>엔진/풀이 코드 수정 없이 구현체만 교체해 채점 기준을 바꿀 수 있다.
 * 모든 구현체는 {@code null} 인자를 빈 문자열("")로 취급해야 한다.
 */
public interface OutputComparator {

    /**
     * @param expected 문제 파일의 기대 출력 (null 허용 → "" 취급)
     * @param actual   풀이 실행 결과 stdout (null 허용 → "" 취급)
     * @return 정책에 따라 두 출력이 일치하면 true
     */
    boolean matches(String expected, String actual);
}
