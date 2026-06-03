# AlgoBench 구현 계획

## Context (왜)

설계도 PDF(`202311516 권창민 프로젝트 설계도.pdf`)는 **AlgoBench** — 로컬 알고리즘 벤치마킹 프로그램의 객체지향 설계도다. 외부 온라인 저지(BOJ 등)에 의존하지 않고, 로컬에서 직접 만든 문제 세트에 여러 풀이 코드를 동일 조건으로 실행해 **정답 여부 + 실행 시간**을 비교하는 학습용 도구.

현재 작업 디렉토리는 PDF 1개만 있는 빈 그린필드. 이 계획은 설계도의 클래스 다이어그램(§5.1~5.6)을 **순수 Java SE**로 실제 동작하는 프로그램으로 구현한다.

### 사용자 확정 결정사항
1. **빌드/실행**: 순수 `javac`/`java`. 런타임 외부 의존성 0, 테스트 프레임워크 없음.
2. **Java 풀이 실행 계약**: 제출 코드는 `public static void main`만 있으면 됨. `URLClassLoader`로 `.class`/`.jar` 로드 → `System.in`/`System.out` 임시 교체 → `main` 리플렉션 호출 → stdout 캡처. (외부 프로세스 모델과 동일한 stdin/stdout 인터페이스)
3. **문제 파일 포맷**: 구분자 블록 (key:value 헤더 + `###` 케이스 구분 + `INPUT:`/`EXPECTED:` 블록).
4. **데모 산출물**: 샘플 문제 파일 + Java 샘플 풀이 + Python 샘플 풀이. 메모리 제한은 메타데이터로만 보관(강제 X).

### 추가 산출물 (사용자 요청)
- `plan.md` (작업 디렉토리): 이 계획의 복사본.
- `ai_rec.md` (작업 디렉토리): 사용자 프롬프트/대화/내 행동을 시간순 누적 기록. 구현 시작 시점부터 작성·갱신.

---

## 디렉토리 구조

```
자프 과제/
├─ plan.md                         # 이 계획 복사본
├─ ai_rec.md                       # 대화/행동 기록 (누적)
├─ README.md                       # 빌드/실행법
├─ build.ps1                       # 컴파일 + 실행 헬퍼 (Windows PowerShell)
├─ src/algobench/
│  ├─ Main.java                    # CLI 진입점 / 데모 러너
│  ├─ domain/
│  │  ├─ Problem.java
│  │  └─ TestCase.java
│  ├─ loader/
│  │  └─ ProblemLoader.java        # 구분자 블록 포맷 파서
│  ├─ solution/
│  │  ├─ Solution.java             # «interface»
│  │  ├─ ExecutionResult.java      # «Data Class»
│  │  ├─ JavaJarSolution.java
│  │  └─ ExternalProcessSolution.java
│  ├─ compare/
│  │  ├─ OutputComparator.java     # «interface»
│  │  ├─ ExactOutputComparator.java
│  │  └─ WhitespaceNormalizingComparator.java  # 확장성(NFR-03) 시연용
│  ├─ engine/
│  │  ├─ JudgeEngine.java
│  │  └─ GradingTask.java          # implements Callable<BenchmarkResult>
│  └─ result/
│     ├─ BenchmarkResult.java      # «Data Class»
│     ├─ TestCaseResult.java       # «Data Class»
│     ├─ ResultLogger.java         # «interface»
│     ├─ ConsoleResultLogger.java
│     ├─ CsvResultLogger.java
│     ├─ ResultFormatter.java      # «interface»
│     └─ CsvResultFormatter.java
├─ problems/                       # 샘플 문제 파일
│  ├─ a_plus_b.txt
│  └─ max_of_three.txt
├─ solutions/
│  ├─ java/                        # 샘플 Java 풀이 (.java → 사전 컴파일)
│  │  ├─ CorrectSolution.java      # 정답
│  │  ├─ WrongSolution.java        # 오답
│  │  └─ TimeoutSolution.java      # 무한루프 → 타임아웃 시연
│  └─ python/
│     └─ correct_solution.py       # 외부 프로세스 정답
├─ out/                            # 엔진 컴파일 산출물 (.class)
├─ out_solutions/                  # 샘플 Java 풀이 컴파일 산출물
└─ reports/                        # CSV 결과 출력
```

빌드/실행 (빌드 도구 없음):
```powershell
# 1) 엔진 컴파일
javac -encoding UTF-8 -d out (Get-ChildItem -Recurse src -Filter *.java).FullName
# 2) 샘플 Java 풀이 컴파일 (URLClassLoader가 로드할 .class 생성)
javac -encoding UTF-8 -d out_solutions (Get-ChildItem -Recurse solutions/java -Filter *.java).FullName
# 3) 실행
java -cp out algobench.Main problems/a_plus_b.txt `
     out_solutions/CorrectSolution.class `
     out_solutions/WrongSolution.class `
     out_solutions/TimeoutSolution.class `
     "python solutions/python/correct_solution.py"
```
`build.ps1`이 위 3단계를 묶는다.

---

## 문제 파일 포맷 (`ProblemLoader`가 파싱)

```
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
- 첫 블록(첫 `###` 이전): `KEY: VALUE` 라인 → `TITLE`, `TIME_LIMIT_MS`, `MEMORY_LIMIT_MB`.
- `###`로 케이스 구분. 각 케이스는 `INPUT:` 라인 이후 ~ `EXPECTED:` 라인 이전이 입력(여러 줄 허용), `EXPECTED:` 이후가 기대 출력(여러 줄 허용).
- 입력/출력 본문은 trailing 개행만 정규화하고 내부 줄바꿈 보존.
- 형식 오류 시 명확한 메시지로 `IOException`/`IllegalArgumentException`.

---

## 클래스 상세 (설계도 §5 충실 반영)

### domain
- **`TestCase`** (불변): `int index`, `String input`, `String expectedOutput`.
  - `boolean matches(String actualOutput, OutputComparator comparator)` → `comparator.matches(expectedOutput, actualOutput)`.
- **`Problem`** (불변): `String title`, `Duration timeLimit`, `int memoryLimit`(MB, 메타데이터만), `List<TestCase> testCases`.
  - `List<TestCase> getTestCases()`, `getTitle()`, `getTimeLimit()`.

### loader
- **`ProblemLoader`**: `Problem loadProblem(String filePath)` — 위 포맷 파서. `java.nio.file.Files` 사용.

### solution
- **`Solution`** «interface»: `String getName()`, `ExecutionResult execute(String input)`.
  - 계약: stdin으로 `input` 주입, stdout/stderr/exitCode/실행시간을 `ExecutionResult`로 반환. 타임아웃은 호출측(GradingTask)이 별도 전달하거나 생성자 주입 — 아래 설계 노트 참고.
- **`ExecutionResult`** «Data Class» (불변): `String stdout`, `String stderr`, `int exitCode`, `Duration executionTime`. `boolean isSuccess()` (exitCode==0 && stderr 비었거나 무시), 추가로 `boolean timedOut` 플래그.
- **`JavaJarSolution`**: `URLClassLoader classLoader`, `Class<?> solutionClass`(또는 클래스명/경로). 생성자에서 `.class`/`.jar` 경로 받아 `URLClassLoader` 구성, 대상 클래스 로드. `execute`는 `main(String[])` 리플렉션 호출.
- **`ExternalProcessSolution`**: `String command`(예: `"python sol.py"`, `"./a.out"`). `execute`는 `ProcessBuilder`로 프로세스 실행, stdin 주입, stdout/stderr 캡처, `waitFor(timeout)`.

### compare
- **`OutputComparator`** «interface»: `boolean matches(String expected, String actual)`.
- **`ExactOutputComparator`**: 양끝 공백/개행 정규화 후 정확 비교(라인별 trailing 공백 제거 + 마지막 개행 무시).
- **`WhitespaceNormalizingComparator`**: 토큰 단위 비교(연속 공백 1개로 축약) — NFR-03 "기존 코드 수정 없이 채점 기준 추가" 시연.

### engine
- **`GradingTask`** `implements Callable<BenchmarkResult>` (설계도 «interface» CallableResult = `Callable<BenchmarkResult>`):
  - 필드: `Problem problem`, `Solution solution`, `OutputComparator comparator`.
  - `call()`: problem의 각 `TestCase`에 대해 `solution.execute(input)` 호출 → `comparator`로 판정 → `TestCaseResult` 생성 → 모아서 `BenchmarkResult` 반환. 타임아웃/예외를 케이스 단위로 잡아 `errorMessage`에 기록(NFR-04: 한 풀이의 실패가 전체 중단 X).
- **`JudgeEngine`**:
  - 필드: `ExecutorService threadPool`, `ResultLogger logger`(DI).
  - `JudgeEngine(ResultLogger logger)` — 내부에서 `Executors.newFixedThreadPool` 생성.
  - `List<Future<BenchmarkResult>> evaluateAllAsync(Problem, List<Solution>, OutputComparator)`.
  - `List<BenchmarkResult> evaluateAll(...)` — async 제출 후 결과 수집, **단일 스레드에서 순차 `logger.log(result)`** (NFR-05 스레드 안전: 로깅은 메인 스레드 직렬화).
  - `shutdown()`.

### result
- **`TestCaseResult`** «Data Class» (불변): `int testCaseIndex`, `boolean passed`, `String expectedOutput`, `String actualOutput`, `Duration executionTime`, `String errorMessage`.
- **`BenchmarkResult`** «Data Class» (불변): `String solutionName`, `boolean allPassed`, `Duration totalExecutionTime`, `List<TestCaseResult> caseResults`. `boolean isAllPassed()`.
- **`ResultLogger`** «interface»: `void log(BenchmarkResult result)`.
- **`ConsoleResultLogger`**: 사람이 읽기 좋은 표 형태 콘솔 출력(풀이명, 통과 N/M, 총 시간, 실패 케이스/에러 요약). NFR-06.
- **`CsvResultLogger`**: `String csvFilePath`, `ResultFormatter formatter`(DI). `log`에서 `formatter.format(result)`를 파일에 append. 다중 호출 안전 위해 `synchronized`.
- **`ResultFormatter`** «interface»: `String format(BenchmarkResult result)`.
- **`CsvResultFormatter`**: CSV 행 생성. 컬럼: `solutionName,allPassed,passedCount,totalCount,totalExecutionTimeMs,failedCaseIndexes,firstErrorMessage`. 헤더는 `CsvResultLogger`가 파일 생성 시 1회 기록.

### Main (CLI / 데모 러너)
- 인자: `<problemFile> <solution1> <solution2> ...`.
- 각 solution 인자 타입 판별: 확장자 `.class`/`.jar` → `JavaJarSolution`, 그 외(`python ...`, 실행파일 경로 등) → `ExternalProcessSolution`.
- `ProblemLoader`로 문제 로드 → `JudgeEngine`(콘솔+CSV 로거 조합) 생성 → `evaluateAll` → 콘솔 출력 + `reports/result.csv` 생성 → `shutdown`.

---

## 설계 노트 (구현 시 주의)

1. **`System.in`/`System.out` 전역 충돌**: `JavaJarSolution`은 `System.setIn/setOut/setErr`로 전역 스트림을 교체한다. 전역이므로 여러 Java 풀이를 동시에 실행하면 출력이 섞인다(NFR-05 위반). → `JavaJarSolution.execute`의 **스트림 교체 구간을 `static` 락으로 감싸 직렬화**. 외부 프로세스 풀이는 자체 OS 프로세스라 완전 병렬. 병렬 이득은 외부 프로세스 + I/O 대기 구간에서 확보. (트레이드오프 README에 명시)
2. **타임아웃**:
   - `ExternalProcessSolution`: `process.waitFor(timeoutMs, MILLISECONDS)` → 초과 시 `destroyForcibly()`. 확실히 종료.
   - `JavaJarSolution`: 별도 단일 데몬 스레드 + `Future.get(timeout)` best-effort. 진짜 무한루프는 같은 JVM이라 강제 종료 불가 — 타임아웃 표시는 하되 스레드는 데몬으로 두어 메인 종료 막지 않음. (한계 README에 명시 — NFR-04는 "전체 프로그램 중단 방지"이므로 충족)
3. **불변 데이터 클래스**: 모든 «Data Class»/도메인은 생성자 주입 + getter만, 컬렉션은 방어적 복사 / `List.copyOf`. 스레드 안전성 확보.
4. **예외 격리**: `GradingTask.call()`은 어떤 케이스에서 예외가 나도 잡아서 `TestCaseResult.errorMessage`로 기록하고 계속 진행. 풀이 1개 크래시가 벤치마크 전체를 깨지 않음.
5. **확장성 시연(NFR-03)**: `WhitespaceNormalizingComparator` 추가, `ConsoleResultLogger`/`CsvResultLogger` 2종 — 엔진 수정 없이 비교 정책·출력 형식 교체 가능함을 보임.

---

## 구현 순서

1. 프로젝트 골격 + `plan.md`/`ai_rec.md` 생성 + `README.md` 초안.
2. `domain` (`TestCase`, `Problem`) — 불변 객체.
3. `loader` (`ProblemLoader`) + 샘플 문제 파일 2개.
4. `compare` (`OutputComparator`, `ExactOutputComparator`, `WhitespaceNormalizingComparator`).
5. `solution` (`Solution`, `ExecutionResult`, `ExternalProcessSolution`, `JavaJarSolution`).
6. `result` (`TestCaseResult`, `BenchmarkResult`, 로거/포매터 4종).
7. `engine` (`GradingTask`, `JudgeEngine`).
8. `Main` CLI + 샘플 풀이(Java 3개, Python 1개) + `build.ps1`.
9. 빌드 + end-to-end 실행 검증.

---

## 검증 (Verification)

JUnit 없음 → **Main 데모 러너 end-to-end 실행**으로 검증:

1. `build.ps1` 실행: 엔진 + 샘플 Java 풀이 컴파일 성공(에러 0).
2. `java -cp out algobench.Main problems/a_plus_b.txt out_solutions/CorrectSolution.class out_solutions/WrongSolution.class out_solutions/TimeoutSolution.class "python solutions/python/correct_solution.py"` 실행.
3. 콘솔 출력 확인:
   - `CorrectSolution` → 전체 통과(allPassed=true).
   - `WrongSolution` → 특정 테스트케이스 실패 표시 + 기대/실제 출력 비교.
   - `TimeoutSolution` → 타임아웃 표시, 프로그램 멈추지 않고 계속 진행(NFR-04).
   - Python 풀이 → 외부 프로세스 경로로 정답 처리(다형성 검증).
4. `reports/result.csv` 생성 확인: 헤더 + 풀이별 행, 시간(ms)·통과수·실패케이스·에러 기록.
5. (선택) 비교기를 `WhitespaceNormalizingComparator`로 바꿔 엔진 수정 없이 채점 기준 교체되는지 확인.

모든 검증 결과와 실행 명령/출력은 `ai_rec.md`에 기록.
