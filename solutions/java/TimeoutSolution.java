/**
 * 타임아웃 시연용 풀이 — 무한 루프로 시간 제한을 초과한다.
 *
 * <p>같은 JVM에서 강제 종료가 불가능하므로 {@code JavaJarSolution}은 데몬 스레드 +
 * {@code Future.get(timeout)} best-effort로 TIMEOUT 표시만 하고, 프로그램은 계속 진행한다(NFR-04).
 */
public class TimeoutSolution {
    public static void main(String[] args) {
        long x = 0;
        while (true) {
            x++;
        }
    }
}
