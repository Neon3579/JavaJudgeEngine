package algobench.solution;

import java.time.Duration;

/**
 * 실행 가능한 풀이. <b>핵심 다형성 지점</b> — Java in-JVM 실행과 외부 프로세스 실행이
 * 동일한 stdin/stdout 계약을 따른다.
 *
 * <p>계약: {@code input}을 stdin으로 주입하고 stdout/stderr/exitCode/실행시간을
 * {@link ExecutionResult}로 반환한다. 타임아웃은 호출측(GradingTask)이 문제의 시간 제한으로 전달한다.
 * 내부 예외는 가능한 한 {@link ExecutionResult}로 캡슐화한다.
 */
public interface Solution {

    /** 리포트·로그에 표시할 풀이 이름. */
    String getName();

    /**
     * 입력을 주입해 풀이를 실행한다.
     *
     * @param input   stdin으로 줄 입력
     * @param timeout 실행 제한 시간 — 초과 시 {@code timedOut=true}로 반환
     * @return 실행 결과
     */
    ExecutionResult execute(String input, Duration timeout);
}
