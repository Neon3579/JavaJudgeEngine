package algobench.compare;

/**
 * 정규화 후 정확 비교.
 *
 * <p>정규화 규칙: {@code \r\n}/{@code \r} → {@code \n}, 각 줄 끝 공백 제거,
 * 문자열 끝의 trailing newline 제거. 내부 줄바꿈은 보존한다.
 * 줄 끝 공백·마지막 개행 같은 사소한 차이로 오답 처리되는 것을 막는다.
 */
public final class ExactOutputComparator implements OutputComparator {

    @Override
    public boolean matches(String expected, String actual) {
        return normalize(expected).equals(normalize(actual));
    }

    static String normalize(String s) {
        if (s == null) {
            s = "";
        }
        s = s.replace("\r\n", "\n").replace("\r", "\n");
        String[] lines = s.split("\n", -1);
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(lines[i].stripTrailing());
        }
        // 끝의 trailing newline 제거 (마지막 빈 줄들 포함)
        int end = sb.length();
        while (end > 0 && sb.charAt(end - 1) == '\n') {
            end--;
        }
        return sb.substring(0, end);
    }
}
