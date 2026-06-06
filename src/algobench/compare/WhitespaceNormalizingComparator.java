package algobench.compare;

/**
 * 토큰 단위 비교. 양끝 공백 제거 후 연속 whitespace(공백·탭·개행)를 공백 1칸으로 축약해 비교한다.
 *
 * <p>공백·줄바꿈 형식 차이를 무시하므로 {@link ExactOutputComparator}보다 관대하다.
 * 엔진 수정 없이 채점 기준을 추가할 수 있음을 보이는 NFR-03 확장 시연.
 */
public final class WhitespaceNormalizingComparator implements OutputComparator {

    @Override
    public boolean matches(String expected, String actual) {
        return normalize(expected).equals(normalize(actual));
    }

    private static String normalize(String s) {
        if (s == null) {
            s = "";
        }
        return s.strip().replaceAll("\\s+", " ");
    }
}
