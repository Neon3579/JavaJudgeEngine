# AlgoBench Codex2 최종 구현 계획

## 1. 문서 목적

이 문서는 `plan_claude.md`를 평가하고 수정, 보완한 AlgoBench 최종 구현 계획이다.

원본 요구사항은 `202311516 권창민 프로젝트 설계도.pdf`이며, 기존 계획 문서는 `plan.md`, `plan_codex.md`, `plan_claude.md`다. `plan_claude.md`는 `plan_codex.md`보다 실제 구현 리스크를 더 잘 짚었지만, 일부 표현과 실행 정책은 구현 시 오해를 만들 수 있다. 이 문서는 그 부분을 바로잡아 `plan_codex2.md`를 최종 구현 지침으로 사용하게 한다.

핵심 결론:

- `plan_claude.md`의 주요 보완점은 대부분 유지한다.
- 같은 JVM에서 Java 풀이를 실행하는 `JavaJarSolution`은 신뢰 가능한 협조적 코드용으로만 둔다.
- hard timeout과 안정성 검증은 `ExternalProcessSolution`을 기준으로 한다.
- Windows 한국어 환경에서는 UTF-8 입출력 정책을 명시한다.
- 빌드와 데모 실행은 `build.ps1`, `run-demo.ps1`로 분리한다.

---

## 2. `plan_claude.md` 평가 및 수정 사항

### 2.1 유지할 결정

| 항목 | 평가 |
| --- | --- |
| `ExecutionResult execute(String input, Duration timeout)` | timeout 전달이 명확하므로 유지 |
| 외부 프로세스 timeout | `waitFor(timeout)` + 강제 종료 모델 유지 |
| CSV escaping | RFC 4180 방식 escaping 유지 |
| `CompositeResultLogger` | 콘솔과 CSV 동시 로깅에 필요하므로 유지 |
| 비교기 확장 | `Exact`, `WhitespaceNormalizing`, `CaseInsensitive` 유지 |
| 결과 객체 불변화 | 병렬 채점 안정성에 필요하므로 유지 |
| JUnit 미사용 | 프로젝트 제약과 맞으므로 유지 |

### 2.2 수정할 결정

| ID | `plan_claude.md`의 문제 | `plan_codex2.md`의 수정 |
| --- | --- | --- |
| P1 | `JavaJarSolution` timeout 모델이 계속 실행 중인 daemon thread를 남길 수 있음 | in-JVM Java 실행은 협조적 코드용으로 명시하고, hard timeout 검증은 외부 프로세스로 수행 |
| P2 | `System.exit()` 위험은 언급했지만 구현 정책이 충분히 분리되지 않음 | 신뢰 불가 Java 코드는 `ExternalProcessSolution`으로 별도 JVM 실행하도록 강제 지침화 |
| P3 | 자식 JVM UTF-8 인자 자동 삽입을 일반 규칙처럼 읽을 수 있음 | `java` 명령 감지 시에만 `-Dfile.encoding=UTF-8` 삽입을 허용하고, 나머지는 사용자가 명령에 포함 |
| P4 | `CompositeResultLogger` 예외 정책은 좋아졌지만 `ResultLogger`의 throws와 충돌 가능 | 인터페이스는 `throws IOException` 유지, composite는 내부에서 잡고 stderr 경고 후 반환 |
| P5 | `ProblemLoader` 예약어 충돌 미지원은 적절하나 실패 규칙이 더 필요 | 줄 시작 마커만 인식하고, 케이스 내 중복 `INPUT:`/`EXPECTED:`는 포맷 오류 |
| P6 | `build.ps1` 분리는 타당하나 기존 `CLAUDE.md`와 불일치 | `plan_codex2.md`가 최종 기준이며 구현 후 `CLAUDE.md`를 갱신 대상으로 둠 |
| P7 | 문서 끝에 불필요한 코드펜스가 남아 있음 | `plan_codex2.md`에서는 제거 |
| P8 | Java 17 기능 사용 가능 문장이 구현 자유도를 과하게 넓힘 | Java SE 17 이상을 기준으로 하되 preview 기능과 불필요한 최신 문법은 쓰지 않음 |

---

## 3. 최종 확정 제약

- 언어: Java SE 17 이상.
- 빌드 도구: Maven, Gradle 사용 금지.
- 런타임 외부 라이브러리: 사용 금지.
- 테스트 프레임워크: 사용 금지.
- 검증 방식: `javac`, `java`, PowerShell 스크립트, `Main` end-to-end demo runner.
- 범위 제외: 네트워크, DB, 웹 UI, 사용자 계정, 대규모 채점 서버.
- 메모리 제한: `Problem.memoryLimitMb`에 메타데이터로만 저장하고 강제하지 않는다.
- 기본 실행 환경: Windows PowerShell, 경로에 공백과 한글이 포함될 수 있음을 전제로 한다.
- 인코딩: 파일, 프로세스 stdin/stdout/stderr, CSV 모두 UTF-8을 기준으로 한다.
- 제출 코드 신뢰 모델: semi-trusted. 악의적 또는 신뢰 불가 코드는 반드시 외부 프로세스로 실행한다.

---

## 4. PDF 요구사항 매핑

| ID | 요구사항 | 최종 구현 |
| --- | --- | --- |
| FR-01 | 문제 파일 로드 | `ProblemLoader`가 자체 텍스트 포맷을 UTF-8로 파싱 |
| FR-02 | 테스트 케이스 관리 | `Problem`이 `List<TestCase>`를 불변으로 보관 |
| FR-03 | 풀이 코드 실행 | `Solution` 인터페이스와 Java in-JVM, 외부 프로세스 구현 |
| FR-04 | 정답 판정 | `OutputComparator` 구현체로 expected와 actual 비교 |
| FR-05 | 실행 시간 측정 | 케이스별 `ExecutionResult.executionTime`, 풀이별 합산 |
| FR-06 | 병렬 채점 | `JudgeEngine`이 풀이 단위 `GradingTask`를 thread pool에 submit |
| FR-07 | 결과 리포팅 | 콘솔 요약과 CSV 리포트 동시 출력 |
| NFR-01 | 독립 실행성 | 로컬 파일과 로컬 프로세스만 사용 |
| NFR-02 | Pure Java | Java SE 표준 API 중심 구현 |
| NFR-03 | 확장성 | 실행 방식, 비교 정책, 로깅 포맷을 인터페이스로 분리 |
| NFR-04 | 안정성 | 외부 프로세스 hard timeout, 예외 격리, in-JVM 한계 문서화 |
| NFR-05 | 스레드 안전성 | 불변 결과 객체, 메인 스레드 순차 로깅, CSV synchronized append |
| NFR-06 | 가독성 | 실패 사유, 실패 케이스, 시간, stderr 요약 출력 |

---

## 5. 디렉토리 구조

```text
자프 과제/
├─ plan.md
├─ plan_codex.md
├─ plan_claude.md
├─ plan_codex2.md
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
- `TIME_LIMIT_MS`는 양의 정수여야 한다.
- `MEMORY_LIMIT_MB`는 양의 정수여야 한다.
- 각 테스트 케이스는 `###` 라인으로 시작한다.
- 각 케이스에는 `INPUT:` 라인과 `EXPECTED:` 라인이 정확히 1번씩 있어야 한다.
- `INPUT:` 이후부터 `EXPECTED:` 이전까지가 입력 본문이다.
- `EXPECTED:` 이후부터 다음 `###` 이전까지가 기대 출력 본문이다.
- `###`, `INPUT:`, `EXPECTED:`는 줄 시작에서 전체 라인이 일치할 때만 마커로 본다.
- 본문 내부 줄바꿈은 보존하고, 본문 끝 trailing newline만 제거한다.
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
- 구현체 내부 예외는 가능한 한 stderr 또는 error-like 결과로 캡슐화한다.
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

exitCode 규칙:

| 상황 | exitCode | timedOut |
| --- | --- | --- |
| 정상 종료 | 0 | false |
| Java reflection 실행 중 예외 | 1 | false |
| 외부 프로세스 비정상 종료 | 실제 process exit code | false |
| timeout | -1 | true |

`isSuccess()`는 `exitCode == 0 && !timedOut`만 본다. stderr가 있더라도 exitCode가 0이면 실행 자체는 성공으로 보고, 정답 여부는 comparator가 판단한다.

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

실행 규칙:

- `execute` 호출마다 새 `URLClassLoader`를 생성해 케이스 간 static 상태 누수를 줄인다.
- `URLClassLoader`는 `finally`에서 close한다.
- `System.in`, `System.out`, `System.err` 교체는 `STREAM_LOCK` 안에서 수행한다.
- Java in-JVM 실행은 `STREAM_LOCK` 때문에 사실상 직렬 실행된다.
- stdout과 stderr 캡처는 `ByteArrayOutputStream`과 UTF-8 `PrintStream`을 사용한다.
- `main` 호출만 `System.nanoTime()`으로 측정한다.
- timeout 감지는 daemon thread executor와 `Future.get(timeout)`으로 수행한다.
- timeout이면 `future.cancel(true)`, `exitCode=-1`, `timedOut=true`로 반환한다.
- `finally`에서 원래 `System.in/out/err`를 반드시 복구한다.

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
- unmatched quote는 `IllegalArgumentException`으로 실패시킨다.
- 예: `"python solutions/python/correct_solution.py"`
- 예: `"java -Dfile.encoding=UTF-8 -cp out_solutions CorrectSolution"`

인코딩 규칙:

- stdin 쓰기와 stdout/stderr 읽기는 UTF-8이다.
- `ProcessBuilder.environment()`에 `PYTHONIOENCODING=utf-8`을 설정한다.
- 첫 토큰이 `java` 또는 `java.exe`인 경우 `-Dfile.encoding=UTF-8`이 없으면 `java` 바로 뒤에 추가할 수 있다.
- 그 외 실행 파일의 인코딩은 명령 작성자가 UTF-8 출력으로 맞춘다고 가정한다.

실행 규칙:

- `ProcessBuilder(commandParts)`를 사용한다.
- stdin에 input을 UTF-8로 쓰고 닫는다.
- stdout과 stderr는 별도 future로 동시에 읽어 파이프 버퍼 deadlock을 막는다.
- `process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)`로 timeout을 감지한다.
- timeout이면 `process.descendants()`를 먼저 강제 종료하고, 이후 root process도 `destroyForcibly()` 한다.
- 종료 후 stdout/stderr reader future를 회수한다.
- 정상 종료면 실제 exitCode를 기록한다.

### 7.4 `compare`

#### `OutputComparator`

```java
public interface OutputComparator {
    boolean matches(String expected, String actual);
}
```

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
- 예외 메시지는 `TestCaseResult.errorMessage`에 기록한다.
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

규칙:

- `reports` 디렉토리가 없으면 생성한다.
- 파일이 없거나 비어 있으면 `formatter.header()`를 먼저 기록한다.
- `log`는 `synchronized`로 구현한다.
- 한 결과당 한 줄을 append한다.

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

escaping:

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
7. 기본 comparator는 `ExactOutputComparator`다.
8. logger는 `CompositeResultLogger`로 구성한다.
   - `ConsoleResultLogger`
   - `CsvResultLogger(Path.of("reports", "result.csv"), new CsvResultFormatter())`
9. `JudgeEngine.evaluateAll`을 호출한다.
10. `finally`에서 `JudgeEngine.shutdown()`을 호출한다.

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
- `problems/malformed_example.txt`: `ProblemLoader` 음성 검증용. 필수 헤더 누락 또는 `EXPECTED:` 누락 케이스를 포함한다.

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

1. `out`, `out_solutions`, `reports` 디렉토리를 생성한다.
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

---

## 11. 구현 순서

1. `README.md`, `build.ps1`, `run-demo.ps1`, 디렉토리 골격을 만든다.
2. `domain` 패키지를 구현한다.
3. `loader` 패키지와 문제 파일 3개를 구현한다.
4. `compare` 패키지의 인터페이스와 비교기 3개를 구현한다.
5. `solution` 패키지를 구현한다.
   - `Solution`
   - `ExecutionResult`
   - `ExternalProcessSolution`
   - `JavaJarSolution`
6. `result` 패키지를 구현한다.
7. `engine` 패키지를 구현한다.
8. `Main` CLI를 구현한다.
9. Java/Python 샘플 풀이를 작성한다.
10. end-to-end 검증 후 `ai_rec.md`에 명령과 결과를 기록한다.
11. 구현 후 `CLAUDE.md`가 `plan_codex2.md`를 최신 기준으로 안내하도록 갱신한다.

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
- `SlowInterruptibleSolution`: timeout 실패로 기록되며 프로그램 계속 진행.
- Python 정답 풀이: 전체 통과.
- Python timeout 풀이: `TIMEOUT`으로 기록되며 프로세스 종료.
- `reports/result.csv` 생성.
- CSV에 header와 풀이별 행이 기록.

### 12.3 문제 포맷 음성 검증

명령:

```powershell
java -cp out algobench.Main problems/malformed_example.txt out_solutions/CorrectSolution.class
```

예상 결과:

- `ProblemLoader`가 명확한 포맷 오류 메시지를 출력한다.
- 프로그램은 깔끔하게 종료한다.

### 12.4 확장성 검증

검증 방식:

- 기본 comparator를 `WhitespaceNormalizingComparator`로 바꿔 실행한다.
- 다시 `CaseInsensitiveComparator`로 바꿔 실행한다.

예상 결과:

- `JudgeEngine`, `GradingTask`, `Solution` 구현체 수정 없이 비교 정책만 교체된다.

### 12.5 안정성 검증

확인 항목:

- 외부 프로세스 timeout 이후 다음 풀이가 계속 실행된다.
- 런타임 예외가 전체 벤치마크를 중단하지 않는다.
- CSV logger가 실패해도 콘솔 logger는 출력한다.
- `JudgeEngine.shutdown()`이 항상 호출된다.
- Java in-JVM timeout 한계가 README에 명시되어 있다.

---

## 13. README 필수 내용

`README.md`에는 다음을 포함한다.

- AlgoBench 목적.
- Pure Java SE 제약.
- `build.ps1`과 `run-demo.ps1` 사용법.
- 수동 `javac`와 `java` 명령.
- 문제 파일 포맷.
- `.class` 제출 규칙.
- `.jar` 제출 규칙.
- 외부 프로세스 명령 예시.
- Python UTF-8 실행 주의.
- CSV 결과 위치.
- `JavaJarSolution`의 한계:
  - 같은 JVM 실행은 완전 격리가 아니다.
  - `System.exit()`를 막지 못한다.
  - interrupt를 무시하는 무한 루프를 안전하게 종료하지 못한다.
  - 신뢰 불가 Java 코드는 외부 JVM으로 실행해야 한다.
- 벤치마크 시간의 한계:
  - 단일 실행 측정은 JIT warmup과 OS scheduling 영향을 받을 수 있다.
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
- `SecurityManager` 기반 격리 구현 금지.
- `URLClassLoader`와 `Class<?>`를 `JavaJarSolution` 인스턴스 필드로 캐시해 케이스 간 static 상태를 공유하는 구현 금지.

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
- `README.md`에 in-JVM Java 실행 한계와 UTF-8 주의가 명시되어 있다.
- `ai_rec.md`에 작업 프롬프트, AI 행동, 검증 명령과 결과가 누적 기록되어 있다.

