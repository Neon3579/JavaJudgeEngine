package algobench.domain;

import algobench.compare.OutputComparator;
import java.util.Objects;

/**
 * 단일 테스트 케이스 (불변). 입력과 기대 출력을 한 쌍으로 보관한다.
 */
public final class TestCase {

    private final int index;
    private final String input;
    private final String expectedOutput;

    /**
     * @param index          1부터 시작하는 케이스 번호
     * @param input          stdin으로 주입할 입력 (non-null)
     * @param expectedOutput 기대 출력 (non-null)
     */
    public TestCase(int index, String input, String expectedOutput) {
        if (index < 1) {
            throw new IllegalArgumentException("index must be >= 1: " + index);
        }
        this.index = index;
        this.input = Objects.requireNonNull(input, "input");
        this.expectedOutput = Objects.requireNonNull(expectedOutput, "expectedOutput");
    }

    public int getIndex() {
        return index;
    }

    public String getInput() {
        return input;
    }

    public String getExpectedOutput() {
        return expectedOutput;
    }

    /**
     * 실제 출력이 기대 출력과 일치하는지 채점 정책에 위임해 판정한다.
     */
    public boolean matches(String actualOutput, OutputComparator comparator) {
        return comparator.matches(expectedOutput, actualOutput);
    }
}
