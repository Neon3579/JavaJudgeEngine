# AlgoBench

로컬 알고리즘 벤치마킹 프로그램. 외부 온라인 저지(BOJ 등)에 의존하지 않고, 로컬 문제 세트에 여러 풀이 코드를 **동일 조건**으로 실행해 **정답 여부 + 실행 시간**을 비교하는 학습용 stand-alone 도구.

- 학번/이름: **202311516 권창민**
- 단일 진실 공급원: [`plan.md`](plan.md) (클래스 시그니처·설계 결정). 단계별 실행 계획: [`MILESTONES.md`](MILESTONES.md). 시각화: [`algobench_visual.html`](algobench_visual.html).

> 현재 상태: **M0(프로젝트 골격) 완료.** 디렉토리 트리·빌드 스크립트·문서 준비됨. 소스 구현은 M1부터.

---

## 핵심 제약 (Pure Java SE)

- **순수 Java SE만.** 빌드 도구(Maven/Gradle) 없음, 런타임 외부 의존성 0, 테스트 프레임워크 없음. `javac`/`java` 직접 사용.
- 네트워크·DB·웹/GUI·계정 관리는 전부 범위 밖.
- 검증은 JUnit이 아니라 `Main` 데모 러너 **end-to-end 실행**으로 한다.
- 메모리 제한은 메타데이터로만 보관 — 강제하지 않는다.

---

## 빌드 / 실행

빌드 도구 없음. `build.ps1`이 컴파일 + 데모 실행을 묶는다.

```powershell
# 전체 (컴파일 + 데모 실행)
powershell -ExecutionPolicy Bypass -File .\build.ps1

# 컴파일만
.\build.ps1 -NoRun
```

PowerShell ExecutionPolicy로 스크립트가 차단되면 위처럼 `-ExecutionPolicy Bypass`로 우회한다.

### 수동 빌드 (스크립트 없이)

```powershell
# 1) 엔진 컴파일
javac -encoding UTF-8 -d out (Get-ChildItem -Recurse src -Filter *.java).FullName
# 2) 샘플 Java 풀이 컴파일 (URLClassLoader가 로드할 .class 생성)
javac -encoding UTF-8 -d out_solutions (Get-ChildItem -Recurse solutions/java -Filter *.java).FullName
# 3) 실행 — 인자: <problemFile> <solution...>
java -cp out algobench.Main problems/a_plus_b.txt `
     out_solutions/CorrectSolution.class `
     out_solutions/WrongSolution.class `
     out_solutions/TimeoutSolution.class `
     "python solutions/python/correct_solution.py"
```

단일 "테스트" = 단일 풀이를 단일 문제로 실행하는 것. 위 명령에서 풀이 인자를 하나만 주면 된다.

---

## 디렉토리 구조

```
자프 과제/
├─ plan.md / MILESTONES.md / algobench_visual.html   # 설계·계획·시각화
├─ ai_rec.md                                          # 대화/행동 기록 (누적)
├─ README.md  build.ps1  .gitignore
├─ src/algobench/
│  ├─ Main.java               # CLI 진입점 / 데모 러너
│  ├─ domain/   { Problem, TestCase }                 # 불변 도메인
│  ├─ loader/   { ProblemLoader }                     # 텍스트 포맷 파서
│  ├─ solution/ { Solution«if», ExecutionResult,
│  │             JavaJarSolution, ExternalProcessSolution }
│  ├─ compare/  { OutputComparator«if», ExactOutputComparator,
│  │             WhitespaceNormalizingComparator }
│  ├─ engine/   { JudgeEngine, GradingTask }
│  └─ result/   { BenchmarkResult, TestCaseResult, ResultLogger«if»,
│                ConsoleResultLogger, CsvResultLogger,
│                ResultFormatter«if», CsvResultFormatter,
│                BenchmarkSummaryPrinter }            # G1: 풀이 간 비교 요약
├─ problems/        # 샘플 문제 파일
├─ solutions/java/  # 샘플 Java 풀이 (.java → 사전 컴파일)
├─ solutions/python/# 샘플 Python 풀이 (외부 프로세스)
├─ out/             # 엔진 컴파일 산출물 (.class) — gitignored
├─ out_solutions/   # 샘플 Java 풀이 컴파일 산출물 — gitignored
└─ reports/         # CSV 결과 출력 — gitignored
```

---

## 문제 파일 포맷

`KEY: VALUE` 헤더 + `###` 케이스 구분 + `INPUT:`/`EXPECTED:` 블록.

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

- 첫 `###` 이전 = 헤더(`KEY: VALUE`). `###`로 케이스 구분.
- `INPUT:` 이후 ~ `EXPECTED:` 이전 = 입력, `EXPECTED:` 이후 = 기대 출력 (여러 줄 허용).
- 입력/출력 본문은 trailing 개행만 정규화, 내부 줄바꿈 보존.

---

## 풀이 제출 규칙

| 인자 형태 | 실행 모델 | 클래스 |
|---|---|---|
| `*.class` / `*.jar` | 같은 JVM, `URLClassLoader` + 리플렉션 `main(String[])` | `JavaJarSolution` |
| 그 외 (`python sol.py`, 실행파일 경로 등) | 외부 OS 프로세스 (`ProcessBuilder`) | `ExternalProcessSolution` |

두 모델 모두 동일한 **stdin → stdout** 계약을 따른다. Java 풀이 제출 코드는 `public static void main(String[])`만 있으면 된다.

---

## 설계 트레이드오프 / 한계 (반드시 인지)

- **전역 스트림 충돌**: `System.in/out`은 JVM 전역. `JavaJarSolution`이 스트림을 교체하므로 Java 풀이를 병렬 실행하면 출력이 섞인다. → 스트림 교체 구간을 `static` 락으로 **직렬화**한다. 병렬 이득은 외부 프로세스 풀이 + I/O 대기 구간에서 확보.
- **타임아웃**:
  - 외부 프로세스: `waitFor(timeout)` + `destroyForcibly()`로 확실히 종료.
  - Java 풀이: 같은 JVM이라 **강제 종료 불가**. 데몬 스레드 + `Future.get(timeout)` best-effort. 진짜 무한루프는 데몬으로 잔존하며 타임아웃 표시만 한다(메인 종료는 막지 않음 — NFR-04 충족). 신뢰 불가 Java 코드는 외부 JVM(`ExternalProcessSolution`)으로 실행 권장.
- **인코딩**: 파일·문제 포맷·CSV·프로세스 stdin/stdout/stderr 모두 UTF-8. Python 풀이는 `PYTHONIOENCODING=utf-8`로 한글 깨짐 방지.
- **시간 측정 한계**: 단일 측정은 JIT 워밍업·OS 스케줄링 영향을 받는다. in-JVM과 외부 프로세스는 runner overhead가 달라 모드 간 절대 비교는 주의. 반복·통계는 범위 외.
- **결과 출력**: 콘솔(`ConsoleResultLogger`) + CSV(`reports/result.csv`) + **풀이 간 비교/순위 요약**(`BenchmarkSummaryPrinter` — 통과 우선 → 총 시간 오름차순, 최속 정답 강조).

---

## 기록 규칙

모든 프롬프트·대화·AI 행동·검증 결과는 [`ai_rec.md`](ai_rec.md)에 시간순 누적 기록한다.
