# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 현재 상태

**구현 완료 (M0~M7).** `src/algobench` 전 패키지(domain/loader/compare/solution/result/engine + Main) 구현, 샘플 문제 2 + Java 풀이 3 + Python 풀이 1, `build.ps1` end-to-end 통과. 설계의 단일 진실 공급원(source of truth)은 **`plan.md`** — 클래스 시그니처·디렉토리 구조·설계 결정. 최신 수렴 보정(G1~G4: 비교 요약·소수 ms·스왑→로드 순서·shutdown 안전망)은 **`plan_claude3.md`** 참조. 단계 분해는 `MILESTONES.md`. 원본 설계도는 `202311516 권창민 프로젝트 설계도.pdf`.

> 설계와 다른 결정 1건: `Solution.execute`는 `plan_claude3.md`를 따라 `execute(String input, Duration timeout)` (plan.md 원안 `execute(String input)` + 타임아웃은 호출측 전달).

## 프로젝트 개요

**AlgoBench** — 로컬 알고리즘 벤치마킹 프로그램. 외부 온라인 저지(BOJ 등)에 의존하지 않고, 로컬 문제 세트에 여러 풀이 코드를 동일 조건으로 실행해 **정답 여부 + 실행 시간**을 비교하는 학습용 도구. 순수 Java SE, 단일 머신 stand-alone.

## 핵심 제약 (위반 금지)

- **순수 Java SE만**. 빌드 도구(Maven/Gradle) 없음, 런타임 외부 의존성 0, 테스트 프레임워크 없음. `javac`/`java` 직접 사용.
- 네트워크·DB·웹 UI·계정 관리 전부 범위 밖.
- 검증은 JUnit이 아니라 `Main` 데모 러너 end-to-end 실행으로 한다.

## 빌드 / 실행 (빌드 도구 없음)

```powershell
# 엔진 컴파일
javac -encoding UTF-8 -d out (Get-ChildItem -Recurse src -Filter *.java).FullName
# 샘플 Java 풀이 컴파일 (URLClassLoader가 로드할 .class 생성)
javac -encoding UTF-8 -d out_solutions (Get-ChildItem -Recurse solutions/java -Filter *.java).FullName
# 실행 — 인자: <problemFile> <solution...>
java -cp out algobench.Main problems/a_plus_b.txt out_solutions/CorrectSolution.class "python solutions/python/correct_solution.py"
```

`build.ps1`이 위 3단계를 묶는다 (구현 예정). 단일 "테스트"는 곧 단일 풀이를 단일 문제로 실행하는 것 — 위 명령에서 풀이 인자를 하나만 주면 된다.

## 아키텍처 (책임 분리 + 확장점)

데이터가 흐르는 파이프라인: **로드 → 실행 → 비교 → 결과 수집 → 리포팅**. 각 단계는 독립 패키지이고, 변경 가능성 높은 지점은 인터페이스로 추상화돼 있다 (엔진 수정 없이 구현체 추가 = NFR-03).

- `domain/` — `Problem`, `TestCase` (불변 도메인 객체).
- `loader/` — `ProblemLoader`: 자체 텍스트 포맷 파싱. 포맷 스펙은 `plan.md` 참조 (`KEY: VALUE` 헤더 + `###` 케이스 구분 + `INPUT:`/`EXPECTED:` 블록).
- `solution/` — **`Solution` 인터페이스가 핵심 다형성 지점.** 두 구현체가 동일한 stdin/stdout 계약을 따른다:
  - `JavaJarSolution`: `URLClassLoader`로 `.class`/`.jar` 로드 → 리플렉션으로 `main(String[])` 호출.
  - `ExternalProcessSolution`: `ProcessBuilder`로 C/C++/Python 등 외부 실행.
  - 둘 다 `ExecutionResult`(stdout/stderr/exitCode/시간/timedOut) 반환.
- `compare/` — `OutputComparator` 인터페이스 + `ExactOutputComparator` 등 채점 정책.
- `engine/` — `JudgeEngine`(`ExecutorService` 스레드 풀로 비동기 채점) + `GradingTask`(`implements Callable<BenchmarkResult>`, 한 풀이를 한 문제에 대해 채점하는 단위).
- `result/` — `BenchmarkResult`/`TestCaseResult`(불변 데이터) + `ResultLogger`(Console/CSV) + `ResultFormatter`(CSV) 인터페이스. `JudgeEngine`에 로거를 DI로 주입.

## 구현 시 반드시 지킬 비자명 규칙

1. **`System.in`/`out`은 JVM 전역** — `JavaJarSolution`이 스트림을 교체하므로 Java 풀이를 병렬 실행하면 출력이 섞인다. 스트림 교체 구간은 **`static` 락으로 직렬화**할 것. 병렬성은 외부 프로세스 풀이에서 확보. (자세한 이유: `plan.md` 설계 노트)
2. **타임아웃**: 외부 프로세스는 `waitFor(timeout)` + `destroyForcibly()`로 확실히 종료. Java 풀이는 같은 JVM이라 강제 종료 불가 → 데몬 스레드 + `Future.get(timeout)` best-effort, 메인 종료를 막지 않게 한다.
3. **예외 격리(NFR-04)**: `GradingTask.call()`은 케이스 단위로 예외를 잡아 `TestCaseResult.errorMessage`에 기록하고 계속 진행. 풀이 1개 크래시가 전체 벤치마크를 깨선 안 된다.
4. **불변 + 방어적 복사**: 모든 데이터/도메인 객체는 생성자 주입 + getter, 컬렉션은 `List.copyOf`. 병렬 채점 중 결과가 섞이지 않도록(NFR-05) 공유 가변 상태를 두지 않는다. 로깅은 메인 스레드에서 순차 수행.
5. **메모리 제한**은 메타데이터로만 보관 — 강제하지 않는다.

## `ai_rec.md` 기록 규칙 (프로젝트 약속)

사용자는 모든 프롬프트·대화·AI 행동을 `ai_rec.md`에 시간순 누적 기록하기를 원한다. **작업이 끝날 때마다 `ai_rec.md` 끝에 append**할 것 (자동 hook 아님 — 수동 갱신). 기존 세션 항목은 보존하고 아래에 이어 쓴다.
