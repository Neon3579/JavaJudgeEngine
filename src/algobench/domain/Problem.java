package algobench.domain;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * 하나의 문제 (불변). 제목·시간 제한·메모리 제한(메타데이터)과 테스트 케이스 목록을 보관한다.
 *
 * <p>{@code memoryLimitMb}는 메타데이터로만 저장하며 실행 시 강제하지 않는다.
 * 테스트 케이스 목록은 {@link List#copyOf}로 방어적 복사해 불변성을 보장한다.
 */
public final class Problem {

    private final String title;
    private final Duration timeLimit;
    private final int memoryLimitMb;
    private final List<TestCase> testCases;

    public Problem(String title, Duration timeLimit, int memoryLimitMb, List<TestCase> testCases) {
        Objects.requireNonNull(title, "title");
        if (title.isBlank()) {
            throw new IllegalArgumentException("title must be non-empty");
        }
        Objects.requireNonNull(timeLimit, "timeLimit");
        if (timeLimit.isZero() || timeLimit.isNegative()) {
            throw new IllegalArgumentException("timeLimit must be > 0: " + timeLimit);
        }
        if (memoryLimitMb <= 0) {
            throw new IllegalArgumentException("memoryLimitMb must be > 0: " + memoryLimitMb);
        }
        Objects.requireNonNull(testCases, "testCases");
        if (testCases.isEmpty()) {
            throw new IllegalArgumentException("testCases must be non-empty");
        }
        this.title = title;
        this.timeLimit = timeLimit;
        this.memoryLimitMb = memoryLimitMb;
        this.testCases = List.copyOf(testCases); // 방어적 복사 → 불변
    }

    public String getTitle() {
        return title;
    }

    public Duration getTimeLimit() {
        return timeLimit;
    }

    /** 메타데이터 전용 — 실행 시 강제하지 않음. */
    public int getMemoryLimitMb() {
        return memoryLimitMb;
    }

    /** 불변 리스트 반환 (List.copyOf). */
    public List<TestCase> getTestCases() {
        return testCases;
    }
}
