# AlgoBench 마일스톤

`plan.md`의 구현 순서(§구현 순서)와 검증(§Verification)을 단계별 마일스톤으로 분해한 문서. 각 마일스톤은 독립적으로 컴파일·검증 가능한 단위로 끊었고, 의존성 순서대로 나열했다. 단일 진실 공급원은 여전히 `plan.md` — 클래스 시그니처/설계 결정이 바뀌면 `plan.md`를 먼저 고친다.

> 진행 규칙: 각 마일스톤 완료 시 **완료 기준(Exit Criteria)** 을 모두 충족해야 다음으로 넘어간다. 모든 작업·명령·출력은 `ai_rec.md`에 시간순 누적 기록한다.

---

## M0 — 프로젝트 골격 (Scaffolding)

**목표:** 디렉토리 구조와 문서 기반을 만든다. 아직 로직 없음.

**작업**
- `plan.md`의 디렉토리 구조대로 폴더 생성: `src/algobench/{domain,loader,solution,compare,engine,result}`, `problems/`, `solutions/{java,python}`, `out/`, `out_solutions/`, `reports/`.
- `README.md` 초안: 빌드/실행법, 병렬성 트레이드오프·타임아웃 한계 명시(설계 노트 1·2).
- `build.ps1` 뼈대: 엔진 컴파일 → 샘플 풀이 컴파일 → 실행 3단계 묶음.
- `ai_rec.md` 작성 시작.

**완료 기준**
- 디렉토리 구조가 `plan.md`와 일치.
- `README.md`·`build.ps1`·`ai_rec.md` 존재.

**의존성:** 없음 (시작점).

---

## M1 — 도메인 + 비교기 (Domain & Comparators)

**목표:** 불변 핵심 데이터 타입과 채점 정책 인터페이스를 만든다. 외부 의존이 가장 적은 잎(leaf) 계층.

> `TestCase.matches(String, OutputComparator)`가 `OutputComparator`를 참조하므로 도메인과 비교기를 같은 마일스톤에서 함께 구현한다.

**작업**
- `compare/OutputComparator.java` «interface»: `boolean matches(String expected, String actual)`.
- `compare/ExactOutputComparator.java`: 양끝 공백/개행 정규화 후 정확 비교.
- `compare/WhitespaceNormalizingComparator.java`: 토큰 단위 비교 (NFR-03 확장성 시연).
- `domain/TestCase.java` (불변): `index`, `input`, `expectedOutput` + `matches(...)`.
- `domain/Problem.java` (불변): `title`, `timeLimit`(Duration), `memoryLimit`(메타데이터), `List<TestCase>` (방어적 복사/`List.copyOf`).

**완료 기준**
- `javac`로 `domain` + `compare` 컴파일 성공(에러 0).
- 모든 클래스 불변: 생성자 주입 + getter, 세터 없음, 컬렉션 방어적 복사.

**의존성:** M0.

---

## M2 — 문제 로더 + 샘플 문제 (Loader & Problem Files)

**목표:** 문제 파일 포맷을 파싱해 `Problem` 객체로 만든다.

**작업**
- `loader/ProblemLoader.java`: `Problem loadProblem(String filePath)` — `KEY: VALUE` 헤더 + `###` 케이스 구분 + `INPUT:`/`EXPECTED:` 블록 파싱(`java.nio.file.Files`). 형식 오류 시 명확한 메시지로 예외.
- 샘플 문제 2개: `problems/a_plus_b.txt`, `problems/max_of_three.txt`.

**완료 기준**
- `loader` 컴파일 성공.
- 샘플 문제 파일이 포맷 스펙(`plan.md` §문제 파일 포맷) 준수.
- (스모크) 임시 `main`으로 `a_plus_b.txt` 로드 → 케이스 수·헤더 값 정상 파싱 확인.

**의존성:** M1.

---

## M3 — 결과 데이터 + 로거/포매터 (Result & Reporting)

**목표:** 채점 결과를 담는 불변 데이터와 출력 계층(콘솔/CSV)을 만든다.

**작업**
- `result/TestCaseResult.java` (불변): `testCaseIndex`, `passed`, `expectedOutput`, `actualOutput`, `executionTime`, `errorMessage`.
- `result/BenchmarkResult.java` (불변): `solutionName`, `allPassed`, `totalExecutionTime`, `List<TestCaseResult>`.
- `result/ResultLogger.java` «interface»: `void log(BenchmarkResult)`.
- `result/ConsoleResultLogger.java`: 사람이 읽는 표 출력(NFR-06).
- `result/ResultFormatter.java` «interface» + `result/CsvResultFormatter.java`: CSV 행 생성.
- `result/CsvResultLogger.java`: `formatter` DI, 파일 append, `synchronized`, 헤더 1회 기록.

**완료 기준**
- `result` 패키지 전체 컴파일 성공.
- 데이터 클래스 불변 + 방어적 복사 준수.

**의존성:** M1 (Duration 등 기본 타입만; 도메인과 독립적이라 M2와 병행 가능).

---

## M4 — 풀이 실행 (Solution Execution)

**목표:** 동일한 stdin/stdout 계약을 따르는 두 실행 모델을 만든다 — 핵심 다형성 지점.

**작업**
- `solution/ExecutionResult.java` «Data Class» (불변): `stdout`, `stderr`, `exitCode`, `executionTime`, `timedOut` + `isSuccess()`.
- `solution/Solution.java` «interface»: `String getName()`, `ExecutionResult execute(String input)`.
- `solution/ExternalProcessSolution.java`: `ProcessBuilder` 실행, stdin 주입, stdout/stderr 캡처, `waitFor(timeout)` + `destroyForcibly()`.
- `solution/JavaJarSolution.java`: `URLClassLoader`로 `.class`/`.jar` 로드 → `main(String[])` 리플렉션 호출. **`System.in/out/err` 교체 구간을 `static` 락으로 직렬화**(설계 노트 1). 타임아웃은 데몬 스레드 + `Future.get(timeout)` best-effort(설계 노트 2).

**완료 기준**
- `solution` 패키지 컴파일 성공.
- `ExternalProcessSolution`: 타임아웃 초과 프로세스 `destroyForcibly`로 확실히 종료.
- `JavaJarSolution`: 스트림 교체가 `static` 락으로 보호됨(병렬 출력 섞임 방지).

**의존성:** M1 (도메인 타입 참조 가능).

---

## M5 — 엔진 (Judge Engine)

**목표:** 풀이×문제를 비동기 채점하고 결과를 메인 스레드에서 순차 로깅한다.

**작업**
- `engine/GradingTask.java` `implements Callable<BenchmarkResult>`: 필드 `problem`/`solution`/`comparator`. `call()`에서 각 `TestCase` 실행 → 판정 → `TestCaseResult` 수집 → `BenchmarkResult`. **케이스 단위 예외 격리**로 `errorMessage` 기록 후 계속 진행(NFR-04).
- `engine/JudgeEngine.java`: `ExecutorService`(내부 `newFixedThreadPool`) + `ResultLogger` DI. `evaluateAllAsync(...)`, `evaluateAll(...)`(제출 후 **단일 스레드 순차 `logger.log`** — NFR-05), `shutdown()`.

**완료 기준**
- `engine` 패키지 컴파일 성공.
- 한 풀이의 예외가 전체 벤치마크를 중단시키지 않음(예외 격리).
- 로깅이 메인 스레드에서 직렬화됨(결과 섞임 없음).

**의존성:** M1, M3, M4.

---

## M6 — Main + 샘플 풀이 + 빌드 마무리 (CLI & Demo)

**목표:** CLI 진입점을 완성하고 데모용 샘플 풀이를 만든다.

**작업**
- `Main.java`: 인자 `<problemFile> <solution...>`. 확장자로 타입 판별(`.class`/`.jar` → `JavaJarSolution`, 그 외 → `ExternalProcessSolution`). 문제 로드 → `JudgeEngine`(콘솔+CSV 로거) → `evaluateAll` → 콘솔 출력 + `reports/result.csv` → `shutdown`.
- 샘플 Java 풀이: `CorrectSolution.java`(정답), `WrongSolution.java`(오답), `TimeoutSolution.java`(무한루프).
- 샘플 Python 풀이: `solutions/python/correct_solution.py`.
- `build.ps1` 3단계 완성.

**완료 기준**
- 전체 `src` + 샘플 Java 풀이 컴파일 성공(에러 0).
- `Main`이 4개 풀이 인자를 받아 실행 가능(다음 마일스톤에서 동작 검증).

**의존성:** M5.

---

## M7 — 빌드 + End-to-End 검증 (Verification)

**목표:** JUnit 없이 데모 러너 end-to-end 실행으로 전체를 검증한다(`plan.md` §Verification).

**작업 / 완료 기준**
1. `build.ps1` 실행 → 엔진 + 샘플 Java 풀이 컴파일 성공(에러 0).
2. 데모 명령 실행:
   ```powershell
   java -cp out algobench.Main problems/a_plus_b.txt `
        out_solutions/CorrectSolution.class `
        out_solutions/WrongSolution.class `
        out_solutions/TimeoutSolution.class `
        "python solutions/python/correct_solution.py"
   ```
3. 콘솔 출력 확인:
   - `CorrectSolution` → 전체 통과(allPassed=true).
   - `WrongSolution` → 특정 케이스 실패 + 기대/실제 비교.
   - `TimeoutSolution` → 타임아웃 표시, 프로그램 계속 진행(NFR-04).
   - Python 풀이 → 외부 프로세스 경로로 정답 처리(다형성 검증).
4. `reports/result.csv` 생성 확인: 헤더 + 풀이별 행(시간 ms·통과수·실패케이스·에러).
5. (선택) 비교기를 `WhitespaceNormalizingComparator`로 교체 → 엔진 수정 없이 채점 기준 교체됨 확인(NFR-03).

**검증 결과·실행 명령·출력은 모두 `ai_rec.md`에 기록.**

**의존성:** M6.

---

## 의존성 요약

```
M0 ──> M1 ──┬──> M2
            ├──> M3 ──┐
            └──> M4 ──┼──> M5 ──> M6 ──> M7
                      │
            (M3, M4는 M1 위에서 병행 가능)
```

| 마일스톤 | 산출물 | 핵심 검증 |
|---|---|---|
| M0 | 디렉토리·README·build.ps1·ai_rec.md | 구조 일치 |
| M1 | domain + compare | 컴파일·불변성 |
| M2 | loader + 샘플 문제 | 파싱 스모크 |
| M3 | result(데이터+로거+포매터) | 컴파일·불변성 |
| M4 | solution(인터페이스+2구현) | 타임아웃·스트림 락 |
| M5 | engine(GradingTask+JudgeEngine) | 예외 격리·순차 로깅 |
| M6 | Main + 샘플 풀이 + build.ps1 | 전체 컴파일 |
| M7 | E2E 실행 | 4풀이 시나리오 + CSV |
