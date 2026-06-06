import java.util.Scanner;

/**
 * A+B 오답 풀이 (데모용) — 입력을 절댓값으로 처리하는 버그.
 *
 * <p>양수 케이스는 통과하지만 음수가 섞인 케이스(예: {@code -3 10})에서 WRONG_ANSWER가 난다.
 */
public class WrongSolution {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        long a = sc.nextLong();
        long b = sc.nextLong();
        System.out.println(Math.abs(a) + Math.abs(b)); // 버그: 음수 입력 오답
    }
}
