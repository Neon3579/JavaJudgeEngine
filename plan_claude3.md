# AlgoBench Claude3 최종 구현 계획 (plan_claude3.md)

> **For agentic workers:** REQUIRED SUB-SKILL — `superpowers:executing-plans` 또는 `superpowers:subagent-driven-development`로 task 단위 구현. 단계는 `- [ ]` 체크박스로 추적.

**Goal:** Pure Java SE 로컬 알고리즘 벤치마킹 도구 AlgoBench 구현. 로컬 문제 파일 로드 → 여러 풀이 실행 → 출력 비교 → 실행 시간 측정 → **풀이 간 비교 리포트**(console + CSV).

**Tech Stack:** Java SE 17+, PowerShell, `javac`, `java`. Maven/Gradle/JUnit/런타임 외부 라이브러리 없음.

---

## 1. 문서 목적 및 수렴 선언

이 문서는 `plan_codex3.md`를 재평가한 결과다. 계보: `plan.md` → `plan_codex.md` → `plan_claude.md` → `plan_codex2.md` → `plan_claude2.md` → `plan_codex3.md` → (본 문서) `plan_claude3.md`.

**평가 결론 — 계획은 수렴했다.** `plan_codex3.md`는 구조·인터페이스·실행 모델·자원 관리·인코딩·검증을 모두 구현 가능한 수준으로 고정했고, 로컬 환경(Java 25, `stdout.encoding=MS949`)까지 검증해 D1 인코딩 결정을 실측으로 뒷받침했다. 새 아키텍처 결함은 없다.

본 문서의 보정은 **신규 아키텍처가 아니라**, 6회 반복 동안 간과된 **벤치마킹 핵심 산출물(풀이 간 비교 리포트)** 보강과 소수 micro-refinement(G1~G5)에 한정한다. 이 문서 확정 후 다음 단계는 **추가 평가가 아니라 실제 구현 착수**다.

---

## 2. plan_codex3.md 평가 — 보정 델타 (G1~G5)

### 2.1 그대로 계승하는 결정
`Solution.execute(input, timeout)` 시그니처, semi-trusted in-JVM 모델, 외부 프로세스 hard timeout + process-tree kill, per-call `URLClassLoader`, `ExecutionResult` non-null·comparator null-safe, CSV lazy truncate(run마다 fresh), `failedCaseIndexes` `;` 구분, `firstErrorMessage` 첫 줄 200자, `UniqueNamedSolution` 유일화, 자식 JVM `-Dstdout.encoding/-Dstderr.encoding=UTF-8` 삽입(E1/E2), duplicate known 헤더 키 오류·unknown 키 무시(E5), reader grace timeout + executor shutdownNow(E6), descendants snapshot→root→재조회 kill 순서(E8), `build.ps1`/`run-demo.ps1` 분리, task 체크리스트 — **전부 유지.**

### 2.2 보정하는 델타

| ID | 심각도 | plan_codex3.md의 남은 문제 | plan_claude3.md 결정 |
| --- | --- | --- | --- |
| **G1** | MED (기능 누락) | 풀이를 **개별** 로깅만 하고 풀이 간 **비교/순위 요약**이 없음. 그러나 PDF 1.3·3.4는 "여러 풀이의 실행 시간·실패 원인을 **비교**"가 핵심 — 벤치마킹 도구의 본질 산출물이 빠짐 | `BenchmarkSummaryPrinter`(또는 `Main`의 요약 단계)로 전 풀이를 **나란히 비교**: 통과 여부 → 총 시간 오름차순 정렬, 가장 빠른 정답 풀이 표시. CSV는 이미 행 단위라 스프레드시트 정렬로 보완 |
| **G2** | LOW (정밀도) | `totalExecutionTimeMs`를 `Duration.toMillis()`로 보고 → 마이크로초대 풀이(A+B 등)가 0ms로 뭉개짐. PDF 3.4 "정밀한 성능 측정"에 미달 | Duration의 나노초를 **소수점 ms(예: `12.345`) 또는 마이크로초 컬럼**으로 포맷. 내부 저장은 `Duration`(나노 보존) 유지, 포맷 시 `toNanos()/1_000_000.0` |
| **G3** | LOW | `JavaJarSolution`이 클래스를 **스트림 스왑 전** 로드 → 대상 클래스 static initializer의 stdout 출력이 캡처되지 않고 실제 콘솔로 샘 | 순서를 **스트림 스왑 → 클래스 로드(static init 포함, 타이머 밖) → 타이머 시작 → `main` invoke**로 고정. static init 출력도 캡처 |
| **G4** | LOW | `JudgeEngine.shutdown()`이 단순 `threadPool.shutdown()`만 → 만일 한 task가 비정상적으로 지연되면 비-데몬 풀 스레드가 JVM 종료를 지연 | `shutdown()` → `awaitTermination(짧은 시간)` → 미종료 시 `shutdownNow()` 안전망 |
| **G5** | 문서 | 구현 후 `CLAUDE.md`가 `plan_codex3.md`를 가리키게 한다고 명시 | 최신 기준 문서를 **`plan_claude3.md`**로 안내하도록 갱신 |

> 이외 `plan_codex3.md`의 모든 세부 결정은 본 문서가 그대로 계승한다.

---

## 3. 최종 확정 제약
- Java SE 17+ (preview·불필요 최신 문법 미사용). Maven/Gradle/JUnit/런타임 외부 라이브러리 금지.
- 검증: `javac`/`java` + PowerShell + `Main` end-to-end demo runner.
- 범위 제외: 네트워크, DB, 웹/GUI, 계정, 대규모 채점 서버.
- 메모리 제한: `Problem.memoryLimitMb` 메타데이터로만 저장, 강제 안 함.
- 인코딩: 파일·문제 포맷·CSV·프로세스 stdin/stdout/stderr 모두 UTF-8. 자식 JVM은 `stdout.encoding`/`stderr.encoding`까지 UTF-8(로컬 실측: Java 25, native/stdout/stderr = MS949).
- 기본 환경: Windows PowerShell, 경로에 공백·한글 포함.
- 제출 코드 semi-trusted. 신뢰 불가 Java는 별도 JVM + `ExternalProcessSolution`.

---

## 4. PDF 요구사항 매핑

| ID | 요구사항 | 구현 |
| --- | --- | --- |
| FR-01 | 문제 파일 로드 | `ProblemLoader`(UTF-8 자체 포맷) |
| FR-02 | 테스트 케이스 관리 | `Problem`이 `List<TestCase>` 불변 보관 |
| FR-03 | 풀이 코드 실행 | `Solution` + `JavaJarSolution`/`ExternalProcessSolution` |
| FR-04 | 정답 판정 | `OutputComparator` 구현체(null-safe) |
| FR-05 | 실행 시간 측정 | `ExecutionResult.executionTime`(나노 보존), 합산 → 정밀 포맷(G2) |
| FR-06 | 병렬 채점 | `JudgeEngine`이 `GradingTask`를 thread pool에 submit |
| FR-07 | 결과 리포팅 | console + CSV + **풀이 간 비교 요약(G1)** |
| NFR-01~06 | 독립/PureJava/확장/안정/스레드안전/가독 | codex3 매핑 그대로 + G1 비교 요약으로 가독·비교 강화 |

---

## 5. 디렉토리 구조

```text
자프 과제/
├─ plan*.md (계보 7종)  ai_rec.md  CLAUDE.md  README.md
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
│                ResultFormatter.java, CsvResultFormatter.java, BenchmarkSummaryPrinter.java }  # G1 추가
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

규칙: 첫 `###` 이전=헤더(`KEY: VALUE`만). 필수 키 `TITLE`(non-empty)/`TIME_LIMIT_MS`(양정수)/`MEMORY_LIMIT_MB`(양정수). **unknown 키 무시, duplicate known 키 오류.** 케이스는 `###`로 시작, `INPUT:`/`EXPECTED:` 각 1회(중복 시 오류). `INPUT:`~`EXPECTED:`=입력, `EXPECTED:`~다음 `###`=기대 출력. 마커는 줄 전체 정확 일치 시만. `\r\n`/`\r`→`\n`, 본문 내부 줄바꿈 보존·끝 trailing newline만 제거. index 1부터. 오류는 line number 포함 메시지로 `IllegalArgumentException`/`IOException`.

---

## 7. 클래스별 구현 명세

### 7.1 domain
- **`TestCase`** (불변): `int index`(≥1), `String input`(non-null), `String expectedOutput`(non-null). getter + `matches(actual, comparator)` → `comparator.matches(expectedOutput, actual)` 위임.
- **`Problem`** (불변): `String title`(non-empty), `Duration timeLimit`(>0), `int memoryLimitMb`(>0), `List<TestCase>`(non-empty, `List.copyOf`). getter.

### 7.2 loader
- **`ProblemLoader`**: `Problem loadProblem(String) throws IOException`. `Files.readString(UTF_8)` → 개행 정규화 → §6 라인 파싱, 오류 메시지에 line number.

### 7.3 solution
- **`Solution`** «interface»: `String getName()`, `ExecutionResult execute(String input, Duration timeout)`. 내부 예외는 가능한 한 `ExecutionResult`로 캡슐화, 미캡슐은 `GradingTask`가 케이스 단위 처리.
- **`ExecutionResult`** (불변): `String stdout`(null→""), `String stderr`(null→""), `int exitCode`, `Duration executionTime`(non-null, **나노 보존**), `boolean timedOut`. `isSuccess()`=`exitCode==0 && !timedOut`. exitCode 표: 정상 0 / Java 예외 1 / 외부 비정상 실제코드 / timeout -1(timedOut true).
- **`JavaJarSolution`** (semi-trusted, 협조적 Java용). 필드 `static final Object STREAM_LOCK`, `Path filePath`, `String solutionName`. 로딩: `.class`=default package, 클래스명=파일명−`.class`, root=부모 디렉토리 / `.jar`=manifest `Main-Class`(없으면 생성자 실패) / `public static void main(String[])` 필수 / classLoader·class **인스턴스 캐시 금지**.
  실행(`execute`):
  1. `STREAM_LOCK` 획득(Java 실행 사실상 직렬화).
  2. `System.in`을 UTF-8 입력으로, `System.out`/`System.err`를 `ByteArrayOutputStream`+UTF-8 `PrintStream`으로 교체.
  3. **(G3) 스왑 후** 새 `URLClassLoader`로 클래스 로드(static init 출력도 캡처, 타이머 밖).
  4. 타이머 시작 → `main`을 **daemon single-thread executor**에 submit → `future.get(timeout)`.
  5. 정상 exitCode 0 / `InvocationTargetException` 등 exitCode 1·stderr 스택트레이스 / `TimeoutException` `cancel(true)`·exitCode -1·timedOut.
  6. `finally`: 원래 스트림 복구 → helper executor `shutdownNow()` → `URLClassLoader.close()` → lock 해제. timeout 케이스 stdout은 best-effort 부분 캡처.
  한계(README): `cancel(true)`는 interrupt 반응 코드만 / interrupt 무시 무한 루프 worker는 daemon 잔존, 복구 후 실제 `System.out` 오염 가능 / `System.exit()`는 AlgoBench JVM 전체 종료 / 표준 API로 완전 차단 불가 / SecurityManager 비의존 → 신뢰 불가 Java는 외부 JVM.
- **`ExternalProcessSolution`**. 토크나이저: 공백 구분 + double quote 묶음, backslash escape·nested quote 미지원, unmatched quote `IllegalArgumentException`.
  인코딩: stdin/stdout/stderr UTF-8, env `PYTHONIOENCODING=utf-8`. **첫 토큰 basename(`Path.of(token).getFileName()`)이 `java`/`java.exe`면 자식 JVM으로 보고, `-Dstdout.encoding=`/`-Dstderr.encoding=` 없으면 java 토큰 바로 뒤 삽입(`=UTF-8`).** `-Dfile.encoding`은 무관(파이프 출력은 stdout.encoding이 결정).
  실행: `ProcessBuilder(commandParts)`, stdin UTF-8 쓰고 닫기, stdout/stderr **별도 future 동시 판독**(순차 read 금지). `waitFor(timeout)` 초과 시 ① descendants snapshot `destroyForcibly` ② root `destroyForcibly` ③ 짧게 대기 ④ 남은 descendants 재조회 kill. reader future는 **200~500ms grace로 회수**, 실패 시 부분 캡처 + reader executor `shutdownNow()`. `execute()`는 timeout 상황에서도 bounded 반환. 정상 시 실제 exitCode.
  시간 측정: 외부=process start~종료/timeout 처리까지 wall-clock, in-JVM=`main` 구간. **모드 간 비교에 runner overhead 차이 → README 명시.**

### 7.4 compare
- **`OutputComparator`** «interface»: `boolean matches(String expected, String actual)`. **모든 구현체 null→"" 취급.**
- `ExactOutputComparator`: `\r\n`/`\r`→`\n`, 줄 끝 공백 제거, 끝 trailing newline 제거 후 일치.
- `WhitespaceNormalizingComparator`: trim + 연속 whitespace 1칸 축약 후 일치.
- `CaseInsensitiveComparator`: Exact 정규화 후 `equalsIgnoreCase`.

### 7.5 engine
- **`GradingTask implements Callable<BenchmarkResult>`**. 필드 `Problem`/`Solution`/`OutputComparator`. `call()`: 케이스마다 `execute(input, timeLimit)`. 판정 우선순위 TIMEOUT → RUNTIME_ERROR(exitCode≠0, +stderr 요약) → WRONG_ANSWER → 통과. 미캡슐 예외는 `EXCEPTION` 사유로 errorMessage 기록 후 다음 케이스. `actualOutput`엔 stdout 항상 기록. `totalExecutionTime`=케이스별 합(나노 보존).
- **`JudgeEngine`**: 필드 `ExecutorService threadPool`/`ResultLogger logger`. 생성자 `(ResultLogger)`/`(ResultLogger, int)`. 기본 thread `Math.max(1, availableProcessors())`. `evaluateAllAsync`/`evaluateAll`(제출 순서 수집, **메인 스레드 순차 `logger.log`**)/`shutdown()`. **(G4) `shutdown()` = `shutdown()` → `awaitTermination(짧게)` → 미종료 시 `shutdownNow()`.**

### 7.6 result
- **`TestCaseResult`** (불변): `int testCaseIndex`, `boolean passed`, `String expectedOutput`, `String actualOutput`, `Duration executionTime`, `String errorMessage`(통과 시 ""). 실패 사유 토큰 TIMEOUT/RUNTIME_ERROR/WRONG_ANSWER/EXCEPTION. 문자열 null→"".
- **`BenchmarkResult`** (불변): `String solutionName`(non-empty), `boolean allPassed`, `Duration totalExecutionTime`, `List<TestCaseResult>`(`List.copyOf`). getter + `isAllPassed`/`getPassedCount`/`getTotalCount`.
- **`ResultLogger`** «interface»: `void log(BenchmarkResult) throws IOException`.
- **`ConsoleResultLogger`**: 풀이별 — name, passed/total, allPassed, **총 시간(소수 ms — G2)**, 실패 케이스 번호, 첫 실패 사유, stderr/expected-actual 요약(truncate).
- **`CsvResultLogger`**: 필드 `Path csvFilePath`/`ResultFormatter formatter`/`boolean initialized`. 첫 `log` 전 **lazy init**(reports 생성 → 파일 truncate → header 1회), 인스턴스당 1회. `log` `synchronized`, 결과당 1행 append.
- **`CompositeResultLogger`**: 등록 순서 호출, 한 logger 실패해도 계속, 예외는 `System.err` 경고만(재throw 금지).
- **`ResultFormatter`** «interface»: `String header()`, `String format(BenchmarkResult)`.
- **`CsvResultFormatter`**: 컬럼 `solutionName,allPassed,passedCount,totalCount,totalExecutionTimeMs,failedCaseIndexes,firstErrorMessage`. **(G2) `totalExecutionTimeMs`는 `toNanos()/1_000_000.0` 소수 ms.** `failedCaseIndexes` `;` 결합, `firstErrorMessage` 첫 줄 200자. RFC 4180 escaping(comma/quote/newline → `"`로 감싸고 내부 `"`→`""`), null→"".
- **`BenchmarkSummaryPrinter`** (G1, 신규): `void printComparison(List<BenchmarkResult> results, PrintStream out)`. 전 풀이를 **비교 표**로 출력 — 정렬: 통과(allPassed=true) 우선 → 총 시간 오름차순. 컬럼: 순위 / solutionName / PASS·FAIL(통과수/전체) / 총 시간(소수 ms) / 첫 실패 사유. 가장 빠른 정답 풀이를 강조 표시. (PDF 1.3 "여러 풀이 실행 시간·실패 원인 비교" 직접 충족.) `Main`이 `evaluateAll` 후 1회 호출. 결과 객체만 읽는 순수 함수 → 스레드 안전.

---

## 8. Main CLI

`java -cp out algobench.Main <problemFile> <solution...>`

동작: 인자 2개 미만 → 사용법 후 종료. 첫 인자=문제 파일, 나머지=풀이(`.class`/`.jar`→`JavaJarSolution`, else `ExternalProcessSolution`). `ProblemLoader` 로드 → **`UniqueNamedSolution` wrapper로 이름 유일화**(중복 시 `name#2`, `name#3`; `Main` private static nested class, `getName()`=displayName, `execute`는 delegate 위임) → comparator 기본 `ExactOutputComparator` → logger=`CompositeResultLogger[ConsoleResultLogger, CsvResultLogger(Path.of("reports","result.csv"), new CsvResultFormatter())]` → `List<BenchmarkResult> results = engine.evaluateAll(...)` → **`BenchmarkSummaryPrinter.printComparison(results, System.out)`(G1)** → `finally`에서 `engine.shutdown()`.

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
- `problems/`: `a_plus_b.txt`(두 정수 합), `max_of_three.txt`(세 정수 최댓값), `malformed_example.txt`(헤더/`EXPECTED:` 누락 — 음성 검증).
- `solutions/java/`: `CorrectSolution`(정답), `WrongSolution`(일부 오답), `SlowInterruptibleSolution`(제한 초과하나 interrupt 반응).
- `solutions/python/`: `correct_solution.py`(정답), `timeout_solution.py`(timeout 유발).

---

## 10. PowerShell 스크립트
**`build.ps1`**(컴파일만): `out`/`out_solutions`/`reports` `New-Item -ItemType Directory -Force` 생성 → `javac -encoding UTF-8 -d out (Get-ChildItem -Recurse src -Filter *.java).FullName` → `javac -encoding UTF-8 -d out_solutions (Get-ChildItem -Recurse solutions/java -Filter *.java).FullName` → 실패 시 `exit $LASTEXITCODE`.
**`run-demo.ps1`**(실행만): §8 데모 명령.
ExecutionPolicy 차단 시: `powershell -ExecutionPolicy Bypass -File .\build.ps1` / `... .\run-demo.ps1` (README 안내).

---

## 11. 구현 순서 (task)

### Task 1: 문서·스크립트 골격
- Create: `README.md`, `build.ps1`, `run-demo.ps1`; dirs `src/algobench`, `problems`, `solutions/java`, `solutions/python`, `reports`
- [ ] README에 목적·제약·빌드/실행·in-JVM 한계·UTF-8(stdout.encoding) 정책 작성.
- [ ] `build.ps1`(컴파일만)/`run-demo.ps1`(실행만) 작성, `New-Item -Force`로 디렉토리.

### Task 2: domain
- Create: `domain/TestCase.java`, `domain/Problem.java`
- [ ] 불변 + 생성자 검증 + `List.copyOf`.

### Task 3: loader + 문제 파일
- Create: `loader/ProblemLoader.java`, `problems/{a_plus_b,max_of_three,malformed_example}.txt`
- [ ] UTF-8 읽기·개행 정규화, header 검증(dup known 오류/unknown 무시), 케이스 라인 파싱, line number 오류, 정상 2 + 음성 1.

### Task 4: compare
- Create: `compare/{OutputComparator,ExactOutputComparator,WhitespaceNormalizingComparator,CaseInsensitiveComparator}.java`
- [ ] 인터페이스 + null→"" + 3 정책.

### Task 5: solution
- Create: `solution/{Solution,ExecutionResult,JavaJarSolution,ExternalProcessSolution}.java`
- [ ] `Solution`/`ExecutionResult`(non-null, Duration 나노 보존).
- [ ] `JavaJarSolution`: per-call `URLClassLoader`, STREAM_LOCK, **스왑→로드→타이머→invoke 순서(G3)**, daemon executor timeout, finally 복구·shutdownNow·close.
- [ ] `ExternalProcessSolution`: tokenizer, UTF-8 env, 자식 JVM stdout/stderr.encoding 삽입(basename 감지, 중복 방지), 동시 판독, process-tree timeout kill, reader grace timeout + shutdownNow.

### Task 6: result + 비교 요약
- Create: `result/{TestCaseResult,BenchmarkResult,ResultLogger,ConsoleResultLogger,CsvResultLogger,CompositeResultLogger,ResultFormatter,CsvResultFormatter,BenchmarkSummaryPrinter}.java`
- [ ] 결과 데이터 불변.
- [ ] console logger 풀이별 요약(소수 ms — G2).
- [ ] CSV logger lazy truncate/header + synchronized append.
- [ ] CSV formatter: 소수 ms(G2), `;` 케이스, 200자 에러, RFC 4180 escaping.
- [ ] composite logger 예외 삼킴 + stderr 경고.
- [ ] **`BenchmarkSummaryPrinter`(G1): 통과 우선 → 총 시간 오름차순 비교 표, 최속 정답 강조.**

### Task 7: engine
- Create: `engine/{GradingTask,JudgeEngine}.java`
- [ ] `GradingTask` 케이스 실행·판정·예외 격리.
- [ ] `JudgeEngine` submit/제출순서 수집/메인 순차 로깅, **`shutdown()` awaitTermination→shutdownNow(G4)**.

### Task 8: CLI + 샘플 풀이
- Create: `Main.java`, `solutions/java/{CorrectSolution,WrongSolution,SlowInterruptibleSolution}.java`, `solutions/python/{correct_solution,timeout_solution}.py`
- [ ] CLI 파싱·solution 생성, `UniqueNamedSolution` 유일화, comparator·composite logger 연결, **요약 출력 호출(G1)**.
- [ ] Java/Python 샘플 작성.

### Task 9: 검증·기록
- Modify: `ai_rec.md`; after impl: `CLAUDE.md`
- [ ] `.\build.ps1` / `.\run-demo.ps1` 실행·기록.
- [ ] malformed 음성 검증.
- [ ] demo 2회 → CSV fresh 확인.
- [ ] **비교 요약 표가 최속 정답 풀이를 정확히 정렬·강조하는지 확인(G1).**
- [ ] **`CLAUDE.md`가 `plan_claude3.md`를 최신 기준으로 안내(G5).**
- [ ] 검증 명령·결과 `ai_rec.md` append.

---

## 12. 검증 계획
1. **컴파일** `.\build.ps1` → 오류 0, `out/algobench/Main.class`·`out_solutions/CorrectSolution.class`.
2. **데모** `.\run-demo.ps1` → Correct 전체 통과 / Wrong WRONG_ANSWER+케이스 / SlowInterruptible TIMEOUT·계속 / Python 정답 통과(**한글 안 깨짐**) / Python timeout TIMEOUT·프로세스 종료 / `reports/result.csv` 생성 / **비교 요약 표 출력(G1)**.
3. **CSV fresh** `.\run-demo.ps1` 2회 → 누적 아님, header 1 + 현재 run 행만.
4. **음성** `java -cp out algobench.Main problems/malformed_example.txt out_solutions/CorrectSolution.class` → 명확한 오류 메시지·깔끔 종료.
5. **확장성** comparator를 Whitespace/CaseInsensitive로 교체 → 엔진/Task/Solution 수정 없이 정책만 바뀜.
6. **안정성·자원** 외부 timeout 후 계속 / 런타임 예외가 전체 중단 안 함 / CSV 실패해도 콘솔 출력 / `shutdown()` 항상 호출 / timeout 후 프로세스·reader thread 누적 없음.
7. **인코딩 실측** `java -XshowSettings:properties -version`로 `stdout.encoding` 확인 → 외부 JVM 실행 시 UTF-8 삽입 확인.
8. **정밀도(G2)** 빠른 풀이의 총 시간이 0이 아닌 소수 ms로 표시되는지 확인.

---

## 13. README 필수 내용
목적 / Pure Java SE 제약 / `build.ps1`·`run-demo.ps1`·수동 `javac`·`java` / ExecutionPolicy 우회 / 문제 포맷 / `.class`·`.jar` 제출 규칙 / 외부 명령 예시 / Python·**자식 JVM UTF-8(특히 `-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8`)** 주의 / CSV 위치·run마다 fresh / **비교 요약 리포트 설명(G1)** / `JavaJarSolution` 한계(완전 격리 아님, `System.exit()`·무한 루프 차단 불가 → 신뢰 불가 코드는 외부 JVM) / 시간 한계(단일 측정은 JIT warmup·OS scheduling 영향, in-JVM과 외부 프로세스 runner overhead 상이, 반복·통계는 범위 외).

---

## 14. 금지 사항
Maven/Gradle/JUnit/외부 CSV·런타임 라이브러리 금지. 네트워크/DB/GUI/웹 금지. 결과 객체 setter 금지. worker thread CSV 직접 동시 쓰기 금지. `JavaJarSolution`이 신뢰 불가 코드 안전 격리 주장 금지. SecurityManager 격리 금지. `URLClassLoader`/`Class<?>` 인스턴스 캐시 금지. `ExecutionResult.stdout/stderr` null 금지. 자식 JVM 인코딩을 `-Dfile.encoding`만으로 해결했다 가정 금지. timeout 후 reader future 무제한 대기 금지. **(G2) 정밀 시간을 정수 ms로 truncate해 0으로 만들기 금지.**

---

## 15. 최종 산출물 기준
`src/algobench` 전 클래스(+`BenchmarkSummaryPrinter`) 존재 / 문제 정상 2+음성 1 / Java 3+Python 2 / `.\build.ps1`·`.\run-demo.ps1` 성공 / 콘솔만으로 풀이별 성공·실패 케이스·시간·에러 파악 + **풀이 간 비교/순위 요약 제공(G1)** / `reports/result.csv` UTF-8·run 반복 시 중복 없음·시간 소수 ms(G2) / README에 in-JVM 한계·UTF-8(stdout.encoding) 명시 / **`CLAUDE.md`가 `plan_claude3.md`를 최신 기준으로 안내(G5)** / `ai_rec.md` 누적 기록.

---

## 16. 수렴 메모 및 권고

`plan_codex3.md`로 아키텍처·정책은 사실상 동결됐다. 본 문서의 G1~G5는 결함 제거가 아니라 **벤치마킹 본질(풀이 비교 리포트) 보강 + 정밀도/순서/종료 micro-refinement**다.

**권고: 추가 평가 라운드는 수확 체감 구간. 다음 메시지는 `plan_claude3.md` 기준 실제 구현 착수를 권한다.** 더 평가가 필요하다면 "문서 검토"가 아니라 "구현된 코드의 동작 검증"으로 단계를 바꾸는 것이 효과적이다.
