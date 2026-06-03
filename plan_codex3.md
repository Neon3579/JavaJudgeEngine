# AlgoBench Codex3 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement AlgoBench, a Pure Java SE local algorithm benchmarking tool that loads local problem files, runs multiple solutions, compares outputs, measures execution time, and reports results to console and CSV.

**Architecture:** The implementation is a small Java SE pipeline: load problem files, execute solutions through a common interface, compare outputs through pluggable comparators, collect immutable results, and log them through console/CSV loggers. Java in-JVM execution is kept for semi-trusted cooperative code; hard timeout and stronger isolation use external OS processes.

**Tech Stack:** Java SE 17+, PowerShell, `javac`, `java`, no Maven/Gradle, no JUnit, no runtime external libraries.

---

## 1. 문서 목적

이 문서는 `plan_claude2.md`를 평가하고 수정, 보완한 `plan_codex3.md` 최종 구현 지침이다.

원본 요구사항은 `202311516 권창민 프로젝트 설계도.pdf`다. 계획 계보는 `plan.md` → `plan_codex.md` → `plan_claude.md` → `plan_codex2.md` → `plan_claude2.md` → `plan_codex3.md`다.

평가 결론:

- `plan_claude2.md`는 현재까지 가장 정확한 계획이다.
- D1~D6 보정은 대부분 타당하므로 유지한다.
- 다만 구현자가 바로 코딩할 때 남는 세부 결정이 있다.
  - 자식 JVM 인코딩 인자 삽입 방식.
  - 외부 프로세스 reader grace timeout의 정확한 bounded 반환 규칙.
  - CSV truncate 시점.
  - solution name 유일화 방법.
  - 문제 헤더 중복 처리.
  - in-JVM Java와 외부 프로세스 간 시간 측정 해석.

`plan_codex3.md`는 위 항목을 결정 완료 상태로 만든다.

---

## 2. `plan_claude2.md` 평가 및 Codex3 보완

### 2.1 유지하는 결정

| 항목 | 평가 |
| --- | --- |
| `Solution.execute(String input, Duration timeout)` | timeout 전달이 명확하므로 유지 |
| in-JVM Java는 semi-trusted 코드용 | `System.exit()`와 무한 루프를 완전 차단할 수 없으므로 유지 |
| hard timeout은 `ExternalProcessSolution` 기준 | OS 프로세스 종료가 가능하므로 유지 |
| per-call `URLClassLoader` | static 상태 케이스 간 누수를 줄이므로 유지 |
| `ExecutionResult.stdout/stderr` non-null | comparator NPE 방지에 필요하므로 유지 |
| comparator null-safe 처리 | 실패 격리에 유리하므로 유지 |
| CSV run마다 fresh 파일 | 데모 반복 시 결과 중복을 막으므로 유지 |
| `failedCaseIndexes` 세미콜론 구분 | CSV escaping을 줄이므로 유지 |
| `firstErrorMessage` 첫 줄 200자 제한 | CSV 가독성을 높이므로 유지 |
| `build.ps1` / `run-demo.ps1` 분리 | 컴파일 검증과 실행 검증을 분리하므로 유지 |

### 2.2 Codex3에서 수정하는 결정

| ID | `plan_claude2.md`의 남은 문제 | Codex3 최종 결정 |
| --- | --- | --- |
| E1 | 첫 토큰이 정확히 `java`/`java.exe`일 때만 JVM 인코딩 인자를 삽입하면 절대경로 Java 실행을 놓침 | `Path.of(token).getFileName()` 기준으로 `java`/`java.exe`도 감지한다 |
| E2 | 자식 JVM 인코딩 삽입 대상 속성이 명확하지만 중복 삽입 방지 규칙이 부족함 | `-Dstdout.encoding=`, `-Dstderr.encoding=`가 이미 있으면 각각 삽입하지 않는다 |
| E3 | `CsvResultLogger`가 생성자에서 truncate하면 객체 생성만으로 파일이 변경된다 | `CsvResultLogger.startRun()`을 명시하거나, 단순 구현에서는 생성자가 아니라 첫 `log` 전 lazy initialization에서 truncate한다 |
| E4 | `solutionName` 유일화가 `Main` 단계라고만 되어 있고 구현 수단이 없음 | `Main` 내부 `UniqueNamedSolution` wrapper로 suffix를 붙인다 |
| E5 | 문제 헤더 unknown key는 무시하지만 duplicate required key 정책이 없음 | unknown key는 무시, duplicate known key는 포맷 오류 |
| E6 | 외부 reader future grace timeout 후 future가 남을 수 있음 | reader executor도 `shutdownNow()`하고, 미회수 stream은 부분 캡처로 기록한다 |
| E7 | Java in-JVM과 외부 프로세스 runtime 비교가 완전히 동등하다고 오해될 수 있음 | README에 mode 간 시간 비교 한계를 명시한다 |
| E8 | `process.descendants()` kill 순서가 한 번의 snapshot에 의존함 | timeout 시 descendants snapshot kill → root kill → 짧은 wait → 남은 descendants 재조회 kill 순서로 정한다 |

---

## 3. 최종 확정 제약

- Java SE 17 이상을 기준으로 구현한다.
- preview 기능과 불필요한 최신 문법은 사용하지 않는다.
- Maven, Gradle, JUnit, 외부 CSV 라이브러리, 런타임 외부 라이브러리는 사용하지 않는다.
- 검증은 `javac`, `java`, PowerShell 스크립트, `Main` end-to-end demo runner로 수행한다.
- 네트워크, DB, 웹 UI, GUI, 사용자 계정, 대규모 채점 서버는 범위 밖이다.
- 메모리 제한은 `Problem.memoryLimitMb`에 메타데이터로만 저장하고 강제하지 않는다.
- 파일, 문제 포맷, CSV, 프로세스 stdin/stdout/stderr는 UTF-8 기준이다.
- 기본 실행 환경은 Windows PowerShell이며, 작업 경로에 공백과 한글이 포함될 수 있다.
- 제출 코드는 semi-trusted로 가정한다.
- 신뢰 불가 Java 코드는 반드시 별도 JVM 명령으로 실행하고 `ExternalProcessSolution`을 사용한다.

---

## 4. PDF 요구사항 최종 매핑

| ID | 요구사항 | 구현 지침 |
| --- | --- | --- |
| FR-01 | 문제 파일 로드 | `ProblemLoader`가 UTF-8 자체 텍스트 포맷을 파싱 |
| FR-02 | 테스트 케이스 관리 | `Problem`이 `List<TestCase>`를 불변 보관 |
| FR-03 | 풀이 코드 실행 | `Solution` 인터페이스, `JavaJarSolution`, `ExternalProcessSolution` |
| FR-04 | 정답 판정 | `OutputComparator` 구현체로 expected/actual 비교 |
| FR-05 | 실행 시간 측정 | `ExecutionResult.executionTime`, `BenchmarkResult.totalExecutionTime` |
| FR-06 | 병렬 채점 | `JudgeEngine`이 풀이 단위 `GradingTask`를 thread pool에 submit |
| FR-07 | 결과 리포팅 | console + CSV 동시 출력 |
| NFR-01 | 독립 실행성 | 로컬 파일과 로컬 프로세스만 사용 |
| NFR-02 | Pure Java | Java SE 표준 API 중심 구현 |
| NFR-03 | 확장성 | 실행, 비교, 로깅을 인터페이스로 분리 |
| NFR-04 | 안정성 | 외부 hard timeout, 예외 격리, in-JVM 한계 문서화 |
| NFR-05 | 스레드 안전성 | 불변 결과 객체, 메인 스레드 순차 로깅, CSV synchronized append |
| NFR-06 | 가독성 | 실패 사유, 케이스 번호, 시간, stderr 요약 출력 |

---

## 5. 디렉토리 구조

```text
자프 과제/
├─ plan.md
├─ plan_codex.md
├─ plan_claude.md
├─ plan_codex2.md
├─ plan_claude2.md
├─ plan_codex3.md
├─ ai_rec.md
├─ CLAUDE.md
├─ README.md
├─ build.ps1
├─ run-demo.ps1
├─ src/algobench/
│  ├─ Main.java
│  ├─ domain/
│  │  ├─ Problem.java
│  │  └─ TestCase.java
│  ├─ loader/
│  │  └─ ProblemLoader.java
│  ├─ solution/
│  │  ├─ Solution.java
│  │  ├─ ExecutionResult.java
│  │  ├─ JavaJarSolution.java
│  │  └─ ExternalProcessSolution.java
│  ├─ compare/
│  │  ├─ OutputComparator.java
│  │  ├─ ExactOutputComparator.java
│  │  ├─ WhitespaceNormalizingComparator.java
│  │  └─ CaseInsensitiveComparator.java
│  ├─ engine/
│  │  ├─ JudgeEngine.java
│  │  └─ GradingTask.java
│  └─ result/
│     ├─ BenchmarkResult.java
│     ├─ TestCaseResult.java
│     ├─ ResultLogger.java
│     ├─ ConsoleResultLogger.java
│     ├─ CsvResultLogger.java
│     ├─ CompositeResultLogger.java
│     ├─ ResultFormatter.java
│     └─ CsvResultFormatter.java
├─ problems/
│  ├─ a_plus_b.txt
│  ├─ max_of_three.txt
│  └─ malformed_example.txt
├─ solutions/
│  ├─ java/
│  │  ├─ CorrectSolution.java
│  │  ├─ WrongSolution.java
│  │  └─ SlowInterruptibleSolution.java
│  └─ python/
│     ├─ correct_solution.py
│     └─ timeout_solution.py
├─ out/
├─ out_solutions/
└─ reports/
```

---

## 6. 문제 파일 포맷

기본 형식:

```text
TITLE: A+B
TIME_LIMIT_MS: 2000
MEMORY_LIMIT_MB: 256
###
INPUT:
1 2
EXPECTED:
3
###
INPUT:
5 7
EXPECTED:
12
```

파싱 규칙:

- 첫 `###` 이전은 헤더다.
- 헤더 필수 키는 `TITLE`, `TIME_LIMIT_MS`, `MEMORY_LIMIT_MB`다.
- 헤더 라인은 `KEY: VALUE` 형식만 허용한다.
- `TITLE`은 빈 문자열이면 안 된다.
- `TIME_LIMIT_MS`는 양의 정수여야 한다.
- `MEMORY_LIMIT_MB`는 양의 정수여야 한다.
- unknown header key는 무시한다.
- duplicate known header key는 포맷 오류다.
- 각 테스트 케이스는 `###` 라인으로 시작한다.
- 각 케이스에는 `INPUT:` 라인과 `EXPECTED:` 라인이 정확히 1번씩 있어야 한다.
- `INPUT:` 이후부터 `EXPECTED:` 이전까지가 입력 본문이다.
- `EXPECTED:` 이후부터 다음 `###` 이전까지가 기대 출력 본문이다.
- `###`, `INPUT:`, `EXPECTED:`는 전체 라인이 정확히 일치할 때만 마커로 본다.
- 본문 내부 줄바꿈은 보존한다.
- 본문 끝 trailing newline만 제거한다.
- 본문에 줄 시작 `###`, `INPUT:`, `EXPECTED:`가 필요한 문제는 이번 범위에서 지원하지 않는다.
- 형식 오류는 위치와 원인이 담긴 메시지로 실패시킨다.

---

## 7. 클래스별 구현 명세

### 7.1 `domain`

#### `TestCase`

책임: 테스트 케이스 하나를 표현한다.

필드:

- `private final int index`
- `private final String input`
- `private final String expectedOutput`

메서드:

- `int getIndex()`
- `String getInput()`
- `String getExpectedOutput()`
- `boolean matches(String actualOutput, OutputComparator comparator)`

규칙:

- `index`는 1 이상이어야 한다.
- `input`, `expectedOutput`은 null이면 안 된다.
- `matches`는 `comparator.matches(expectedOutput, actualOutput)`만 호출한다.

#### `Problem`

책임: 문제 메타데이터와 테스트 케이스 목록을 보관한다.

필드:

- `private final String title`
- `private final Duration timeLimit`
- `private final int memoryLimitMb`
- `private final List<TestCase> testCases`

메서드:

- `String getTitle()`
- `Duration getTimeLimit()`
- `int getMemoryLimitMb()`
- `List<TestCase> getTestCases()`

규칙:

- `title`은 빈 문자열이면 안 된다.
- `timeLimit`은 양수여야 한다.
- `memoryLimitMb`는 양수여야 한다.
- `testCases`는 비어 있으면 안 된다.
- `testCases`는 `List.copyOf(testCases)`로 저장한다.

### 7.2 `loader`

#### `ProblemLoader`

책임: 문제 파일을 읽어 `Problem`을 만든다.

시그니처:

```java
public Problem loadProblem(String filePath) throws IOException
```

구현 규칙:

- `Files.readString(Path.of(filePath), StandardCharsets.UTF_8)`을 사용한다.
- `\r\n`과 `\r`은 `\n`으로 정규화한다.
- 헤더와 케이스를 라인 단위로 파싱한다.
- 케이스 index는 파일 등장 순서대로 1부터 부여한다.
- 포맷 오류 메시지에는 가능한 경우 line number를 포함한다.
- `malformed_example.txt`는 음성 검증용으로 둔다.

### 7.3 `solution`

#### `Solution`

```java
public interface Solution {
    String getName();
    ExecutionResult execute(String input, Duration timeout);
}
```

계약:

- `input`을 풀이의 stdin처럼 전달한다.
- stdout, stderr, exitCode, 실행 시간, timeout 여부를 `ExecutionResult`로 반환한다.
- 구현체 내부 예외는 가능한 한 `ExecutionResult`로 캡슐화한다.
- 캡슐화되지 않은 예외는 `GradingTask`가 케이스 단위로 잡는다.

#### `ExecutionResult`

필드:

- `private final String stdout`
- `private final String stderr`
- `private final int exitCode`
- `private final Duration executionTime`
- `private final boolean timedOut`

메서드:

- `String getStdout()`
- `String getStderr()`
- `int getExitCode()`
- `Duration getExecutionTime()`
- `boolean isTimedOut()`
- `boolean isSuccess()`

규칙:

- `stdout`과 `stderr`는 null이면 안 된다.
- 생성자에 null이 들어오면 빈 문자열로 정규화한다.
- `executionTime`은 null이면 안 된다.
- `isSuccess()`는 `exitCode == 0 && !timedOut`이다.
- stderr 존재 여부는 실행 성공 실패 조건으로 직접 쓰지 않는다.

exitCode 규칙:

| 상황 | exitCode | timedOut |
| --- | --- | --- |
| 정상 종료 | 0 | false |
| Java reflection 실행 중 예외 | 1 | false |
| 외부 프로세스 비정상 종료 | 실제 process exit code | false |
| timeout | -1 | true |

#### `JavaJarSolution`

책임: `.class` 또는 `.jar` Java 풀이를 같은 JVM 안에서 실행한다.

사용 범위:

- 신뢰 가능한 협조적 Java 코드 실행용이다.
- `System.exit()`, interrupt 무시 무한 루프, 전역 상태 오염을 안전하게 막을 수 없다.
- 신뢰 불가 Java 코드는 별도 JVM 명령으로 실행해 `ExternalProcessSolution`을 사용한다.

생성자:

- `JavaJarSolution(String filePath)`

필드:

- `private static final Object STREAM_LOCK`
- `private final Path filePath`
- `private final String solutionName`

로딩 규칙:

- `.class`는 default package만 지원한다.
- `.class` 클래스명은 파일명에서 `.class`를 제거해 얻는다.
- `.class` classpath root는 해당 파일의 부모 디렉토리다.
- `.jar`는 manifest의 `Main-Class`를 사용한다.
- manifest에 `Main-Class`가 없으면 생성자에서 실패한다.
- 대상 클래스는 `public static void main(String[] args)`를 가져야 한다.
- `URLClassLoader`와 `Class<?>`는 인스턴스 필드로 캐시하지 않는다.

실행 규칙:

- `execute` 호출마다 새 `URLClassLoader`를 생성한다.
- 새 class loader는 케이스 간 static 상태 누수를 줄이기 위한 것이다.
- `System.in`, `System.out`, `System.err` 교체는 `STREAM_LOCK` 안에서 수행한다.
- Java in-JVM 실행은 `STREAM_LOCK` 때문에 사실상 직렬 실행된다.
- stdout과 stderr 캡처는 `ByteArrayOutputStream`과 UTF-8 `PrintStream`을 사용한다.
- `main` 호출 구간만 `System.nanoTime()`으로 측정한다.
- timeout 감지는 daemon single-thread executor와 `Future.get(timeout)`으로 수행한다.
- timeout이면 `future.cancel(true)`, `exitCode=-1`, `timedOut=true`로 반환한다.
- `finally`에서 원래 `System.in/out/err` 복구, helper executor `shutdownNow()`, `URLClassLoader.close()`, lock 해제를 모두 수행한다.
- timeout 케이스의 stdout/stderr는 best-effort 부분 캡처일 수 있다.

명시할 한계:

- `Future.cancel(true)`는 interrupt에 반응하는 코드에만 효과가 있다.
- interrupt를 무시하는 Java thread는 daemon으로 남을 수 있다.
- 남은 daemon thread가 출력하면 실제 `System.out` 또는 다음 캡처를 오염시킬 수 있다.
- `System.exit()` 호출은 AlgoBench JVM 전체를 종료시킬 수 있다.
- Java SE 표준 API만으로 이 문제를 완전히 막지 않는다.
- SecurityManager에 의존하지 않는다.

#### `ExternalProcessSolution`

책임: Python, C/C++, 실행 파일, 또는 별도 JVM Java 풀이를 OS 프로세스로 실행한다.

생성자:

- `ExternalProcessSolution(String command)`

필드:

- `private final String command`
- `private final List<String> commandParts`

명령 파싱:

- CLI에서 외부 명령은 하나의 문자열 인자로 받는다.
- 공백 구분 인자와 double quote로 감싼 인자를 지원한다.
- backslash escape와 nested quote는 지원하지 않는다.
- unmatched quote는 `IllegalArgumentException`으로 실패시킨다.
- 예: `"python solutions/python/correct_solution.py"`
- 예: `"java -cp out_solutions CorrectSolution"`
- 예: `"C:\Program Files\Java\jdk-25\bin\java.exe" -cp out_solutions CorrectSolution`

인코딩 규칙:

- stdin 쓰기와 stdout/stderr 읽기는 UTF-8이다.
- `ProcessBuilder.environment()`에 `PYTHONIOENCODING=utf-8`을 설정한다.
- 첫 command token의 basename이 `java` 또는 `java.exe`이면 자식 JVM으로 본다.
- 자식 JVM 명령에 `-Dstdout.encoding=`이 없으면 java token 바로 뒤에 `-Dstdout.encoding=UTF-8`을 삽입한다.
- 자식 JVM 명령에 `-Dstderr.encoding=`이 없으면 java token 바로 뒤에 `-Dstderr.encoding=UTF-8`을 삽입한다.
- `-Dfile.encoding=UTF-8`은 있어도 되고 없어도 된다. Java 18+에서는 `file.encoding` 기본값이 UTF-8일 수 있지만, Windows 파이프 출력은 `stdout.encoding`/`stderr.encoding`이 중요하다.
- 그 외 실행 파일은 작성자가 UTF-8 출력으로 맞춘다고 가정한다.

실행 규칙:

- `ProcessBuilder(commandParts)`를 사용한다.
- stdin에 input을 UTF-8로 쓰고 닫는다.
- stdout과 stderr는 별도 future로 동시에 판독한다.
- 파이프 버퍼 deadlock을 막기 위해 stdout/stderr를 순차 read하지 않는다.
- `process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)`로 timeout을 감지한다.
- timeout이면 다음 순서로 종료한다.
  1. 현재 `process.descendants()` snapshot을 `destroyForcibly()` 한다.
  2. root process를 `destroyForcibly()` 한다.
  3. 짧게 대기한다.
  4. 남은 descendants를 다시 조회해 `destroyForcibly()` 한다.
- 종료 후 stdout/stderr reader future는 200~500ms grace timeout으로 회수한다.
- reader future 회수가 실패하면 부분 캡처를 사용하고 reader executor를 `shutdownNow()` 한다.
- `execute()`는 timeout 상황에서도 bounded time 안에 반환해야 한다.
- 정상 종료면 실제 exitCode를 기록한다.

시간 측정 규칙:

- 외부 프로세스는 process start부터 종료 또는 timeout 처리까지의 wall-clock 시간을 측정한다.
- Java in-JVM은 `main` 호출 구간을 측정한다.
- 서로 다른 실행 모드 간 시간 비교에는 runner overhead 차이가 있다.
- README에 mode 간 시간 비교 한계를 적는다.

### 7.4 `compare`

#### `OutputComparator`

```java
public interface OutputComparator {
    boolean matches(String expected, String actual);
}
```

공통 규칙:

- 모든 comparator는 null expected 또는 actual을 빈 문자열로 취급한다.

#### `ExactOutputComparator`

- `\r\n`, `\r`을 `\n`으로 정규화한다.
- 각 줄 끝 trailing whitespace를 제거한다.
- 전체 출력 끝 trailing newline을 제거한다.
- 정규화 후 문자열이 같으면 통과다.

#### `WhitespaceNormalizingComparator`

- 양끝 공백을 제거한다.
- 연속 whitespace를 공백 하나로 축약한다.
- 축약 후 문자열이 같으면 통과다.

#### `CaseInsensitiveComparator`

- `ExactOutputComparator`와 같은 정규화를 적용한다.
- `equalsIgnoreCase`로 비교한다.

### 7.5 `engine`

#### `GradingTask`

시그니처:

```java
public final class GradingTask implements Callable<BenchmarkResult>
```

필드:

- `private final Problem problem`
- `private final Solution solution`
- `private final OutputComparator comparator`

`call()` 규칙:

- 각 케이스마다 `solution.execute(testCase.getInput(), problem.getTimeLimit())`를 호출한다.
- `timedOut == true`면 실패 사유는 `TIMEOUT`.
- `exitCode != 0`이면 실패 사유는 `RUNTIME_ERROR`.
- comparator 불일치면 실패 사유는 `WRONG_ANSWER`.
- 그 외는 통과다.
- 케이스 처리 중 예외가 발생해도 다음 케이스로 진행한다.
- 예외 메시지는 `EXCEPTION` 사유와 함께 `TestCaseResult.errorMessage`에 기록한다.
- `TestCaseResult.actualOutput`에는 stdout을 항상 기록한다.
- `BenchmarkResult.totalExecutionTime`은 케이스별 `executionTime` 합이다.

#### `JudgeEngine`

필드:

- `private final ExecutorService threadPool`
- `private final ResultLogger logger`

생성자:

- `JudgeEngine(ResultLogger logger)`
- `JudgeEngine(ResultLogger logger, int threadCount)`

메서드:

- `List<Future<BenchmarkResult>> evaluateAllAsync(Problem problem, List<Solution> solutions, OutputComparator comparator)`
- `List<BenchmarkResult> evaluateAll(Problem problem, List<Solution> solutions, OutputComparator comparator)`
- `void shutdown()`

규칙:

- 기본 thread count는 `Math.max(1, Runtime.getRuntime().availableProcessors())`다.
- 풀이 단위로 `GradingTask`를 submit한다.
- 결과는 제출 순서대로 수집한다.
- 로깅은 worker thread에서 하지 않는다.
- `evaluateAll`의 결과 수집 루프에서 순차적으로 `logger.log(result)`를 호출한다.
- `Main`은 `finally`에서 `shutdown()`을 호출한다.

### 7.6 `result`

#### `TestCaseResult`

필드:

- `private final int testCaseIndex`
- `private final boolean passed`
- `private final String expectedOutput`
- `private final String actualOutput`
- `private final Duration executionTime`
- `private final String errorMessage`

규칙:

- 통과 시 `errorMessage`는 빈 문자열이다.
- 실패 사유는 `TIMEOUT`, `RUNTIME_ERROR`, `WRONG_ANSWER`, `EXCEPTION` 중 하나를 포함한다.
- 문자열 필드는 null이면 빈 문자열로 정규화한다.

#### `BenchmarkResult`

필드:

- `private final String solutionName`
- `private final boolean allPassed`
- `private final Duration totalExecutionTime`
- `private final List<TestCaseResult> caseResults`

메서드:

- `String getSolutionName()`
- `boolean isAllPassed()`
- `Duration getTotalExecutionTime()`
- `List<TestCaseResult> getCaseResults()`
- `int getPassedCount()`
- `int getTotalCount()`

규칙:

- `caseResults`는 `List.copyOf(caseResults)`로 저장한다.
- `solutionName`은 null 또는 빈 문자열이면 안 된다.

#### `ResultLogger`

```java
public interface ResultLogger {
    void log(BenchmarkResult result) throws IOException;
}
```

#### `ConsoleResultLogger`

출력 항목:

- solution name
- passed count / total count
- allPassed
- totalExecutionTimeMs
- failed case indexes
- first failure reason
- stderr 요약 또는 expected/actual 요약

#### `CsvResultLogger`

필드:

- `private final Path csvFilePath`
- `private final ResultFormatter formatter`
- `private boolean initialized`

규칙:

- 첫 `log` 호출 전에 lazy initialization을 수행한다.
- initialization은 `reports` 디렉토리 생성, CSV 파일 truncate, header 1회 기록으로 구성한다.
- `log`는 `synchronized`로 구현한다.
- 한 결과당 한 줄을 append한다.
- 같은 `CsvResultLogger` 인스턴스에서 initialization은 한 번만 수행한다.
- run history가 필요하면 별도 timestamp 파일명을 쓰는 변형을 README에 안내할 수 있다.

#### `CompositeResultLogger`

생성자:

- `CompositeResultLogger(List<ResultLogger> loggers)`

규칙:

- logger를 등록 순서대로 호출한다.
- 한 logger가 실패해도 다음 logger를 계속 호출한다.
- 내부에서 잡은 예외는 `System.err`에 경고로 남긴다.
- composite의 `log`는 logger 실패를 다시 throw하지 않는다.

#### `ResultFormatter`

```java
public interface ResultFormatter {
    String header();
    String format(BenchmarkResult result);
}
```

#### `CsvResultFormatter`

컬럼:

```text
solutionName,allPassed,passedCount,totalCount,totalExecutionTimeMs,failedCaseIndexes,firstErrorMessage
```

규칙:

- `failedCaseIndexes`는 `;`로 결합한다.
- 예: `2;5`.
- `firstErrorMessage`는 첫 줄만 사용하고 최대 200자로 자른다.
- 필드에 comma, double quote, newline 중 하나라도 있으면 double quote로 감싼다.
- 필드 내부 double quote는 `""`로 바꾼다.
- null은 빈 문자열로 기록한다.

---

## 8. `Main` CLI

실행 형식:

```powershell
java -cp out algobench.Main <problemFile> <solution1> <solution2> ...
```

동작:

1. 인자가 2개 미만이면 사용법을 출력하고 종료한다.
2. 첫 번째 인자는 문제 파일 경로다.
3. 나머지는 풀이 인자다.
4. `.class` 또는 `.jar`로 끝나면 `JavaJarSolution`을 만든다.
5. 그 외는 `ExternalProcessSolution`을 만든다.
6. `ProblemLoader`로 문제를 로드한다.
7. solution name 중복을 `UniqueNamedSolution` wrapper로 유일화한다.
8. 기본 comparator는 `ExactOutputComparator`다.
9. logger는 `CompositeResultLogger`로 구성한다.
   - `ConsoleResultLogger`
   - `CsvResultLogger(Path.of("reports", "result.csv"), new CsvResultFormatter())`
10. `JudgeEngine.evaluateAll`을 호출한다.
11. `finally`에서 `JudgeEngine.shutdown()`을 호출한다.

`UniqueNamedSolution` 구현 지침:

- `Main`의 private static nested class로 둔다.
- `Solution delegate`, `String displayName`을 필드로 가진다.
- `getName()`은 `displayName`을 반환한다.
- `execute(input, timeout)`은 delegate에 위임한다.
- 중복 이름이 있으면 두 번째부터 `name#2`, `name#3` suffix를 붙인다.

데모 명령:

```powershell
java -cp out algobench.Main problems/a_plus_b.txt `
  out_solutions/CorrectSolution.class `
  out_solutions/WrongSolution.class `
  out_solutions/SlowInterruptibleSolution.class `
  "python solutions/python/correct_solution.py" `
  "python solutions/python/timeout_solution.py"
```

---

## 9. 샘플 파일

### 9.1 문제 파일

- `problems/a_plus_b.txt`: 두 정수 합.
- `problems/max_of_three.txt`: 세 정수 최댓값.
- `problems/malformed_example.txt`: 필수 헤더 누락 또는 `EXPECTED:` 누락을 포함하는 음성 검증 파일.

### 9.2 Java 풀이

- `solutions/java/CorrectSolution.java`: default package, 정답 출력.
- `solutions/java/WrongSolution.java`: 최소 한 케이스에서 오답 출력.
- `solutions/java/SlowInterruptibleSolution.java`: 제한 시간보다 오래 걸리지만 interrupt에 반응.

### 9.3 Python 풀이

- `solutions/python/correct_solution.py`: 정답 출력.
- `solutions/python/timeout_solution.py`: timeout 유발.

---

## 10. PowerShell 스크립트

### 10.1 `build.ps1`

역할: 컴파일만 수행한다.

동작:

1. `out`, `out_solutions`, `reports` 디렉토리를 `New-Item -ItemType Directory -Force`로 생성한다.
2. 엔진 소스를 컴파일한다.
3. 샘플 Java 풀이를 컴파일한다.
4. 컴파일 실패 시 즉시 해당 exit code로 종료한다.

명령:

```powershell
javac -encoding UTF-8 -d out (Get-ChildItem -Recurse src -Filter *.java).FullName
javac -encoding UTF-8 -d out_solutions (Get-ChildItem -Recurse solutions/java -Filter *.java).FullName
```

### 10.2 `run-demo.ps1`

역할: 컴파일된 결과로 데모를 실행한다.

명령:

```powershell
java -cp out algobench.Main problems/a_plus_b.txt `
  out_solutions/CorrectSolution.class `
  out_solutions/WrongSolution.class `
  out_solutions/SlowInterruptibleSolution.class `
  "python solutions/python/correct_solution.py" `
  "python solutions/python/timeout_solution.py"
```

ExecutionPolicy로 `.ps1` 실행이 막히면 README에 다음 우회 명령을 안내한다.

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
powershell -ExecutionPolicy Bypass -File .\run-demo.ps1
```

---

## 11. 구현 순서

### Task 1: 문서와 스크립트 골격

**Files:**

- Create: `README.md`
- Create: `build.ps1`
- Create: `run-demo.ps1`
- Create directories: `src/algobench`, `problems`, `solutions/java`, `solutions/python`, `reports`

- [ ] `README.md`에 목적, 제약, 빌드/실행, Java in-JVM 한계, UTF-8 정책을 작성한다.
- [ ] `build.ps1`은 컴파일만 수행하게 작성한다.
- [ ] `run-demo.ps1`은 demo 실행만 수행하게 작성한다.
- [ ] `New-Item -ItemType Directory -Force`로 산출물 디렉토리 생성을 처리한다.

### Task 2: 도메인

**Files:**

- Create: `src/algobench/domain/TestCase.java`
- Create: `src/algobench/domain/Problem.java`

- [ ] `TestCase`를 불변 클래스로 구현한다.
- [ ] `Problem`을 불변 클래스로 구현한다.
- [ ] 생성자 검증과 `List.copyOf` 방어적 복사를 적용한다.

### Task 3: 문제 로더와 문제 파일

**Files:**

- Create: `src/algobench/loader/ProblemLoader.java`
- Create: `problems/a_plus_b.txt`
- Create: `problems/max_of_three.txt`
- Create: `problems/malformed_example.txt`

- [ ] UTF-8 파일 읽기와 newline 정규화를 구현한다.
- [ ] header key 검증, duplicate known key 오류, unknown key 무시를 구현한다.
- [ ] 케이스 마커와 본문 파싱을 라인 단위로 구현한다.
- [ ] 정상 문제 2개와 음성 문제 1개를 작성한다.

### Task 4: 비교기

**Files:**

- Create: `src/algobench/compare/OutputComparator.java`
- Create: `src/algobench/compare/ExactOutputComparator.java`
- Create: `src/algobench/compare/WhitespaceNormalizingComparator.java`
- Create: `src/algobench/compare/CaseInsensitiveComparator.java`

- [ ] comparator 인터페이스를 구현한다.
- [ ] 모든 comparator가 null을 빈 문자열로 처리하게 한다.
- [ ] exact, whitespace-normalizing, case-insensitive 정책을 구현한다.

### Task 5: 실행 결과와 풀이 실행

**Files:**

- Create: `src/algobench/solution/Solution.java`
- Create: `src/algobench/solution/ExecutionResult.java`
- Create: `src/algobench/solution/JavaJarSolution.java`
- Create: `src/algobench/solution/ExternalProcessSolution.java`

- [ ] `Solution` 인터페이스와 `ExecutionResult` 불변 클래스를 구현한다.
- [ ] `JavaJarSolution`에 per-call `URLClassLoader`, `STREAM_LOCK`, daemon executor timeout, finally 복구를 구현한다.
- [ ] `ExternalProcessSolution`에 command tokenizer, UTF-8 환경, 자식 JVM encoding property 삽입, stdout/stderr 동시 판독, process tree timeout 종료, reader grace timeout을 구현한다.

### Task 6: 결과와 로깅

**Files:**

- Create: `src/algobench/result/TestCaseResult.java`
- Create: `src/algobench/result/BenchmarkResult.java`
- Create: `src/algobench/result/ResultLogger.java`
- Create: `src/algobench/result/ConsoleResultLogger.java`
- Create: `src/algobench/result/CsvResultLogger.java`
- Create: `src/algobench/result/CompositeResultLogger.java`
- Create: `src/algobench/result/ResultFormatter.java`
- Create: `src/algobench/result/CsvResultFormatter.java`

- [ ] 결과 데이터 클래스를 불변으로 구현한다.
- [ ] console logger에 풀이별 요약과 첫 실패 사유를 출력한다.
- [ ] CSV logger에 lazy truncate/header initialization과 synchronized append를 구현한다.
- [ ] CSV formatter에 세미콜론 실패 케이스, 200자 에러 요약, RFC 4180 escaping을 구현한다.
- [ ] composite logger가 개별 logger 실패를 삼키고 stderr 경고만 남기게 한다.

### Task 7: 채점 엔진

**Files:**

- Create: `src/algobench/engine/GradingTask.java`
- Create: `src/algobench/engine/JudgeEngine.java`

- [ ] `GradingTask`에서 케이스 단위 실행, 판정, 예외 격리를 구현한다.
- [ ] `JudgeEngine`에서 풀이 단위 submit, 제출 순서 수집, 메인 스레드 순차 로깅을 구현한다.
- [ ] `shutdown()`을 구현한다.

### Task 8: CLI와 샘플 풀이

**Files:**

- Create: `src/algobench/Main.java`
- Create: `solutions/java/CorrectSolution.java`
- Create: `solutions/java/WrongSolution.java`
- Create: `solutions/java/SlowInterruptibleSolution.java`
- Create: `solutions/python/correct_solution.py`
- Create: `solutions/python/timeout_solution.py`

- [ ] CLI 인자 파싱과 solution 생성 규칙을 구현한다.
- [ ] `UniqueNamedSolution` wrapper로 solution name을 유일화한다.
- [ ] 기본 comparator와 composite logger를 연결한다.
- [ ] Java/Python 샘플 풀이를 작성한다.

### Task 9: 검증과 기록

**Files:**

- Modify: `ai_rec.md`
- Modify after implementation: `CLAUDE.md`

- [ ] `.\build.ps1`을 실행하고 결과를 기록한다.
- [ ] `.\run-demo.ps1`을 실행하고 결과를 기록한다.
- [ ] malformed problem 음성 검증을 실행한다.
- [ ] CSV 중복 방지를 위해 demo를 2회 실행해 `reports/result.csv`가 fresh 파일인지 확인한다.
- [ ] 구현 후 `CLAUDE.md`가 `plan_codex3.md`를 최신 기준으로 안내하게 갱신한다.
- [ ] 검증 명령과 주요 결과를 `ai_rec.md`에 append한다.

---

## 12. 검증 계획

### 12.1 컴파일 검증

```powershell
.\build.ps1
```

예상 결과:

- 엔진 컴파일 오류 0.
- 샘플 Java 풀이 컴파일 오류 0.
- `out/algobench/Main.class` 생성.
- `out_solutions/CorrectSolution.class` 생성.

### 12.2 데모 실행 검증

```powershell
.\run-demo.ps1
```

예상 결과:

- `CorrectSolution`: 전체 통과.
- `WrongSolution`: `WRONG_ANSWER`와 실패 케이스 번호 출력.
- `SlowInterruptibleSolution`: `TIMEOUT`으로 기록되며 프로그램 계속 진행.
- Python 정답 풀이: 전체 통과.
- Python timeout 풀이: `TIMEOUT`으로 기록되며 프로세스 종료.
- `reports/result.csv` 생성.
- CSV에 header와 풀이별 행이 기록.

### 12.3 CSV fresh run 검증

```powershell
.\run-demo.ps1
.\run-demo.ps1
```

예상 결과:

- 두 번째 실행 후 `reports/result.csv`는 두 run의 누적 파일이 아니다.
- header 1개와 현재 run 풀이별 행만 존재한다.

### 12.4 문제 포맷 음성 검증

```powershell
java -cp out algobench.Main problems/malformed_example.txt out_solutions/CorrectSolution.class
```

예상 결과:

- `ProblemLoader`가 명확한 포맷 오류 메시지를 출력한다.
- 프로그램은 깔끔하게 종료한다.

### 12.5 확장성 검증

검증 방식:

- 기본 comparator를 `WhitespaceNormalizingComparator`로 바꿔 실행한다.
- 다시 `CaseInsensitiveComparator`로 바꿔 실행한다.

예상 결과:

- `JudgeEngine`, `GradingTask`, `Solution` 구현체 수정 없이 비교 정책만 교체된다.

### 12.6 안정성 및 자원 검증

확인 항목:

- 외부 프로세스 timeout 이후 다음 풀이가 계속 실행된다.
- 런타임 예외가 전체 벤치마크를 중단하지 않는다.
- CSV logger가 실패해도 콘솔 logger는 출력한다.
- `JudgeEngine.shutdown()`이 항상 호출된다.
- timeout 이후 프로세스와 reader thread가 누적되지 않는다.
- Java in-JVM timeout 한계가 README에 명시되어 있다.

### 12.7 로컬 Java 인코딩 검증

참고 명령:

```powershell
java -XshowSettings:properties -version
```

확인 항목:

- Windows 환경에서 `file.encoding`이 UTF-8이어도 `stdout.encoding` 또는 `stderr.encoding`이 UTF-8이 아닐 수 있다.
- 외부 JVM 실행 시 `-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8`이 삽입되거나 명령에 포함되어야 한다.

---

## 13. README 필수 내용

`README.md`에는 다음을 포함한다.

- AlgoBench 목적.
- Pure Java SE 제약.
- `build.ps1`과 `run-demo.ps1` 사용법.
- 수동 `javac`와 `java` 명령.
- ExecutionPolicy 우회법.
- 문제 파일 포맷.
- `.class` 제출 규칙.
- `.jar` 제출 규칙.
- 외부 프로세스 명령 예시.
- Python UTF-8 실행 주의.
- 자식 JVM UTF-8 실행 주의.
  - `-Dstdout.encoding=UTF-8`
  - `-Dstderr.encoding=UTF-8`
- CSV 결과 위치.
- CSV는 run마다 fresh file이라는 점.
- `JavaJarSolution`의 한계.
  - 같은 JVM 실행은 완전 격리가 아니다.
  - `System.exit()`를 막지 못한다.
  - interrupt를 무시하는 무한 루프를 안전하게 종료하지 못한다.
  - 신뢰 불가 Java 코드는 외부 JVM으로 실행해야 한다.
- 벤치마크 시간의 한계.
  - 단일 실행 측정은 JIT warmup과 OS scheduling 영향을 받을 수 있다.
  - in-JVM Java와 외부 프로세스의 runner overhead가 다르다.
  - 반복 측정과 통계 처리는 이번 범위 밖이다.

---

## 14. 금지 사항

- Maven, Gradle 추가 금지.
- JUnit 또는 외부 테스트 프레임워크 추가 금지.
- 외부 CSV 라이브러리 추가 금지.
- 네트워크, DB, GUI, 웹 UI 추가 금지.
- 결과 객체 setter 추가 금지.
- worker thread에서 CSV 파일 직접 동시 쓰기 금지.
- `JavaJarSolution`이 신뢰 불가 코드를 안전하게 격리한다고 주장 금지.
- SecurityManager 기반 격리 구현 금지.
- `URLClassLoader`와 `Class<?>`를 `JavaJarSolution` 인스턴스 필드로 캐시하는 구현 금지.
- `ExecutionResult.stdout` 또는 `stderr`를 null로 두는 구현 금지.
- 자식 JVM 인코딩을 `-Dfile.encoding`만으로 해결했다고 가정 금지.
- timeout 후 reader future를 무제한 대기하는 구현 금지.

---

## 15. 최종 산출물 기준

구현 완료 후 다음 조건을 만족해야 한다.

- `src/algobench` 아래 모든 패키지와 클래스가 존재한다.
- 문제 파일 정상 2개와 음성 1개가 존재한다.
- Java 샘플 풀이 3개와 Python 샘플 풀이 2개가 존재한다.
- `.\build.ps1`이 성공한다.
- `.\run-demo.ps1`이 성공한다.
- 콘솔 출력만으로 풀이별 성공 여부, 실패 케이스, 시간, 에러를 파악할 수 있다.
- `reports/result.csv`가 생성되고 UTF-8로 읽힌다.
- demo 반복 실행 시 CSV가 중복 누적되지 않는다.
- `README.md`에 in-JVM Java 실행 한계와 UTF-8 주의가 명시되어 있다.
- `CLAUDE.md`가 구현 후 최신 기준 문서로 `plan_codex3.md`를 안내한다.
- `ai_rec.md`에 프롬프트, AI 행동, 검증 명령과 결과가 누적 기록되어 있다.

---

## 16. 수렴 메모

`plan_claude2.md`는 구조와 기술 리스크를 거의 모두 반영했다. `plan_codex3.md`의 변경은 새 아키텍처가 아니라 구현자가 실수하기 쉬운 세부 정책을 고정한 것이다. 다음 단계는 추가 계획 평가보다 이 문서를 기준으로 실제 구현에 착수하는 것이 적절하다.

