package algobench.loader;

import algobench.domain.Problem;
import algobench.domain.TestCase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 자체 텍스트 포맷의 문제 파일을 파싱해 {@link Problem}으로 만든다.
 *
 * <p>포맷 (UTF-8):
 * <pre>
 * TITLE: A+B
 * TIME_LIMIT_MS: 2000
 * MEMORY_LIMIT_MB: 256
 * ###
 * INPUT:
 * 1 2
 * EXPECTED:
 * 3
 * </pre>
 *
 * <p>규칙:
 * <ul>
 *   <li>첫 {@code ###} 이전 = 헤더({@code KEY: VALUE}). 필수 키 {@code TITLE}/{@code TIME_LIMIT_MS}/
 *       {@code MEMORY_LIMIT_MB}. 미지정 키는 무시, 알려진 키 중복은 오류.</li>
 *   <li>케이스는 {@code ###}로 구분. 각 케이스에 {@code INPUT:}/{@code EXPECTED:} 마커가 1회씩.</li>
 *   <li>마커({@code ###}/{@code INPUT:}/{@code EXPECTED:})는 줄 전체가 정확히 일치할 때만 인식.</li>
 *   <li>{@code \r\n}/{@code \r} → {@code \n} 정규화. 본문 내부 줄바꿈은 보존, 끝 trailing newline만 제거.</li>
 *   <li>형식 오류는 {@code 파일:라인 - 메시지} 형태의 {@link IllegalArgumentException}.</li>
 * </ul>
 */
public final class ProblemLoader {

    private static final String CASE_SEPARATOR = "###";
    private static final String INPUT_MARKER = "INPUT:";
    private static final String EXPECTED_MARKER = "EXPECTED:";

    /**
     * 문제 파일을 읽어 {@link Problem}으로 파싱한다.
     *
     * @param filePath 문제 파일 경로
     * @return 파싱된 불변 {@link Problem}
     * @throws IOException              파일 입출력 오류
     * @throws IllegalArgumentException 포맷 오류(라인 번호 포함 메시지)
     */
    public Problem loadProblem(String filePath) throws IOException {
        String content = Files.readString(Path.of(filePath)); // UTF-8 기본
        content = content.replace("\r\n", "\n").replace("\r", "\n");
        String[] lines = content.split("\n", -1);

        // --- 헤더 파싱 (첫 ### 이전) ---
        String title = null;
        Long timeLimitMs = null;
        Integer memoryMb = null;
        int i = 0;
        boolean separatorFound = false;
        for (; i < lines.length; i++) {
            String line = lines[i];
            if (line.equals(CASE_SEPARATOR)) {
                separatorFound = true;
                i++; // ### 소비
                break;
            }
            if (line.isBlank()) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon < 0) {
                throw err(filePath, i + 1, "헤더 라인에 ':'가 없습니다: '" + line + "'");
            }
            String key = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            switch (key) {
                case "TITLE":
                    if (title != null) {
                        throw err(filePath, i + 1, "TITLE 키 중복");
                    }
                    title = value;
                    break;
                case "TIME_LIMIT_MS":
                    if (timeLimitMs != null) {
                        throw err(filePath, i + 1, "TIME_LIMIT_MS 키 중복");
                    }
                    timeLimitMs = parsePositiveLong(value, "TIME_LIMIT_MS", filePath, i + 1);
                    break;
                case "MEMORY_LIMIT_MB":
                    if (memoryMb != null) {
                        throw err(filePath, i + 1, "MEMORY_LIMIT_MB 키 중복");
                    }
                    memoryMb = (int) parsePositiveLong(value, "MEMORY_LIMIT_MB", filePath, i + 1);
                    break;
                default:
                    // 미지정 키는 무시
                    break;
            }
        }
        if (!separatorFound) {
            throw err(filePath, lines.length, "케이스 구분자 '###'가 없습니다");
        }
        if (title == null) {
            throw err(filePath, 1, "필수 헤더 TITLE 누락");
        }
        if (timeLimitMs == null) {
            throw err(filePath, 1, "필수 헤더 TIME_LIMIT_MS 누락");
        }
        if (memoryMb == null) {
            throw err(filePath, 1, "필수 헤더 MEMORY_LIMIT_MB 누락");
        }

        // --- 케이스 파싱 (### ~ 다음 ### 또는 EOF) ---
        List<TestCase> cases = new ArrayList<>();
        int index = 1;
        while (i < lines.length) {
            int bodyStartLine = i + 1; // 1-base
            List<String> body = new ArrayList<>();
            while (i < lines.length && !lines[i].equals(CASE_SEPARATOR)) {
                body.add(lines[i]);
                i++;
            }
            if (i < lines.length) {
                i++; // 다음 ### 소비
            }
            if (isAllBlank(body)) {
                continue; // 빈 세그먼트(연속 ###·끝의 ###)는 무시
            }
            cases.add(parseCase(body, index, bodyStartLine, filePath));
            index++;
        }
        if (cases.isEmpty()) {
            throw err(filePath, 1, "테스트 케이스가 하나도 없습니다");
        }

        return new Problem(title, Duration.ofMillis(timeLimitMs), memoryMb, cases);
    }

    private TestCase parseCase(List<String> body, int index, int bodyStartLine, String filePath) {
        int inputAt = -1;
        int expectedAt = -1;
        for (int k = 0; k < body.size(); k++) {
            String l = body.get(k);
            if (l.equals(INPUT_MARKER)) {
                if (inputAt != -1) {
                    throw err(filePath, bodyStartLine + k, "케이스 " + index + ": 'INPUT:' 마커 중복");
                }
                inputAt = k;
            } else if (l.equals(EXPECTED_MARKER)) {
                if (expectedAt != -1) {
                    throw err(filePath, bodyStartLine + k, "케이스 " + index + ": 'EXPECTED:' 마커 중복");
                }
                expectedAt = k;
            }
        }
        if (inputAt == -1) {
            throw err(filePath, bodyStartLine, "케이스 " + index + ": 'INPUT:' 마커 누락");
        }
        if (expectedAt == -1) {
            throw err(filePath, bodyStartLine, "케이스 " + index + ": 'EXPECTED:' 마커 누락");
        }
        if (expectedAt < inputAt) {
            throw err(filePath, bodyStartLine, "케이스 " + index + ": 'EXPECTED:'가 'INPUT:'보다 앞에 있습니다");
        }
        String input = joinAndTrimTrailing(body.subList(inputAt + 1, expectedAt));
        String expected = joinAndTrimTrailing(body.subList(expectedAt + 1, body.size()));
        return new TestCase(index, input, expected);
    }

    private static String joinAndTrimTrailing(List<String> lines) {
        String s = String.join("\n", lines);
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '\n') {
            end--;
        }
        return s.substring(0, end);
    }

    private static boolean isAllBlank(List<String> lines) {
        for (String l : lines) {
            if (!l.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static long parsePositiveLong(String value, String key, String filePath, int lineNo) {
        long parsed;
        try {
            parsed = Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            throw err(filePath, lineNo, key + "는 양의 정수여야 합니다: '" + value + "'");
        }
        if (parsed <= 0) {
            throw err(filePath, lineNo, key + "는 양수여야 합니다: " + parsed);
        }
        return parsed;
    }

    private static IllegalArgumentException err(String filePath, int lineNo, String message) {
        return new IllegalArgumentException(filePath + ":" + lineNo + " - " + message);
    }
}
