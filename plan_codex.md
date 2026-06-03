# AlgoBench Codex 보완 구현 계획

## 1. 문서 목적

이 문서는 `202311516 권창민 프로젝트 설계도.pdf`와 기존 `plan.md`를 대조해 보완한 AlgoBench 구현 계획이다.

기존 `plan.md`의 전체 방향은 설계도와 잘 맞는다. 다만 실제 구현자가 바로 코드를 작성하기에는 일부 결정이 모호했다. 특히 `Solution.execute`의 타임아웃 전달 방식, Java `.class`/`.jar` 로딩 규칙, 같은 JVM에서 실행되는 Java 풀이의 타임아웃 한계, CSV escaping, 콘솔과 CSV 동시 로깅 방식, 검증 기준이 더 명확해야 한다.

`plan_codex.md`는 위 모호성을 제거하고, Pure Java SE만으로 구현 가능한 범위를 기준으로 확정된 구현 지침을 제공한다.

---

## 2. PDF 요구사항 반영 평가

### 2.1 PDF 기능 요구사항 매핑

| ID | PDF 요구사항 | 구현 반영 |
| --- | --- | --- |
| FR-01 | 문제 파일 로드 | `ProblemLoader`가 자체 텍스트 포맷을 읽어 `Problem` 생성 |
| FR-02 | 테스트 케이스 관리 | `Problem`이 여러 `TestCase`를 불변 리스트로 보관 |
| FR-03 | 풀이 코드 실행 | `Solution` 인터페이스와 `JavaJarSolution`, `ExternalProcessSolution` 구현 |
| FR-04 | 정답 판정 | `OutputComparator`와 구현체들이 기대 출력과 실제 출력 비교 |
| FR-05 | 실행 시간 측정 | `ExecutionResult`, `TestCaseResult`, `BenchmarkResult`에 `Duration` 저장 |
| FR-06 | 병렬 채점 | `JudgeEngine`이 `ExecutorService`로 풀이 단위 비동기 채점 |
| FR-07 | 결과 리포팅 | `ConsoleResultLogger`, `CsvResultLogger`, `CompositeResultLogger`로 출력 |

### 2.2 PDF 비기능 요구사항 매핑

| ID | PDF 요구사항 | 구현 반영 |
| --- | --- | --- |
| NFR-01 | 독립 실행성 | 네트워크, DB, 서버 없이 로컬 파일과 프로세스만 사용 |
| NFR-02 | Pure Java | Java SE 표준 라이브러리만 사용, Maven/Gradle/JUnit 없음 |
| NFR-03 | 확장성 | 실행 방식, 비교 정책, 로깅 포맷을 인터페이스로 분리 |
| NFR-04 | 안정성 | 외부 프로세스는 강제 종료, Java 동일 JVM 실행은 한계를 문서화하고 예외 격리 |
| NFR-05 | 스레드 안전성 | 결과 객체 불변화, 로깅은 결과 수집 후 순차 수행, CSV append는 synchronized |
| NFR-06 | 가독성 | 콘솔 요약과 CSV 리포트에 실패 케이스, 시간, 에러 메시지 기록 |

### 2.3 기존 `plan.md` 보완 사항

1. `Solution.execute(String input)`만으로는 타임아웃 기준이 불명확하다.  
   보완: `ExecutionResult execute(String input, Duration timeout)`으로 확정한다.

2. Java 풀이의 무한 루프를 같은 JVM 안에서 강제 종료할 수 없다는 점이 구현 리스크다.  
   보완: `JavaJarSolution`은 best-effort timeout으로 명시하고, hard timeout이 필요한 Java 제출은 `ExternalProcessSolution`으로 별도 JVM 실행을 권장한다.

3. `.class`와 `.jar`를 어떻게 로드할지 규칙이 부족하다.  
   보완: `.class`는 default package 기준으로 파일명에서 클래스명을 추론하고, `.jar`는 manifest의 `Main-Class`를 사용한다.

4. 설계도는 공백 무시와 대소문자 무시 같은 비교 정책을 언급한다.  
   보완: `WhitespaceNormalizingComparator`, `CaseInsensitiveComparator`를 확장 예시로 포함한다.

5. 콘솔 또는 CSV 출력은 가능하지만 동시 출력 구조가 명확하지 않다.  
   보완: `CompositeResultLogger`를 추가해 여러 logger를 한 번에 호출한다.

6. CSV 파일은 쉼표, 따옴표, 줄바꿈 escaping이 필요하다.  
   보완: `CsvResultFormatter`가 RFC 4180 방식으로 필드를 escape한다.

---

## 3. 확정 제약

- 언어: Java SE.
- 빌드 도구: 없음. `javac`와 `java`만 사용.
- 런타임 외부 라이브러리: 없음.
- 테스트 프레임워크: 없음. 검증은 `Main` demo runner end-to-end 실행으로 수행한다.
- 범위 제외: 네트워크, DB, 웹 UI, 사용자 계정, 대규모 채점 서버.
- 메모리 제한: 문제 메타데이터로만 보관하고 강제하지 않는다.
- 구현의 기본 실행 환경: Windows PowerShell.

---

## 4. 디렉토리 구조

```text
자프 과제/
├─ plan.md
├─ plan_codex.md
├─ ai_rec.md
├─ CLAUDE.md
├─ README.md
├─ build.ps1
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
│  └─ max_of_three.txt
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

`SlowInterruptibleSolution.java`는 같은 JVM Java 실행의 timeout 동작을 보여주는 예제다. 무한 busy loop 대신 `Thread.sleep` 또는 interrupt 확인 루프를 사용해 `Future.cancel(true)` 후 스트림 복구가 가능하게 한다.

`timeout_solution.py`는 외부 프로세스 hard timeout 검증용이다. 외부 프로세스는 timeout 시 `destroyForcibly()`로 종료할 수 있으므로 NFR-04 검증에 더 적합하다.

---

## 5. 문제 파일 포맷

문제 파일은 헤더 블록과 테스트 케이스 블록으로 구성한다.

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
- 헤더는 `KEY: VALUE` 형식만 허용한다.
- 필수 키는 `TITLE`, `TIME_LIMIT_MS`, `MEMORY_LIMIT_MB`다.
- 각 테스트 케이스는 `###`로 시작한다.
- 각 케이스는 `INPUT:`과 `EXPECTED:`를 반드시 포함한다.
- `INPUT:` 이후부터 `EXPECTED:` 이전까지를 입력 본문으로 본다.
- `EXPECTED:` 이후부터 다음 `###` 이전까지를 기대 출력 본문으로 본다.
- 본문 내부 줄바꿈은 보존한다.
- 본문 끝의 trailing newline만 정규화한다.
- 형식 오류는 `IllegalArgumentException` 또는 `IOException`으로 명확한 메시지를 던진다.

---

## 6. 클래스별 구현 명세

### 6.1 `domain`

#### `TestCase`

책임: 테스트 케이스 하나의 입력과 기대 출력을 표현한다.

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

- 생성자에서 `null` 입력을 거부한다.
- `matches`는 직접 비교하지 않고 `OutputComparator`에 위임한다.

#### `Problem`

책임: 문제 메타데이터와 여러 테스트 케이스를 보관한다.

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

- `testCases`는 `List.copyOf(testCases)`로 방어적 복사한다.
- 빈 테스트 케이스 리스트는 거부한다.
- `timeLimit`은 양수여야 한다.

### 6.2 `loader`

#### `ProblemLoader`

책임: 자체 텍스트 포맷을 파싱해 `Problem`을 생성한다.

메서드:

- `Problem loadProblem(String filePath) throws IOException`

구현 규칙:

- `Files.readString(Path.of(filePath), StandardCharsets.UTF_8)`을 사용한다.
- `\r\n`과 `\r`을 `\n`으로 정규화한 뒤 파싱한다.
- 테스트 케이스 index는 1부터 시작한다.
- `TIME_LIMIT_MS`는 `Duration.ofMillis(...)`로 변환한다.
- `MEMORY_LIMIT_MB`는 int로 변환하되 강제 제한에는 사용하지 않는다.

### 6.3 `solution`

#### `Solution`

책임: 풀이 실행 방식을 공통 인터페이스로 추상화한다.

확정 시그니처:

```java
public interface Solution {
    String getName();
    ExecutionResult execute(String input, Duration timeout);
}
```

계약:

- `input`을 표준 입력처럼 풀이에 전달한다.
- 풀이의 stdout, stderr, exitCode, 실행 시간, timeout 여부를 `ExecutionResult`로 반환한다.
- 구현체는 예외를 `ExecutionResult`에 담아 반환하거나, 호출자인 `GradingTask`가 잡을 수 있는 예외를 던질 수 있다.
- `GradingTask`는 어떤 예외도 벤치마크 전체로 전파하지 않는다.

#### `ExecutionResult`

책임: 풀이 실행 결과를 불변 데이터로 보관한다.

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

- 정상 exitCode는 `0`이다.
- timeout 결과의 exitCode는 `-1`로 둔다.
- `isSuccess()`는 `exitCode == 0 && !timedOut`로 판단한다. stderr 존재 여부는 실패 조건으로 직접 쓰지 않고 결과 메시지에 기록한다.

#### `JavaJarSolution`

책임: Java `.class` 또는 `.jar`를 `URLClassLoader`와 reflection으로 같은 JVM 안에서 실행한다.

생성자:

- `JavaJarSolution(String filePath)`

필드:

- `private static final Object STREAM_LOCK`
- `private final Path filePath`
- `private final String solutionName`
- `private final URLClassLoader classLoader`
- `private final Class<?> solutionClass`

로딩 규칙:

- `.class` 파일은 default package만 지원한다.
- `.class` 파일의 클래스명은 파일명에서 `.class`를 제거해 추론한다.
- `.class` 파일의 classpath root는 해당 파일의 부모 디렉토리다.
- `.jar` 파일은 manifest의 `Main-Class` 값을 사용한다.
- `.jar`에 `Main-Class`가 없으면 생성자에서 `IllegalArgumentException`을 던진다.
- 대상 클래스는 `public static void main(String[] args)`를 가져야 한다.

실행 규칙:

- `execute(input, timeout)`은 풀이 실행 전후 시간을 `System.nanoTime()`으로 측정한다.
- `System.in`, `System.out`, `System.err`는 JVM 전역 상태이므로 `STREAM_LOCK` 안에서만 교체한다.
- 원래 스트림은 `finally`에서 반드시 복구한다.
- stdout/stderr 캡처에는 `ByteArrayOutputStream`과 UTF-8 `PrintStream`을 사용한다.

타임아웃 한계:

- 같은 JVM에서 실행되는 Java 코드는 Java SE 표준 API만으로 안전하게 강제 종료할 수 없다.
- `Future.get(timeout)`과 `Future.cancel(true)`는 interrupt에 반응하는 코드에만 효과적이다.
- interrupt를 무시하는 무한 루프는 worker thread가 계속 살아 있을 수 있다.
- 따라서 Java hard timeout이 필요한 경우 해당 Java 풀이를 별도 JVM 명령으로 실행하고 `ExternalProcessSolution`을 사용한다.
- 이 한계는 `README.md`에 반드시 적는다.

#### `ExternalProcessSolution`

책임: Python, C/C++, 실행 파일, 또는 별도 JVM으로 실행되는 Java 풀이를 OS 프로세스로 실행한다.

생성자:

- `ExternalProcessSolution(String command)`

필드:

- `private final String command`
- `private final List<String> commandParts`

명령 파싱 규칙:

- CLI에서 외부 명령은 하나의 인자로 전달한다.
- 예: `"python solutions/python/correct_solution.py"`
- `ExternalProcessSolution`은 공백과 따옴표를 고려하는 간단한 tokenizer로 `List<String>`을 만든다.
- 지원 범위는 double quote로 감싼 인자와 일반 공백 구분 인자다.
- tokenizer 오류는 `IllegalArgumentException`으로 처리한다.

실행 규칙:

- `ProcessBuilder(commandParts)`를 사용한다.
- stdin은 UTF-8로 쓰고 닫는다.
- stdout/stderr는 별도 thread 또는 `CompletableFuture`로 동시에 읽어 deadlock을 방지한다.
- `process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)`를 사용한다.
- timeout이면 `destroyForcibly()` 후 `timedOut=true`, `exitCode=-1`로 반환한다.
- timeout이 아니면 실제 exitCode를 기록한다.

### 6.4 `compare`

#### `OutputComparator`

```java
public interface OutputComparator {
    boolean matches(String expected, String actual);
}
```

#### `ExactOutputComparator`

규칙:

- `\r\n`, `\r`을 `\n`으로 정규화한다.
- 각 줄 끝의 trailing whitespace를 제거한다.
- 전체 출력 끝의 trailing newline을 제거한다.
- 정규화 후 문자열이 완전히 같으면 통과다.

#### `WhitespaceNormalizingComparator`

규칙:

- expected와 actual을 trim한다.
- 연속 whitespace를 하나의 공백으로 축약한다.
- 축약 후 문자열이 같으면 통과다.

#### `CaseInsensitiveComparator`

규칙:

- `ExactOutputComparator`와 동일한 newline/공백 정규화를 먼저 적용한다.
- `equalsIgnoreCase`로 비교한다.
- PDF의 "대소문자 무시" 확장 예시를 구현으로 보여준다.

### 6.5 `engine`

#### `GradingTask`

책임: 하나의 풀이를 하나의 문제 전체에 대해 채점한다.

시그니처:

```java
public final class GradingTask implements Callable<BenchmarkResult>
```

필드:

- `private final Problem problem`
- `private final Solution solution`
- `private final OutputComparator comparator`

`call()` 규칙:

- 각 `TestCase`에 대해 `solution.execute(testCase.getInput(), problem.getTimeLimit())` 호출.
- `OutputComparator`로 expected와 actual을 비교한다.
- timeout이면 해당 케이스는 실패다.
- exitCode가 0이 아니면 해당 케이스는 실패다.
- 예외가 발생하면 해당 케이스의 `errorMessage`에 기록하고 다음 케이스로 진행한다.
- 전체 실행 시간은 케이스별 실행 시간 합으로 계산한다.
- 모든 케이스가 통과해야 `BenchmarkResult.allPassed=true`다.

#### `JudgeEngine`

책임: 여러 풀이를 병렬로 제출하고 결과를 수집한다.

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
- `evaluateAllAsync`는 `GradingTask`를 submit하고 future 리스트를 반환한다.
- `evaluateAll`은 future를 순서대로 수집한다.
- 로깅은 worker thread가 아니라 `evaluateAll` 결과 수집 루프에서 순차 수행한다.
- `shutdown()`은 `threadPool.shutdown()`을 호출한다.

### 6.6 `result`

#### `TestCaseResult`

필드:

- `private final int testCaseIndex`
- `private final boolean passed`
- `private final String expectedOutput`
- `private final String actualOutput`
- `private final Duration executionTime`
- `private final String errorMessage`

규칙:

- `errorMessage`는 실패 이유가 없으면 빈 문자열이다.
- timeout, exitCode 실패, runtime exception, comparator mismatch를 구분해 메시지에 남긴다.

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

- `caseResults`는 `List.copyOf(caseResults)`로 보관한다.

#### `ResultLogger`

```java
public interface ResultLogger {
    void log(BenchmarkResult result) throws IOException;
}
```

#### `ConsoleResultLogger`

출력 내용:

- 풀이명
- 통과 수와 전체 케이스 수
- 전체 통과 여부
- 총 실행 시간 ms
- 실패 케이스 번호
- 첫 에러 메시지 또는 출력 불일치 요약

#### `CsvResultLogger`

필드:

- `private final Path csvFilePath`
- `private final ResultFormatter formatter`

규칙:

- `reports` 디렉토리가 없으면 생성한다.
- 파일이 없거나 비어 있으면 header를 먼저 쓴다.
- `log`는 `synchronized`로 구현한다.
- 한 `BenchmarkResult`당 한 줄을 append한다.

#### `CompositeResultLogger`

책임: 여러 logger를 순차 호출한다.

생성자:

- `CompositeResultLogger(List<ResultLogger> loggers)`

규칙:

- `log(result)`는 등록된 logger를 순서대로 호출한다.
- 한 logger에서 예외가 발생해도 다음 logger 호출을 막지 않는다.
- 발생한 예외는 마지막에 `IOException`으로 묶어 던지거나 콘솔에 경고로 출력한다.
- 단순성을 우선하면 콘솔 경고 출력 후 계속 진행한다.

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

escaping 규칙:

- 필드에 comma, double quote, newline 중 하나라도 있으면 필드를 double quote로 감싼다.
- 필드 내부 double quote는 `""`로 치환한다.
- null은 빈 문자열로 기록한다.

---

## 7. `Main` CLI 명세

실행 형식:

```powershell
java -cp out algobench.Main <problemFile> <solution1> <solution2> ...
```

예시:

```powershell
java -cp out algobench.Main problems/a_plus_b.txt `
  out_solutions/CorrectSolution.class `
  out_solutions/WrongSolution.class `
  out_solutions/SlowInterruptibleSolution.class `
  "python solutions/python/correct_solution.py" `
  "python solutions/python/timeout_solution.py"
```

동작:

1. 인자가 2개 미만이면 사용법을 출력하고 종료한다.
2. 첫 번째 인자는 문제 파일 경로다.
3. 나머지 인자는 풀이 경로 또는 외부 명령이다.
4. `.class` 또는 `.jar`로 끝나면 `JavaJarSolution`으로 생성한다.
5. 그 외는 `ExternalProcessSolution`으로 생성한다.
6. `ProblemLoader`로 문제를 로드한다.
7. 기본 comparator는 `ExactOutputComparator`다.
8. logger는 `CompositeResultLogger`로 구성한다.
   - `ConsoleResultLogger`
   - `CsvResultLogger("reports/result.csv", new CsvResultFormatter())`
9. `JudgeEngine.evaluateAll(...)`을 호출한다.
10. `finally`에서 `JudgeEngine.shutdown()`을 호출한다.

---

## 8. 샘플 산출물 명세

### 8.1 문제 파일

`problems/a_plus_b.txt`

- 제목: A+B
- 제한 시간: 2000ms
- 메모리 제한: 256MB
- 케이스: 두 정수를 더하는 2개 이상 케이스

`problems/max_of_three.txt`

- 제목: Max of Three
- 제한 시간: 2000ms
- 메모리 제한: 256MB
- 케이스: 세 정수 중 최댓값 출력

### 8.2 Java 샘플 풀이

`solutions/java/CorrectSolution.java`

- default package.
- stdin에서 두 정수를 읽고 합을 출력한다.

`solutions/java/WrongSolution.java`

- default package.
- 일부 케이스에서 틀린 값을 출력한다.

`solutions/java/SlowInterruptibleSolution.java`

- default package.
- 제한 시간보다 오래 걸리지만 interrupt에 반응한다.
- 같은 JVM Java timeout 시 스트림 복구가 되는 예제로 사용한다.

### 8.3 Python 샘플 풀이

`solutions/python/correct_solution.py`

- stdin에서 두 정수를 읽고 합을 출력한다.

`solutions/python/timeout_solution.py`

- 무한 루프 또는 긴 sleep으로 timeout을 유발한다.
- 외부 프로세스 hard timeout 검증용이다.

---

## 9. 빌드 및 실행 스크립트

### 9.1 수동 빌드

```powershell
javac -encoding UTF-8 -d out (Get-ChildItem -Recurse src -Filter *.java).FullName
javac -encoding UTF-8 -d out_solutions (Get-ChildItem -Recurse solutions/java -Filter *.java).FullName
```

### 9.2 수동 실행

```powershell
java -cp out algobench.Main problems/a_plus_b.txt `
  out_solutions/CorrectSolution.class `
  out_solutions/WrongSolution.class `
  out_solutions/SlowInterruptibleSolution.class `
  "python solutions/python/correct_solution.py" `
  "python solutions/python/timeout_solution.py"
```

### 9.3 `build.ps1`

책임:

1. `out`, `out_solutions`, `reports` 디렉토리를 필요 시 생성한다.
2. `src` 아래 Java 엔진 코드를 컴파일한다.
3. `solutions/java` 아래 샘플 Java 풀이를 컴파일한다.
4. 기본 demo command를 실행한다.

실패 처리:

- 컴파일 실패 시 즉시 종료한다.
- 실행 실패 시 exit code를 그대로 반환한다.

---

## 10. 구현 순서

1. `README.md`, `build.ps1`, 샘플 디렉토리 골격 생성.
2. `domain` 패키지 구현.
   - `TestCase`
   - `Problem`
3. `loader` 패키지 구현.
   - `ProblemLoader`
   - `problems/a_plus_b.txt`
   - `problems/max_of_three.txt`
4. `compare` 패키지 구현.
   - `OutputComparator`
   - `ExactOutputComparator`
   - `WhitespaceNormalizingComparator`
   - `CaseInsensitiveComparator`
5. `solution` 패키지 구현.
   - `Solution`
   - `ExecutionResult`
   - `ExternalProcessSolution`
   - `JavaJarSolution`
6. `result` 패키지 구현.
   - `TestCaseResult`
   - `BenchmarkResult`
   - `ResultLogger`
   - `ConsoleResultLogger`
   - `CsvResultLogger`
   - `CompositeResultLogger`
   - `ResultFormatter`
   - `CsvResultFormatter`
7. `engine` 패키지 구현.
   - `GradingTask`
   - `JudgeEngine`
8. `Main` CLI 구현.
9. 샘플 Java/Python 풀이 구현.
10. end-to-end 검증 후 `ai_rec.md`에 실행 명령과 결과를 기록.

---

## 11. 검증 계획

JUnit이나 외부 테스트 프레임워크는 사용하지 않는다.

### 11.1 컴파일 검증

```powershell
javac -encoding UTF-8 -d out (Get-ChildItem -Recurse src -Filter *.java).FullName
```

예상 결과:

- 컴파일 오류 0.
- `out/algobench/Main.class` 생성.

```powershell
javac -encoding UTF-8 -d out_solutions (Get-ChildItem -Recurse solutions/java -Filter *.java).FullName
```

예상 결과:

- 컴파일 오류 0.
- `out_solutions/CorrectSolution.class`, `WrongSolution.class`, `SlowInterruptibleSolution.class` 생성.

### 11.2 실행 검증

```powershell
java -cp out algobench.Main problems/a_plus_b.txt `
  out_solutions/CorrectSolution.class `
  out_solutions/WrongSolution.class `
  out_solutions/SlowInterruptibleSolution.class `
  "python solutions/python/correct_solution.py" `
  "python solutions/python/timeout_solution.py"
```

예상 결과:

- `CorrectSolution`: 전체 통과.
- `WrongSolution`: 최소 1개 케이스 실패, 실패 케이스 번호 표시.
- `SlowInterruptibleSolution`: timeout 또는 시간 초과 실패 표시, 프로그램 계속 진행.
- Python 정답 풀이: 전체 통과.
- Python timeout 풀이: timeout 표시, 프로세스 종료, 프로그램 계속 진행.
- `reports/result.csv` 생성.
- CSV에 header와 풀이별 결과 행이 기록.

### 11.3 확장성 검증

구현 후 `Main` 또는 임시 코드에서 comparator를 `WhitespaceNormalizingComparator`로 바꿔 실행한다.

예상 결과:

- `JudgeEngine`, `GradingTask`, `Solution` 구현체 수정 없이 비교 정책만 바뀐다.

`CaseInsensitiveComparator`도 같은 방식으로 검증한다.

### 11.4 안정성 검증

확인 항목:

- 외부 프로세스 timeout 후 다음 풀이가 계속 실행된다.
- 한 풀이의 runtime error가 전체 벤치마크를 중단시키지 않는다.
- CSV logger 실패가 콘솔 출력 자체를 막지 않는다.
- `JudgeEngine.shutdown()`이 항상 호출된다.

---

## 12. README 필수 기재 내용

`README.md`에는 다음 내용을 포함한다.

- AlgoBench의 목적.
- Pure Java SE 프로젝트라는 제약.
- 빌드 명령.
- 실행 명령.
- 문제 파일 포맷.
- Java `.class`/`.jar` 제출 규칙.
- 외부 명령 제출 예시.
- 결과 CSV 위치.
- Java same-JVM timeout 한계:
  - `JavaJarSolution`은 `URLClassLoader`와 reflection을 쓰므로 PDF 설계 의도를 충족한다.
  - 그러나 같은 JVM에서 실행되는 Java 무한 루프는 Java SE만으로 안전하게 강제 종료할 수 없다.
  - hard timeout이 필요하면 Java 제출도 별도 JVM 명령으로 실행해 `ExternalProcessSolution`을 사용한다.

---

## 13. 구현 시 금지 사항

- Maven, Gradle 추가 금지.
- JUnit 또는 외부 테스트 프레임워크 추가 금지.
- 외부 CSV 라이브러리 추가 금지.
- 네트워크 호출 추가 금지.
- DB 저장 추가 금지.
- GUI 또는 웹 UI 추가 금지.
- 결과 객체에 setter 추가 금지.
- worker thread에서 CSV 파일을 직접 동시에 쓰는 구조 금지.
- Java same-JVM 무한 루프를 완전히 종료할 수 있다고 README나 보고서에 주장 금지.

---

## 14. 최종 산출물 기준

구현 완료 후 작업 디렉토리는 다음 조건을 만족해야 한다.

- `src/algobench` 아래 모든 패키지와 클래스가 존재한다.
- `problems`에 샘플 문제 2개가 존재한다.
- `solutions/java`와 `solutions/python`에 샘플 풀이가 존재한다.
- `build.ps1`로 컴파일과 demo 실행이 가능하다.
- demo 실행 후 `reports/result.csv`가 생성된다.
- 콘솔 출력만 봐도 풀이별 성공 여부, 실패 케이스, 시간, 에러를 파악할 수 있다.
- `ai_rec.md`에는 구현 과정의 주요 프롬프트, AI 행동, 검증 명령과 결과가 누적 기록되어 있다.

