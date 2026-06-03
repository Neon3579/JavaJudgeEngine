# AlgoBench Claude2 재평가·최종 구현 계획 (plan_claude2.md)

## 1. 문서 목적

이 문서는 `plan_codex2.md`를 다시 평가해 남은 기술 결함을 보정한 **최종 단일 구현 지침**이다. 원본 요구사항은 `202311516 권창민 프로젝트 설계도.pdf`. 계획 계보: `plan.md` → `plan_codex.md` → `plan_claude.md` → `plan_codex2.md` → (본 문서) `plan_claude2.md`.

평가 결론: `plan_codex2.md`는 거의 수렴했다. semi-trusted 실행 모델, exitCode 표, per-call `URLClassLoader`, hard timeout, `process.descendants()` 강제 종료, 빌드/실행 스크립트 분리 등은 모두 타당하므로 **그대로 유지**한다. 다만 아래 6개 델타(D1~D6)에서 실제 구현/채점을 깨뜨리거나 자원을 누수시키는 문제가 남아 이를 고친다.

---

## 2. plan_codex2.md 평가 — 잔여 델타 (D1~D6)

| ID | 심각도 | plan_codex2.md의 문제 | plan_claude2.md의 보정 |
| --- | --- | --- | --- |
| **D1** | HIGH | 자식 JVM 인코딩을 `-Dfile.encoding=UTF-8`만 권장. Java 18+(JEP 400)에선 `file.encoding`은 이미 UTF-8 기본이고, **파이프로 리다이렉트된 `System.out`은 `stdout.encoding`(미설정 시 `native.encoding`=Windows-Korean MS949)** 을 따른다 → 한글 출력이 여전히 깨질 수 있음 (인코딩 정책 회귀) | 자식 JVM 권장 인자를 `-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8`로 정정(필요 시 `-Dfile.encoding=UTF-8` 병기). `java` 첫 토큰 감지 시 `stdout.encoding` 미지정이면 삽입 |
| **D2** | MED | `JavaJarSolution`의 timeout helper executor 정리(`shutdownNow`)가 명시 안 됨 → 케이스마다 스레드 누수 | `execute()` `finally`에서 helper executor `shutdownNow()` 의무화 |
| **D3** | MED | 외부 프로세스 `destroyForcibly` 후 stdout/stderr reader future를 무제한 회수 → 손자 프로세스가 파이프를 잡으면 `execute()` 영구 블로킹 가능 | reader future 회수에 **짧은 grace timeout** 부여, 초과 시 부분 캡처로 진행. `execute()`는 반드시 bounded 시간에 반환 |
| **D4** | MED | comparator/`ExecutionResult`의 null 안전성 미정의 → stdout이 null이면 비교 NPE | `ExecutionResult`의 `stdout`/`stderr`는 **null 금지, 기본 ""**. comparator는 null 인자를 ""로 취급 |
| **D5** | MED | CSV가 append-only라 `run-demo`를 반복하면 행이 중복되고 run 구분 불가. `solutionName` 도출 규칙도 미정의 | CSV는 **run마다 truncate + header 재기록**(단일 run = 단일 파일). `solutionName` 도출 규칙 명문화(아래 7.3) |
| **D6** | LOW | `failedCaseIndexes` 내부 구분자 미정의(comma면 CSV escaping 유발), 멀티라인 stderr 셀, PowerShell ExecutionPolicy, 헤더 unknown 키 처리 미정 | `failedCaseIndexes`는 `;` 구분. `firstErrorMessage`는 첫 줄 + 최대 200자 truncate. ExecutionPolicy 우회 명령 README 기재. 헤더 unknown 키는 무시(전방 호환) |

> 이외 `plan_codex2.md`의 모든 결정은 본 문서가 그대로 계승한다.

---

## 3. 최종 확정 제약

- 언어: **Java SE 17 이상**(preview 기능·불필요한 최신 문법 미사용). 인코딩 동작은 17~21+ 공통 기준.
- 빌드 도구·런타임 외부 라이브러리·테스트 프레임워크 사용 금지.
- 검증: `javac`/`java` + PowerShell 스크립트 + `Main` end-to-end demo runner.
- 범위 제외: 네트워크, DB, 웹 UI, 계정, 대규모 채점 서버.
- 메모리 제한: `Problem.memoryLimitMb` 메타데이터로만 저장, 강제 안 함.
- 기본 실행 환경: **Windows PowerShell**, 경로에 공백·한글 포함 전제.
- 인코딩: 파일·프로세스 stdin/stdout/stderr·CSV 모두 **UTF-8** (자식 JVM은 `stdout.encoding`까지 UTF-8 — D1).
- 제출 코드 신뢰 모델: **semi-trusted**. 악의적/신뢰 불가 코드는 반드시 외부 프로세스로 실행.

---

## 4. PDF 요구사항 매핑

| ID | 요구사항 | 최종 구현 |
| --- | --- | --- |
| FR-01 | 문제 파일 로드 | `ProblemLoader`가 자체 텍스트 포맷을 UTF-8로 파싱 |
| FR-02 | 테스트 케이스 관리 | `Problem`이 `List<TestCase>`를 불변 보관 |
| FR-03 | 풀이 코드 실행 | `Solution` + Java in-JVM / 외부 프로세스 구현 |
| FR-04 | 정답 판정 | `OutputComparator` 구현체로 expected/actual 비교(null-safe — D4) |
| FR-05 | 실행 시간 측정 | 케이스별 `ExecutionResult.executionTime`, 풀이별 합산 |
| FR-06 | 병렬 채점 | `JudgeEngine`이 `GradingTask`를 thread pool에 submit |
| FR-07 | 결과 리포팅 | 콘솔 요약 + CSV 동시 출력(run마다 fresh — D5) |
| NFR-01 | 독립 실행성 | 로컬 파일·프로세스만 사용 |
| NFR-02 | Pure Java | Java SE 표준 API 중심 |
| NFR-03 | 확장성 | 실행/비교/로깅을 인터페이스로 분리 |
| NFR-04 | 안정성 | 외부 hard timeout, 예외 격리, in-JVM 한계 문서화 |
| NFR-05 | 스레드 안전성 | 불변 결과 객체, 메인 스레드 순차 로깅, CSV synchronized |
| NFR-06 | 가독성 | 실패 사유·케이스·시간·stderr 요약(truncate — D6) 출력 |

---

## 5. 디렉토리 구조

```text
자프 과제/
├─ plan.md / plan_codex.md / plan_claude.md / plan_codex2.md / plan_claude2.md
├─ ai_rec.md / CLAUDE.md / README.md
├─ build.ps1            # 컴파일 전용
├─ run-demo.ps1         # 데모 실행 전용
├─ src/algobench/
│  ├─ Main.java
│  ├─ domain/   { Problem.java, TestCase.java }
│  ├─ loader/   { ProblemLoader.java }
│  ├─ solution/ { Solution.java, ExecutionResult.java, JavaJarSolution.java, ExternalProcessSolution.java }
│  ├─ compare/  { OutputComparator.java, ExactOutputComparator.java,
│  │             WhitespaceNormalizingComparator.java, CaseInsensitiveComparator.java }
│  ├─ engine/   { JudgeEngine.java, GradingTask.java }
│  └─ result/   { BenchmarkResult.java, TestCaseResult.java, ResultLogger.java,
│                ConsoleResultLogger.java, CsvResultLogger.java, CompositeResultLogger.java,
│                ResultFormatter.java, CsvResultFormatter.java }
├─ problems/    { a_plus_b.txt, max_of_three.txt, malformed_example.txt }
├─ solutions/
│  ├─ java/     { CorrectSolution.java, WrongSolution.java, SlowInterruptibleSolution.java }
│  └─ python/   { correct_solution.py, timeout_solution.py }
├─ out/ out_solutions/ reports/
```

---

## 6. 문제 파일 포맷

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
- 첫 `###` 이전 = 헤더. `KEY: VALUE` 형식만. 필수 키 `TITLE`, `TIME_LIMIT_MS`(양의 정수), `MEMORY_LIMIT_MB`(양의 정수). **알 수 없는 키는 무시(전방 호환 — D6).**
- 각 케이스는 `###` 라인으로 시작, `INPUT:`/`EXPECTED:`를 각 1회 포함. 중복이면 포맷 오류.
- `INPUT:`~`EXPECTED:` = 입력 본문, `EXPECTED:`~다음 `###` = 기대 출력 본문.
- `###`/`INPUT:`/`EXPECTED:`는 **줄 시작 전체 라인 일치 시에만** 마커. 본문에 이 토큰이 줄 시작으로 필요한 문제는 미지원(문서화).
- `\r\n`/`\r`→`\n` 정규화, 본문 내부 줄바꿈 보존, 끝 trailing newline만 제거. 케이스 index 1부터.
- 형식 오류는 위치·원인 메시지로 `IllegalArgumentException`/`IOException`.

---

## 7. 클래스별 구현 명세

### 7.1 domain

**`TestCase`** (불변): `int index`(≥1), `String input`(non-null), `String expectedOutput`(non-null). `getIndex/getInput/getExpectedOutput`, `boolean matches(String actual, OutputComparator c)` → `c.matches(expectedOutput, actual)`만 호출.

**`Problem`** (불변): `String title`(non-empty), `Duration timeLimit`(>0), `int memoryLimitMb`(>0), `List<TestCase> testCases`(non-empty, `List.copyOf`). getter들.

### 7.2 loader

**`ProblemLoader`**: `Problem loadProblem(String filePath) throws IOException`. `Files.readString(Path.of(filePath), UTF_8)` → 개행 정규화 → §6 라인 단위 파싱. unknown 헤더 키 무시. `malformed_example.txt`는 음성 검증용.

### 7.3 solution

**`Solution`** «interface»
```java
public interface Solution {
    String getName();
    ExecutionResult execute(String input, Duration timeout);
}
```
- `input`을 stdin처럼 주입, 결과를 `ExecutionResult`로 반환.
- 내부 예외는 가능한 한 error-like 결과로 캡슐화. 미캡슐 예외는 `GradingTask`가 케이스 단위로 처리.
- **`getName()` 도출 규칙(D5)**: `JavaJarSolution` = 로드 대상 클래스의 simple name(또는 `.jar`의 Main-Class simple name). `ExternalProcessSolution` = 원본 command 문자열(콘솔/CSV에서 식별 가능하도록). 이름 충돌 시 `Main` 단계에서 `#2` 같은 suffix를 붙여 유일화.

**`ExecutionResult`** «Data Class» (불변): `String stdout`, `String stderr`, `int exitCode`, `Duration executionTime`, `boolean timedOut`. **`stdout`/`stderr`는 null 금지, 기본 ""(D4).** getter들 + `isSuccess()` = `exitCode == 0 && !timedOut`. stderr 존재는 실패 조건 아님(정답 여부는 comparator가 판단).

exitCode 규칙:

| 상황 | exitCode | timedOut |
| --- | --- | --- |
| 정상 종료 | 0 | false |
| Java reflection 실행 중 예외 | 1 | false |
| 외부 프로세스 비정상 종료 | 실제 exit code | false |
| timeout | -1 | true |

**`JavaJarSolution`** — `.class`/`.jar`를 같은 JVM에서 실행 (semi-trusted, 협조적 코드용)

생성자 `JavaJarSolution(String filePath)`. 필드: `static final Object STREAM_LOCK`, `Path filePath`, `String solutionName`. **classLoader/class를 인스턴스 캐시하지 않음.**

로딩 규칙:
- `.class`: default package만. 클래스명 = 파일명 − `.class`, classpath root = 부모 디렉토리.
- `.jar`: manifest `Main-Class` 사용. 없으면 생성자에서 실패.
- 대상 클래스는 `public static void main(String[])` 필수.

실행 규칙 (`execute(input, timeout)`):
1. `STREAM_LOCK` 획득 → Java in-JVM 실행은 사실상 직렬(전역 스트림 보호). 병렬 이득은 외부 프로세스에서 확보.
2. (타이머 밖) **새 `URLClassLoader`로 클래스 재로딩**(케이스 간 static 격리). 로더는 `finally`에서 close.
3. `System.in`을 입력 바이트(UTF-8)로, `System.out`/`System.err`를 `ByteArrayOutputStream`+UTF-8 `PrintStream`으로 교체.
4. `main` 호출만 **daemon 단일 스레드 executor**에 submit, `nanoTime`로 계측.
5. `future.get(timeout)`: 정상→exitCode 0 / `InvocationTargetException` 등→exitCode 1·stderr 스택트레이스 / `TimeoutException`→`cancel(true)`·exitCode -1·timedOut.
6. **`finally`(D2): 원래 스트림 복구 → helper executor `shutdownNow()` → 새 classLoader `close()` → `STREAM_LOCK` 해제.**
7. timeout 케이스의 stdout은 best-effort(부분 캡처일 수 있음, `ByteArrayOutputStream`은 synchronized라 손상은 없음).

한계(README 필수): `cancel(true)`는 interrupt에 반응하는 코드에만 유효 / interrupt 무시 무한 루프 worker는 daemon으로 잔존, 복구 후 진짜 `System.out`을 오염시킬 수 있음 / `System.exit()`는 AlgoBench JVM 전체 종료 / Java SE 표준 API로 완전 차단 불가 / SecurityManager 비의존(JDK 24+ 영구 비활성화) → 신뢰 불가 Java는 외부 JVM으로.

**`ExternalProcessSolution`** — Python/C/C++/실행 파일/별도 JVM Java

생성자 `ExternalProcessSolution(String command)`. 토크나이저: 공백 구분 + double quote 묶음, unmatched quote는 `IllegalArgumentException`.

인코딩 규칙:
- stdin 쓰기·stdout/stderr 읽기는 UTF-8.
- `ProcessBuilder.environment()`에 `PYTHONIOENCODING=utf-8` 설정.
- **D1: 첫 토큰이 `java`/`java.exe`이고 `stdout.encoding`이 지정 안 됐으면, `java` 바로 뒤에 `-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8`를 삽입한다.** (`-Dfile.encoding=UTF-8`은 Java 18+에서 이미 기본이라 단독으론 파이프 출력 인코딩을 못 바꾼다 — 핵심은 `stdout.encoding`.) 그 외 실행 파일은 작성자가 UTF-8 출력으로 맞춘다고 가정.
- 참고: 전형적 알고리즘 I/O는 ASCII라 인코딩이 무의미한 경우가 많지만, 한글/유니코드 출력 비교의 정확성을 위해 정책을 UTF-8로 통일한다.

실행 규칙:
- `ProcessBuilder(commandParts)`, stdin에 input UTF-8로 쓰고 닫기.
- stdout/stderr는 **별도 future로 동시 판독**(파이프 버퍼 deadlock 방지).
- `process.waitFor(timeout.toMillis(), MILLISECONDS)`로 timeout 감지.
- timeout이면 `process.descendants().forEach(ProcessHandle::destroyForcibly)` 후 root도 `destroyForcibly()`, `timedOut=true`, exitCode -1.
- **D3: 종료 후 stdout/stderr reader future를 짧은 grace timeout(예: 200~500ms)으로 회수하고, 초과 시 부분 캡처로 진행한다. `execute()`는 항상 bounded 시간에 반환해야 한다.**
- 정상 종료면 실제 exitCode 기록.

### 7.4 compare

**`OutputComparator`** «interface»: `boolean matches(String expected, String actual)`. **모든 구현체는 null 인자를 ""로 취급(D4).**
- **`ExactOutputComparator`**: `\r\n`/`\r`→`\n`, 줄 끝 공백 제거, 전체 끝 trailing newline 제거 후 완전 일치.
- **`WhitespaceNormalizingComparator`**: trim + 연속 whitespace 1칸 축약 후 일치.
- **`CaseInsensitiveComparator`**: Exact 정규화 후 `equalsIgnoreCase`.

### 7.5 engine

**`GradingTask implements Callable<BenchmarkResult>`** (= 설계도 «interface» CallableResult). 필드 `Problem`, `Solution`, `OutputComparator`.
`call()`:
- 각 케이스 `ExecutionResult r = solution.execute(testCase.getInput(), problem.getTimeLimit())`.
- 판정 우선순위: `r.timedOut` → `TIMEOUT` / `r.exitCode != 0` → `RUNTIME_ERROR`(+stderr 요약) / `comparator.matches(expected, r.stdout)==false` → `WRONG_ANSWER` / else 통과.
- `TestCaseResult`에 expected와 `actual=r.stdout`을 항상 기록(디버깅용).
- 케이스 처리 중 미캡슐 예외 발생 시 사유 `EXCEPTION`으로 errorMessage에 담고 다음 케이스 진행(NFR-04).
- `totalExecutionTime` = 케이스별 `executionTime` 합. 전 케이스 통과 시 `allPassed=true`.

**`JudgeEngine`**: 필드 `ExecutorService threadPool`, `ResultLogger logger`. 생성자 `(ResultLogger)` / `(ResultLogger, int threadCount)`. 기본 thread = `Math.max(1, availableProcessors())`.
- `List<Future<BenchmarkResult>> evaluateAllAsync(...)` — 풀이 단위 submit.
- `List<BenchmarkResult> evaluateAll(...)` — 제출 순서대로 `get()` 수집, **수집 루프(메인 스레드)에서 순차 `logger.log`**(NFR-05). worker에서 로깅 금지.
- `shutdown()` → `threadPool.shutdown()`. `Main`은 `finally`에서 호출.

### 7.6 result

**`TestCaseResult`** (불변): `int testCaseIndex`, `boolean passed`, `String expectedOutput`, `String actualOutput`, `Duration executionTime`, `String errorMessage`(통과 시 ""). 실패 사유 토큰: `TIMEOUT`/`RUNTIME_ERROR`/`WRONG_ANSWER`/`EXCEPTION`.

**`BenchmarkResult`** (불변): `String solutionName`, `boolean allPassed`, `Duration totalExecutionTime`, `List<TestCaseResult> caseResults`(`List.copyOf`). getter들 + `isAllPassed`, `getPassedCount`, `getTotalCount`.

**`ResultLogger`** «interface»: `void log(BenchmarkResult result) throws IOException`.

**`ConsoleResultLogger`**: solution name, passed/total, allPassed, totalExecutionTimeMs, failed case indexes, first failure reason, stderr/expected-actual 요약(truncate).

**`CsvResultLogger`**: `Path csvFilePath`, `ResultFormatter formatter`. **D5: 생성/run 시작 시 `reports` 생성 후 파일을 truncate(새로 쓰기)하고 `formatter.header()` 1회 기록 → run마다 깨끗한 파일.** 이후 결과당 1행 append. `log`는 `synchronized`. (히스토리 누적이 필요하면 파일명에 timestamp를 붙이는 변형을 README에 옵션으로 안내.)

**`CompositeResultLogger`**: `CompositeResultLogger(List<ResultLogger>)`. 등록 순서대로 호출, 한 logger 실패해도 다음 계속, 내부에서 잡은 예외는 `System.err` 경고로만 남기고 **재throw 안 함**(콘솔 출력이 CSV 실패에 막히지 않음).

**`ResultFormatter`** «interface»: `String header()`, `String format(BenchmarkResult)`.

**`CsvResultFormatter`**: 컬럼
```text
solutionName,allPassed,passedCount,totalCount,totalExecutionTimeMs,failedCaseIndexes,firstErrorMessage
```
- **D6: `failedCaseIndexes`는 `;`로 결합**(예: `2;5`). `firstErrorMessage`는 **첫 줄 + 최대 200자 truncate**.
- RFC 4180 escaping: comma/quote/newline 포함 시 `"`로 감싸고 내부 `"`→`""`. null→"".

---

## 8. Main CLI

실행: `java -cp out algobench.Main <problemFile> <solution...>`

동작: 인자 2개 미만 → 사용법 출력 후 종료. 첫 인자=문제 파일, 나머지=풀이. `.class`/`.jar`로 끝나면 `JavaJarSolution`, 그 외 `ExternalProcessSolution`. `ProblemLoader` 로드 → comparator 기본 `ExactOutputComparator` → logger = `CompositeResultLogger[ConsoleResultLogger, CsvResultLogger(Path.of("reports","result.csv"), new CsvResultFormatter())]` → solution name 유일화(D5) → `JudgeEngine.evaluateAll` → `finally`에서 `shutdown()`.

데모:
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

- `problems/a_plus_b.txt`(두 정수 합), `problems/max_of_three.txt`(세 정수 최댓값), `problems/malformed_example.txt`(헤더/`EXPECTED:` 누락 — 음성 검증).
- `solutions/java/`: `CorrectSolution`(정답), `WrongSolution`(일부 오답), `SlowInterruptibleSolution`(제한 초과하나 interrupt 반응).
- `solutions/python/`: `correct_solution.py`(정답), `timeout_solution.py`(timeout 유발).

---

## 10. PowerShell 스크립트

**`build.ps1`** (컴파일 전용):
1. `out`, `out_solutions`, `reports`를 `New-Item -ItemType Directory -Force`로 생성.
2. `javac -encoding UTF-8 -d out (Get-ChildItem -Recurse src -Filter *.java).FullName`
3. `javac -encoding UTF-8 -d out_solutions (Get-ChildItem -Recurse solutions/java -Filter *.java).FullName`
4. 컴파일 실패 시 즉시 `exit $LASTEXITCODE`.

**`run-demo.ps1`** (실행 전용): §8 데모 명령 실행.

> 경로에 공백·한글 → `(Get-ChildItem ...).FullName` 배열 splatting으로 각 경로가 개별 인자로 전달됨.
> **D6: ExecutionPolicy로 `.ps1` 실행이 막히면** `powershell -ExecutionPolicy Bypass -File .\build.ps1` 형태로 실행(README 기재).

---

## 11. 구현 순서

1. `README.md`, `build.ps1`, `run-demo.ps1`, 디렉토리 골격.
2. `domain`.
3. `loader` + 문제 파일 3개(정상 2 + 음성 1).
4. `compare`(인터페이스 + 3 구현체, null-safe).
5. `solution`(`Solution`, `ExecutionResult`, `ExternalProcessSolution`, `JavaJarSolution`) — D1~D4 반영.
6. `result`(데이터 2 + 로거 3 + 포매터 2) — D5/D6 반영.
7. `engine`.
8. `Main`(name 유일화).
9. 샘플 Java/Python 풀이.
10. end-to-end 검증 후 `ai_rec.md` 기록.
11. 구현 후 `CLAUDE.md`가 `plan_claude2.md`를 최신 기준으로 안내하도록 갱신.

---

## 12. 검증 계획

### 12.1 컴파일 — `.\build.ps1`
오류 0, `out/algobench/Main.class`·`out_solutions/CorrectSolution.class` 생성.

### 12.2 데모 — `.\run-demo.ps1`
- `CorrectSolution` 전체 통과 / `WrongSolution` `WRONG_ANSWER`+케이스 번호 / `SlowInterruptibleSolution` `TIMEOUT`이며 프로그램 계속 / Python 정답 전체 통과(**한글 출력 깨짐 없음 — D1**) / Python timeout `TIMEOUT`·프로세스 종료 / `reports/result.csv` 생성(header + 풀이별 행).
- **D5: `run-demo`를 2회 실행해도 CSV 행이 중복되지 않음**(run마다 fresh).

### 12.3 포맷 음성 — `java -cp out algobench.Main problems/malformed_example.txt out_solutions/CorrectSolution.class`
`ProblemLoader`가 명확한 오류 메시지 출력, 프로그램 깔끔 종료(스택트레이스 폭주 X).

### 12.4 확장성
comparator를 `WhitespaceNormalizingComparator`/`CaseInsensitiveComparator`로 교체 → `JudgeEngine`/`GradingTask`/`Solution` 수정 없이 정책만 바뀜.

### 12.5 안정성 / 자원
- 외부 timeout 후 다음 풀이 계속 / 런타임 예외가 전체 중단 안 함 / CSV 실패해도 콘솔 출력됨 / `shutdown()` 항상 호출.
- **D2/D3: 다수 케이스 반복 실행 후에도 스레드/프로세스가 누적·잔존하지 않고 `execute()`가 bounded 시간에 반환**.

### 12.6 격리 회귀(D3 of plan_claude, 선택)
static 카운터 증가 Java 풀이를 2케이스 실행 → 케이스 간 누적 없음(새 classLoader 격리) 확인.

---

## 13. README 필수 내용

목적 / Pure Java SE 제약 / `build.ps1`·`run-demo.ps1` 및 수동 `javac`·`java` 명령 / **ExecutionPolicy 우회법(D6)** / 문제 포맷 / `.class`·`.jar` 제출 규칙 / 외부 명령 예시 / **Python·자식 JVM UTF-8 실행 주의 — 자식 JVM은 `-Dstdout.encoding=UTF-8` 필요(D1)** / CSV 위치·run마다 덮어씀(D5) / `JavaJarSolution` 한계(완전 격리 아님, `System.exit()`·무한 루프 차단 불가, 신뢰 불가 코드는 외부 JVM) / 벤치마크 시간 한계(단일 측정은 JIT warmup·OS scheduling 영향, 반복·통계는 범위 외).

---

## 14. 금지 사항

- Maven/Gradle/JUnit/외부 CSV·기타 런타임 라이브러리 추가 금지.
- 네트워크/DB/GUI/웹 UI 추가 금지.
- 결과 객체 setter 추가 금지.
- worker thread에서 CSV 파일 직접 동시 쓰기 금지.
- `JavaJarSolution`이 신뢰 불가 코드를 안전 격리한다고 주장 금지.
- SecurityManager 기반 격리 구현 금지.
- `URLClassLoader`/`Class<?>`를 `JavaJarSolution` 인스턴스 필드로 캐시(케이스 간 static 공유) 금지.
- **`ExecutionResult.stdout`/`stderr`를 null로 두기 금지(D4).**
- **자식 JVM 인코딩을 `-Dfile.encoding`만으로 해결했다고 가정 금지 — `stdout.encoding` 필요(D1).**

---

## 15. 최종 산출물 기준

- `src/algobench` 전 패키지·클래스 존재.
- 문제 정상 2 + 음성 1, Java 샘플 3 + Python 샘플 2 존재.
- `.\build.ps1` 성공, `.\run-demo.ps1` 성공.
- 콘솔만으로 풀이별 성공/실패 케이스/시간/에러 파악 가능.
- `reports/result.csv` 생성, UTF-8로 읽힘, run 반복 시 중복 없음.
- `README.md`에 in-JVM 한계 + UTF-8(특히 `stdout.encoding`) 주의 명시.
- `ai_rec.md`에 프롬프트·AI 행동·검증 명령·결과 누적 기록.

---

## 16. 수렴 메모

`plan_codex2.md`는 설계 의도와 Java SE 제약을 거의 정확히 반영했고, 본 문서의 보정은 **인코딩 정책 회귀(D1) 1건 + 자원/안전 정밀 보정(D2~D4) + 리포트 품질(D5~D6)** 에 한정된다. 구조·인터페이스·실행 모델은 안정화되었으므로, 다음 단계는 추가 평가보다 **실제 구현 착수**가 적절하다.
