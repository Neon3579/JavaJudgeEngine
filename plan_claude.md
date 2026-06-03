# AlgoBench Claude 재평가·보완 구현 계획 (plan_claude.md)

## 1. 문서 목적

이 문서는 `plan_codex.md`(= `plan.md`를 Codex가 보완한 판본)를 다시 평가해 결함을 고치고, 실제 구현자가 추가 의사결정 없이 곧장 코드를 작성할 수 있도록 만든 **최종 구현 지침**이다. 원본 요구사항은 `202311516 권창민 프로젝트 설계도.pdf`, 제약은 `CLAUDE.md` 참조.

`plan_codex.md`의 방향(인터페이스 분리, timeout 전달 시그니처, CSV escaping, 로딩 규칙)은 대부분 타당하므로 유지한다. 다만 **같은 JVM에서 Java 풀이를 실행하는 부분의 안전성**과 **Windows/한국어 환경의 인코딩**에 실구현을 깨뜨릴 수 있는 빈틈이 있어 이를 집중 보완한다.

---

## 2. plan_codex.md 재평가 요약

### 2.1 유지하는 좋은 결정 (변경 없음)
- `ExecutionResult execute(String input, Duration timeout)` 시그니처 — timeout 전달 명확.
- 외부 프로세스 hard timeout = `waitFor` + `destroyForcibly()`.
- CSV RFC 4180 escaping, `CompositeResultLogger`로 콘솔+CSV 동시 출력.
- `WhitespaceNormalizingComparator`, `CaseInsensitiveComparator` 확장 예시.
- 결과 객체 불변 + 방어적 복사, 로깅은 수집 루프에서 순차 수행.
- JUnit 없이 `Main` 데모 러너 end-to-end 검증.

### 2.2 보완·수정하는 항목 (이 문서의 핵심 가치)

| # | 결함 (plan_codex.md) | 보완 (plan_claude.md) |
| --- | --- | --- |
| C1 | `System.exit()` 처리 누락 — 제출 Java가 `System.exit()` 호출 시 AlgoBench JVM 전체 종료 | in-JVM 격리의 근본 한계로 명문화. 신뢰 못 하는 Java 코드는 **별도 JVM(`ExternalProcessSolution`)** 권장. SecurityManager는 JDK 24+ 영구 비활성화라 대안 아님 |
| C2 | 좀비 스레드 출력 오염 — interrupt 무시 루프가 `cancel(true)` 후에도 살아 스트림 복구 뒤 진짜 `System.out`에 기록 → 다른 풀이 출력 오염 (NFR-05 위반) | timeout worker는 **daemon**으로 두어 JVM 종료는 막지 않되, 오염 가능성을 한계로 README에 명시. 근본 해결은 C1과 동일(외부 JVM) |
| C3 | 정적 상태 케이스 간 누수 — classLoader/class를 생성자에서 1회 로드해 모든 케이스가 같은 클래스 공유 → static 필드 누수로 오답 가능 | `execute()` **호출마다 새 `URLClassLoader`로 클래스 재로딩**(케이스 격리). 클래스 로딩은 타이머 밖, `main` 호출만 계측. 로더는 `finally`에서 close |
| C4 | 자식 프로세스 인코딩 미지정 — Windows-Korean에서 Python stdout이 cp949 → UTF-8 판독 시 깨짐 → 비교 오판정 | `ExternalProcessSolution`이 자식 인코딩을 **UTF-8로 강제**(`PYTHONIOENCODING=utf-8`, 자식 JVM은 `-Dfile.encoding=UTF-8`/`-Dstdout.encoding=UTF-8`), 캡처도 UTF-8 |
| C5 | `STREAM_LOCK`과 timeout 서브스레드 모델이 분리 기술됨 | 둘을 하나의 실행 모델로 통합 명세(아래 6.3). **Java 풀이는 락으로 사실상 직렬 실행**, 병렬 이득은 외부 프로세스에서 나옴을 명시 |
| C6 | Java 경로 `exitCode` 의미 미정의 (timeout만 -1) | Java/외부 양쪽 exitCode 의미 완전 정의(6.3 표) |
| C7 | `CompositeResultLogger` 예외 정책 모호("던지거나 경고") | **절대 throw 안 함**: stderr 경고 후 다음 logger 계속. 콘솔 출력이 CSV 실패에 막히지 않음 |
| C8 | 벤치마크 타이밍 신뢰성 언급 없음 | JIT 워밍업으로 Java 첫 실행이 느린 점, 단일 측정의 노이즈를 한계로 명시(반복 측정은 범위 외, 선택) |
| C9 | `ProblemLoader` 마커 충돌·잘못된 포맷 처리 약함 | `INPUT:`/`EXPECTED:`/`###`는 예약어로, 본문 첫 등장 기준 파싱. 잘못된 포맷 음성 테스트 추가 |
| C10 | `build.ps1`이 컴파일+데모 실행을 한 번에 섞음 | `build.ps1`(컴파일만) / `run-demo.ps1`(실행) **분리** — 컴파일 성공 검증과 실행 검증 독립 |

---

## 3. 확정 제약

- 언어: **Java SE 17 이상** (`Files.readString`, `List.copyOf`, `var`, switch 식 사용 가능). 단일 버전 의존 기능은 17 기준.
- 빌드 도구 없음. `javac`/`java`만. 런타임 외부 라이브러리 0. 테스트 프레임워크 0.
- 범위 제외: 네트워크, DB, 웹 UI, 계정, 대규모 채점 서버.
- 메모리 제한: 문제 메타데이터로만 보관, 강제하지 않음.
- 기본 실행 환경: **Windows PowerShell** (경로에 공백·한글 포함 — 인코딩/따옴표 주의).
- **보안 모델**: 제출 코드는 "준-신뢰(semi-trusted)"로 가정. in-JVM(`JavaJarSolution`)은 완전 격리 불가(C1/C2). 적대적 코드 격리가 필요하면 외부 JVM으로 실행.

---

## 4. 디렉토리 구조

```text
자프 과제/
├─ plan.md / plan_codex.md / plan_claude.md   # 계획 문서들
├─ ai_rec.md / CLAUDE.md / README.md
├─ build.ps1          # 컴파일 전용
├─ run-demo.ps1       # 데모 실행 전용
├─ src/algobench/
│  ├─ Main.java
│  ├─ domain/        { Problem.java, TestCase.java }
│  ├─ loader/        { ProblemLoader.java }
│  ├─ solution/      { Solution.java, ExecutionResult.java,
│  │                   JavaJarSolution.java, ExternalProcessSolution.java }
│  ├─ compare/       { OutputComparator.java, ExactOutputComparator.java,
│  │                   WhitespaceNormalizingComparator.java, CaseInsensitiveComparator.java }
│  ├─ engine/        { JudgeEngine.java, GradingTask.java }
│  └─ result/        { BenchmarkResult.java, TestCaseResult.java,
│                      ResultLogger.java, ConsoleResultLogger.java, CsvResultLogger.java,
│                      CompositeResultLogger.java, ResultFormatter.java, CsvResultFormatter.java }
├─ problems/         { a_plus_b.txt, max_of_three.txt, malformed_example.txt(음성 테스트용) }
├─ solutions/
│  ├─ java/          { CorrectSolution.java, WrongSolution.java, SlowInterruptibleSolution.java }
│  └─ python/        { correct_solution.py, timeout_solution.py }
├─ out/ out_solutions/ reports/
```

- `SlowInterruptibleSolution.java`: 제한 시간 초과하되 `Thread.sleep`/interrupt 체크로 `cancel(true)`에 반응 → 스트림 복구 정상 경로 시연.
- `timeout_solution.py`: 외부 프로세스 hard timeout(`destroyForcibly`) 검증용 — NFR-04에 더 적합.
- `malformed_example.txt`: 헤더 누락 등 깨진 포맷 → `ProblemLoader` 예외 음성 테스트용.

---

## 5. 문제 파일 포맷

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
- 첫 `###` 이전 = 헤더. `KEY: VALUE` 형식만, 순서 무관. 필수 키: `TITLE`, `TIME_LIMIT_MS`, `MEMORY_LIMIT_MB`.
- 각 케이스는 `###`로 시작, `INPUT:`과 `EXPECTED:`를 정확히 1번씩 포함.
- `INPUT:`~`EXPECTED:` 사이 = 입력 본문, `EXPECTED:`~다음 `###` = 기대 출력 본문. **본문 내부 줄바꿈 보존, 끝 trailing newline만 정규화.**
- **C9 보완**: `###`/`INPUT:`/`EXPECTED:`는 줄 시작에서만 마커로 인식(라인 단위, 첫 등장 기준). 본문에 동일 토큰이 줄 시작에 오는 케이스는 미지원이며 문서화한다.
- `\r\n`·`\r` → `\n` 정규화 후 파싱. 케이스 index는 1부터.
- 형식 오류 시 위치·원인이 담긴 메시지로 `IllegalArgumentException`/`IOException`.

---

## 6. 클래스별 구현 명세

### 6.1 domain

**`TestCase`** (불변): `int index`, `String input`, `String expectedOutput`. 메서드 `getIndex/getInput/getExpectedOutput`, `boolean matches(String actual, OutputComparator c)` → `c.matches(expectedOutput, actual)`에 위임. 생성자에서 `null` 거부.

**`Problem`** (불변): `String title`, `Duration timeLimit`, `int memoryLimitMb`, `List<TestCase> testCases`. getter들 + `getTestCases()`. `testCases`는 `List.copyOf`로 방어적 복사, 빈 리스트 거부, `timeLimit` 양수 검증.

### 6.2 loader

**`ProblemLoader`**: `Problem loadProblem(String filePath) throws IOException`. `Files.readString(Path.of(filePath), UTF_8)` → 개행 정규화 → §5 규칙 파싱. `TIME_LIMIT_MS`→`Duration.ofMillis`, `MEMORY_LIMIT_MB`→int(강제 안 함).

### 6.3 solution

**`Solution`** «interface»
```java
public interface Solution {
    String getName();
    ExecutionResult execute(String input, Duration timeout);
}
```
계약: `input`을 표준 입력처럼 주입, 결과를 `ExecutionResult`로 반환. 구현체는 가능한 한 예외를 `ExecutionResult`로 캡슐화한다. `GradingTask`는 어떤 예외도 벤치마크 전체로 전파하지 않는다.

**`ExecutionResult`** «Data Class» (불변): `String stdout`, `String stderr`, `int exitCode`, `Duration executionTime`, `boolean timedOut`. `isSuccess()` = `exitCode == 0 && !timedOut`. stderr 존재는 실패 조건이 아니라 메시지로만 기록.

**exitCode 의미 (C6 — 양 구현체 공통)**

| 상황 | exitCode | timedOut |
| --- | --- | --- |
| 정상 종료 | 0 | false |
| 런타임 예외 / 비정상 종료 | 외부=실제 코드, Java=`1` | false |
| 시간 초과 | `-1` | true |

**`JavaJarSolution`** — `URLClassLoader` + reflection, 같은 JVM 실행

생성자 `JavaJarSolution(String filePath)`. 필드: `static final Object STREAM_LOCK`, `Path filePath`, `String solutionName`. **classLoader/solutionClass는 인스턴스에 캐시하지 않는다(C3).**

로딩 규칙:
- `.class`: default package만 지원. 클래스명 = 파일명에서 `.class` 제거. classpath root = 파일의 부모 디렉토리.
- `.jar`: manifest `Main-Class` 사용, 없으면 생성자에서 `IllegalArgumentException`.
- 대상 클래스는 `public static void main(String[])` 보유 필수.

**실행 모델 (C2·C3·C5 통합)** — `execute(input, timeout)`:
1. `STREAM_LOCK` 획득 (전역 스트림 보호 → **Java 풀이는 사실상 직렬 실행**, 병렬 이득은 외부 프로세스에서 확보).
2. (타이머 밖) **새 `URLClassLoader`로 클래스 재로딩** → 케이스 간 static 상태 격리(C3).
3. `System.in`을 입력 바이트(UTF-8)로, `System.out`/`System.err`를 `ByteArrayOutputStream`+UTF-8 `PrintStream`으로 교체.
4. `main` 호출을 **daemon 단일 스레드 executor**에 submit(C2: JVM 종료를 막지 않도록 daemon). `start=nanoTime`.
5. `future.get(timeout.toMillis(), MILLISECONDS)`:
   - 정상 완료 → exitCode 0.
   - `InvocationTargetException` 등 → exitCode 1, stderr에 스택트레이스.
   - `TimeoutException` → `future.cancel(true)`, `timedOut=true`, exitCode -1.
6. `end=nanoTime`로 `executionTime` 계산(클래스 로딩·스트림 스왑 제외, `main` 구간만).
7. `finally`: 원래 스트림 복구, executor `shutdownNow()`, 새 classLoader `close()`, `STREAM_LOCK` 해제.

**한계 (README 필수, C1·C2)**:
- 같은 JVM에서 도는 Java 코드는 Java SE만으로 **강제 종료·완전 격리 불가**. `cancel(true)`는 interrupt에 반응하는 코드에만 유효.
- interrupt 무시 무한 루프의 worker는 daemon으로 남아 JVM 종료는 막지 않지만, 스트림 복구 후 진짜 `System.out`에 써서 출력이 오염될 수 있다.
- 제출 코드의 `System.exit()`는 AlgoBench JVM 전체를 종료시킨다.
- **SecurityManager는 JDK 17부터 deprecated, JDK 24부터 영구 비활성화**라 위 문제들의 표준 대안이 아니다.
- ⇒ 적대적/신뢰 불가 Java 코드는 별도 JVM 명령(`java -cp ... Main`)으로 띄워 `ExternalProcessSolution`으로 채점하라.

**`ExternalProcessSolution`** — `ProcessBuilder`

생성자 `ExternalProcessSolution(String command)`. 명령 토크나이저: 일반 공백 구분 + double quote 묶음 지원, 오류는 `IllegalArgumentException`.

실행 규칙:
- `ProcessBuilder(commandParts)`.
- **C4: 자식 인코딩 UTF-8 강제** — 환경변수 `PYTHONIOENCODING=utf-8` 설정(파이썬), 자식 JVM 권장 인자 `-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8`. stdin 기록·stdout/stderr 캡처 모두 UTF-8.
- stdin에 `input` UTF-8로 쓰고 닫기.
- **stdout/stderr를 별도 스레드(또는 `CompletableFuture`)로 동시 판독**해 파이프 버퍼 데드락 방지.
- `process.waitFor(timeout.toMillis(), MILLISECONDS)` → 초과 시 `destroyForcibly()`, `timedOut=true`, exitCode -1. 정상 시 실제 exitCode.

### 6.4 compare

**`OutputComparator`** «interface»: `boolean matches(String expected, String actual)`.
- **`ExactOutputComparator`**: `\r\n`/`\r`→`\n`, 줄 끝 공백 제거, 전체 끝 trailing newline 제거 후 완전 일치.
- **`WhitespaceNormalizingComparator`**: trim + 연속 공백 1칸 축약 후 일치.
- **`CaseInsensitiveComparator`**: Exact와 동일 정규화 후 `equalsIgnoreCase`.

### 6.5 engine

**`GradingTask implements Callable<BenchmarkResult>`** (설계도 «interface» CallableResult = `Callable<BenchmarkResult>`). 필드: `Problem`, `Solution`, `OutputComparator`.
`call()`:
- 각 `TestCase`에 `solution.execute(testCase.getInput(), problem.getTimeLimit())`.
- 판정 우선순위: `timedOut` → 실패(사유 "TIMEOUT"); `exitCode!=0` → 실패(사유 "RUNTIME_ERROR" + stderr 요약); comparator 불일치 → 실패(사유 "WRONG_ANSWER", 기대/실제 기록); 그 외 통과.
- **예외 격리(NFR-04)**: 케이스에서 예외 발생 시 `errorMessage`에 담고 다음 케이스 진행.
- `totalExecutionTime` = 케이스별 `executionTime` 합. 모든 케이스 통과 시 `allPassed=true`.

**`JudgeEngine`**: 필드 `ExecutorService threadPool`, `ResultLogger logger`. 생성자 `JudgeEngine(ResultLogger)` / `JudgeEngine(ResultLogger, int threadCount)`. 기본 thread = `Math.max(1, availableProcessors())`.
- `List<Future<BenchmarkResult>> evaluateAllAsync(Problem, List<Solution>, OutputComparator)` — `GradingTask` submit.
- `List<BenchmarkResult> evaluateAll(...)` — future를 제출 순서대로 `get()` 수집, **수집 루프(메인 스레드)에서 순차 `logger.log`** (NFR-05). worker 스레드에서 로깅 금지.
- `shutdown()` → `threadPool.shutdown()`. (`Main`은 `finally`에서 호출)

### 6.6 result

**`TestCaseResult`** (불변): `int testCaseIndex`, `boolean passed`, `String expectedOutput`, `String actualOutput`, `Duration executionTime`, `String errorMessage`(없으면 ""). 실패 사유(TIMEOUT/RUNTIME_ERROR/WRONG_ANSWER) 구분 기록.

**`BenchmarkResult`** (불변): `String solutionName`, `boolean allPassed`, `Duration totalExecutionTime`, `List<TestCaseResult> caseResults`(`List.copyOf`). 메서드: getter들 + `isAllPassed`, `getPassedCount`, `getTotalCount`.

**`ResultLogger`** «interface»: `void log(BenchmarkResult) throws IOException`.

**`ConsoleResultLogger`**: 풀이명, 통과수/전체, 전체 통과 여부, 총 ms, 실패 케이스 번호, 첫 실패 사유/요약 출력 (NFR-06).

**`CsvResultLogger`**: `Path csvFilePath`, `ResultFormatter formatter`. `reports` 없으면 생성, 파일 없/빈 경우 header 1회 기록, `log`는 `synchronized`, 결과당 1행 append.

**`CompositeResultLogger`** (C7): `CompositeResultLogger(List<ResultLogger>)`. `log`는 등록 순서대로 호출하되 **한 logger 예외가 다음 호출을 막지 않는다**. 예외는 stderr 경고로 출력하고 **throw하지 않는다**(콘솔 출력이 CSV 실패에 영향받지 않도록).

**`ResultFormatter`** «interface»: `String header()`, `String format(BenchmarkResult)`.

**`CsvResultFormatter`**: 컬럼 `solutionName,allPassed,passedCount,totalCount,totalExecutionTimeMs,failedCaseIndexes,firstErrorMessage`. RFC 4180 escaping(comma/quote/newline 포함 시 큰따옴표로 감싸고 내부 `"`→`""`), null→"".

---

## 7. Main CLI

실행: `java -cp out algobench.Main <problemFile> <solution...>`

동작: 인자 2개 미만 → 사용법 출력 후 종료. 첫 인자=문제 파일. 나머지=풀이. `.class`/`.jar`로 끝나면 `JavaJarSolution`, 그 외는 `ExternalProcessSolution`. `ProblemLoader` 로드 → comparator 기본 `ExactOutputComparator` → logger = `CompositeResultLogger[ConsoleResultLogger, CsvResultLogger("reports/result.csv", new CsvResultFormatter())]` → `JudgeEngine.evaluateAll` → `finally`에서 `shutdown()`.

데이터 흐름: `ProblemLoader → Problem/TestCase → GradingTask(Solution, OutputComparator) → ExecutionResult → TestCaseResult → BenchmarkResult → ResultLogger`.

---

## 8. 샘플 산출물

- `problems/a_plus_b.txt`: A+B, 2000ms, 256MB, 두 정수 합 케이스 2개+.
- `problems/max_of_three.txt`: 세 정수 최댓값.
- `problems/malformed_example.txt`: 헤더 누락 등 깨진 포맷(음성 테스트).
- `solutions/java/CorrectSolution.java`: default package, 두 정수 합 stdout.
- `solutions/java/WrongSolution.java`: 일부 케이스 오답.
- `solutions/java/SlowInterruptibleSolution.java`: 제한 초과하되 interrupt 반응(스트림 복구 정상 경로 시연).
- `solutions/python/correct_solution.py`: 두 정수 합.
- `solutions/python/timeout_solution.py`: 긴 sleep/무한 루프 → 외부 hard timeout 검증.

---

## 9. 빌드·실행 스크립트 (C10 — 분리)

**`build.ps1`** (컴파일 전용):
1. `out`, `out_solutions`, `reports` 없으면 생성.
2. `javac -encoding UTF-8 -d out (Get-ChildItem -Recurse src -Filter *.java).FullName`
3. `javac -encoding UTF-8 -d out_solutions (Get-ChildItem -Recurse solutions/java -Filter *.java).FullName`
4. 컴파일 실패 시 즉시 `exit $LASTEXITCODE`.

> 경로에 공백·한글 포함 → `Get-ChildItem ... .FullName` 배열 splatting으로 각 경로가 개별 인자로 전달됨(따옴표 불필요). 단일 파일이면 문자열 1개라도 javac 정상.

**`run-demo.ps1`** (실행 전용):
```powershell
java -cp out algobench.Main problems/a_plus_b.txt `
  out_solutions/CorrectSolution.class `
  out_solutions/WrongSolution.class `
  out_solutions/SlowInterruptibleSolution.class `
  "python solutions/python/correct_solution.py" `
  "python solutions/python/timeout_solution.py"
```

---

## 10. 구현 순서

1. `README.md`, `build.ps1`, `run-demo.ps1`, 디렉토리 골격.
2. `domain` (`TestCase`, `Problem`).
3. `loader` (`ProblemLoader`) + `problems/*.txt`(정상 2 + 음성 1).
4. `compare` (인터페이스 + 3 구현체).
5. `solution` (`Solution`, `ExecutionResult`, `ExternalProcessSolution`, `JavaJarSolution`) — **C2/C3/C4 모델 반영**.
6. `result` (데이터 2 + 로거 3 + 포매터 2).
7. `engine` (`GradingTask`, `JudgeEngine`).
8. `Main`.
9. 샘플 Java/Python 풀이.
10. end-to-end 검증 후 `ai_rec.md`에 명령·결과 기록.

---

## 11. 검증 계획 (JUnit 없음 — 데모 러너 + 음성 테스트)

### 11.1 컴파일
`build.ps1` → 오류 0, `out/algobench/Main.class` 및 `out_solutions/*.class` 생성 확인.

### 11.2 실행 (`run-demo.ps1`)
- `CorrectSolution` → 전체 통과.
- `WrongSolution` → ≥1 케이스 WRONG_ANSWER, 케이스 번호 표시.
- `SlowInterruptibleSolution` → TIMEOUT 표시, 프로그램 계속.
- Python 정답 → 전체 통과(**C4: 한글/UTF-8 출력 깨짐 없이 일치**).
- Python timeout → TIMEOUT, 프로세스 종료, 프로그램 계속.
- `reports/result.csv` 생성, header + 풀이별 행, ms·통과수·실패케이스·에러 기록.

### 11.3 확장성 (NFR-03)
comparator를 `WhitespaceNormalizingComparator`/`CaseInsensitiveComparator`로 교체 실행 → `JudgeEngine`/`GradingTask`/`Solution` 수정 없이 정책만 바뀜.

### 11.4 안정성 (NFR-04/05)
- 외부 timeout 후 다음 풀이 계속.
- 런타임 에러가 전체 벤치마크 중단 안 함.
- CSV logger 실패가 콘솔 출력 막지 않음(C7).
- `shutdown()` 항상 호출.

### 11.5 음성 테스트 (C9)
`malformed_example.txt`를 인자로 실행 → `ProblemLoader`가 명확한 예외 메시지로 실패하고 프로그램이 깔끔히 종료(스택트레이스 폭주 X).

### 11.6 격리 회귀 (C3, 선택)
static 카운터를 증가시키는 Java 풀이를 두 케이스에 실행 → 케이스 간 카운터가 누적되지 않음(새 classLoader로 격리됨) 확인.

---

## 12. README 필수 기재
목적 / Pure Java SE 제약 / 빌드·실행 명령 / 문제 포맷 / `.class`·`.jar` 제출 규칙 / 외부 명령 예시 / CSV 위치 / **C4 인코딩 주의(Windows-Korean UTF-8 강제)** / **C1·C2 in-JVM 한계**(URLClassLoader+reflection로 PDF 의도 충족하나 같은 JVM Java 무한 루프·`System.exit()`는 표준 API로 완전 차단 불가, hard 격리는 외부 JVM 사용).

---

## 13. 금지 사항
- Maven/Gradle/JUnit/외부 CSV·기타 런타임 라이브러리 추가 금지.
- 네트워크·DB·GUI 추가 금지.
- 결과 객체에 setter 추가 금지.
- worker 스레드에서 CSV 파일 직접 동시 쓰기 금지.
- **Java same-JVM 무한 루프/`System.exit()`를 완전히 안전하게 막을 수 있다고 README·보고서에 주장 금지.**
- `JavaJarSolution`에서 classLoader/class를 인스턴스 캐시로 재사용 금지(C3 — 케이스 격리 위반).

---

## 14. 최종 산출물 기준
- `src/algobench` 전 패키지·클래스 존재.
- `problems` 정상 2 + 음성 1, `solutions/java`·`solutions/python` 샘플 존재.
- `build.ps1` 컴파일 / `run-demo.ps1` 실행 동작.
- 데모 후 `reports/result.csv` 생성, 한글 출력 깨짐 없음.
- 콘솔만으로 풀이별 성공/실패 케이스/시간/에러 파악 가능.
- `README.md`에 C1·C2·C4 한계·주의 명시.
- `ai_rec.md`에 주요 프롬프트·AI 행동·검증 명령·결과 누적 기록.
```

> 핵심: plan_codex.md 대비 in-JVM 안전성(C1~C3, C5~C6)과 Windows-Korean 인코딩(C4)이 실제 구현/채점을 깨뜨리는 결함이었고, 이를 실행 모델·금지사항·검증으로 못 박았다.
