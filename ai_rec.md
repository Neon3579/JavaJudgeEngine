# AI 작업 기록 (ai_rec.md)

> AlgoBench 프로젝트 진행 중 사용자와의 모든 프롬프트·대화·AI 행동을 시간순으로 누적 기록한다.

---

## 세션 1 — 2026-06-03 · 설계도 분석 및 구현 계획 수립 (Opus 4.8)

### 사용자 프롬프트 #1
> pdf를 보고 상세한 implements plan을 세우고, plan.md 로 저장할꺼야. 그리고 내가 너와 나눈 모든 프롬프트와 대화, 행동은 ai_rec.md 에 저장 될거야. 한번 pdf를 보고 세부적인 내용에 궁금증이 있으면 나한테 물어봐.

### AI 행동
1. 작업 디렉토리에서 PDF 탐색 → `202311516 권창민 프로젝트 설계도.pdf` 발견.
2. PDF 12쪽 전체 판독. 핵심 파악:
   - **AlgoBench**: 외부 온라인 저지(BOJ 등) 비의존, 로컬에서 여러 풀이를 동일 조건 실행 → 정답 여부 + 실행 시간 비교하는 학습용 벤치마킹 도구.
   - 기능 요구(FR-01~07): 문제 파일 로드, 테스트케이스 관리, 풀이 코드 실행, 정답 판정, 실행 시간 측정, 병렬 채점, 결과 리포팅.
   - 비기능 요구(NFR-01~06): 독립 실행, Pure Java, 확장성, 안정성, 스레드 안전성, 가독성.
   - 클래스 구조(§5): `ProblemLoader`/`Problem`/`TestCase`, `Solution`(인터페이스)→`JavaJarSolution`/`ExternalProcessSolution`+`ExecutionResult`, `JudgeEngine`+`GradingTask`(Callable), `OutputComparator`(인터페이스)→`ExactOutputComparator`, `BenchmarkResult`/`TestCaseResult`, `ResultLogger`/`ResultFormatter`(인터페이스)+CSV/Console 구현체.
3. 작업 디렉토리 = PDF 1개만 있는 빈 그린필드 확인.
4. 설계도가 비워둔 결정사항 4개를 사용자에게 질문(AskUserQuestion).

### 사용자 응답 #1 (질문 답변)
- **빌드/실행 방식**: 순수 `javac`/`java` (빌드 도구 0, 테스트 프레임워크 0).
- **Java 풀이 실행 계약**: `main()` + `System.in`/`System.out` 교체 + `URLClassLoader`. (제출 코드는 main만 있으면 됨)
- **문제 파일 포맷**: 구분자 블록 (`KEY: VALUE` 헤더 + `###` 케이스 구분 + `INPUT:`/`EXPECTED:`).
- **데모 산출물**: 샘플 문제 파일 + Java 샘플 풀이 + Python 샘플 풀이. (JUnit 미선택) 메모리 제한은 메타데이터로만 보관(강제 X).

### AI 행동
5. 위 결정 반영해 상세 구현 계획 작성 → 플랜 파일에 저장.
6. ExitPlanMode로 승인 요청.

### 사용자 응답 #2
> (ExitPlanMode 거부) 우선 .md파일만 생성하고 ai_rec.md 에 이 대화 기[록]…

### AI 행동
7. 코드 구현 보류. `plan.md`(작업 디렉토리 사본) + `ai_rec.md`(이 기록) 두 파일만 생성.

### 현재 상태
- 구현 계획 확정: `plan.md` 참조.
- 코드 구현은 사용자 추가 지시 대기 중.

---

## 세션 1 (계속) — 2026-06-03 · CLAUDE.md 생성 (Opus 4.8)

### 사용자 프롬프트 #2
> /init — 코드베이스 분석 후 CLAUDE.md 생성.

### AI 행동
1. 디렉토리 확인: PDF + `plan.md` + `ai_rec.md`만 존재, 소스 코드 미구현(설계/계획 단계).
2. `CLAUDE.md` 작성 — `plan.md` 기반. 포함: 현재 상태(plan.md가 source of truth), 프로젝트 개요, 핵심 제약(순수 Java SE), 빌드/실행 명령, 패키지별 아키텍처, 비자명 규칙 5개(전역 스트림 직렬화/타임아웃/예외 격리/불변성/메모리 메타데이터), `ai_rec.md` 기록 규칙.

### 현재 상태
- 문서 3종 완비: `plan.md`(구현 계획), `CLAUDE.md`(작업 가이드), `ai_rec.md`(기록).
- 코드 구현은 사용자 추가 지시 대기 중.

<!-- 이후 작업은 아래에 계속 누적 -->

---

## 세션 2 — 2026-06-03 · plan_codex.md 평가/보완 계획 생성 (GPT5.5)

### 사용자 프롬프트 #3
> .pdf 보고서와 plan.md를 평가하고 수정 및 보완하여 plan_codex.md 를 만들어줘.

### AI 행동
1. 작업 디렉토리 확인: `202311516 권창민 프로젝트 설계도.pdf`, `plan.md`, `ai_rec.md`, `CLAUDE.md` 존재 확인.
2. `CLAUDE.md`와 `plan.md`를 읽어 현재 프로젝트 제약과 기존 구현 계획을 파악.
3. `pdftotext`로 PDF 원문을 추출해 요구사항(FR-01~FR-07, NFR-01~NFR-06), 핵심 기능, 클래스 다이어그램 내용을 확인.
4. 기존 `plan.md`와 PDF를 대조해 보완 포인트 정리:
   - `Solution.execute`에 timeout 전달 방식 명시 필요.
   - Java same-JVM 실행의 hard timeout 한계 문서화 필요.
   - `.class`/`.jar` 로딩 규칙 명시 필요.
   - PDF에 언급된 공백 무시/대소문자 무시 comparator 확장 반영 필요.
   - 콘솔+CSV 동시 로깅 구조와 CSV escaping 규칙 명시 필요.
5. Plan Mode에서 `plan_codex.md` 작성 계획을 제안.

### 사용자 프롬프트 #4
> plan_codex.md 생성

### AI 행동
6. `plan_codex.md` 생성. 포함 내용:
   - PDF 요구사항 매핑 평가.
   - 기존 `plan.md` 대비 보완 사항.
   - 확정 제약, 디렉토리 구조, 문제 파일 포맷.
   - 클래스별 구현 명세.
   - Java `URLClassLoader` 실행 규칙과 timeout 한계.
   - 외부 프로세스 실행, 비교기, 결과 로깅, CSV escaping 규칙.
   - 구현 순서, 검증 계획, README 필수 기재 내용, 금지 사항, 최종 산출물 기준.

---

## 세션 3 — 2026-06-03 · plan_claude.md 평가 및 plan_codex2.md 생성 (GPT5.5)

### 사용자 프롬프트 #5
> plan_claude.md를 평가하고 수정 및 보완하여 plan_codex2.md 를 만들어줘.

### AI 행동
1. 작업 디렉토리 확인: `plan_claude.md`, `plan_codex.md`, `plan.md`, `CLAUDE.md`, `ai_rec.md` 존재 확인.
2. `plan_claude.md`와 `plan_codex.md`를 비교해 `plan_claude.md`의 유지할 결정과 수정할 결정을 정리.
3. 주요 평가:
   - 같은 JVM Java 실행의 `System.exit()` 및 interrupt 무시 timeout 한계 지적은 타당.
   - 외부 프로세스 hard timeout과 UTF-8 인코딩 보완은 유지할 가치가 있음.
   - `build.ps1`과 `run-demo.ps1` 분리는 검증 단계를 더 명확하게 함.
   - 다만 자식 JVM 인코딩 인자 자동 삽입, composite logger 예외 정책, 문서 말미 코드펜스 등은 정리 필요.
4. `plan_codex2.md` 생성. 포함 내용:
   - `plan_claude.md` 평가 및 수정 사항.
   - PDF 요구사항 최종 매핑.
   - 최종 제약, 디렉토리 구조, 문제 파일 포맷.
   - 클래스별 구현 명세.
   - `JavaJarSolution`의 semi-trusted 실행 모델과 한계.
   - `ExternalProcessSolution` hard timeout 및 UTF-8 처리.
   - PowerShell 스크립트 분리, 검증 계획, README 필수 내용, 금지 사항, 최종 산출물 기준.

---

## 세션 3 — 2026-06-03 · plan_codex.md 재평가 → plan_claude.md 생성 (Opus 4.8)

### 사용자 프롬프트 #5
> @plan_codex.md 는 너가 짠 plan을 codex로 평가 및 보완 수정 한거야. 이걸 다시 평가해서 수정 보완 해줄래? plan_claude.md로 생성해줘.

### AI 행동
1. `plan_codex.md` 전체 정독. 유지할 좋은 결정과 결함 분리.
2. PDF 설계 의도 + Java SE 현실에 비춰 critical 결함 10개(C1~C10) 식별:
   - C1 `System.exit()` 미처리 → AlgoBench JVM 전체 종료 위험.
   - C2 좀비 스레드 출력 오염(`cancel(true)` 후 진짜 `System.out` 기록).
   - C3 정적 상태 케이스 간 누수(classLoader 1회 로드).
   - C4 Windows-Korean 자식 프로세스 인코딩(cp949 ↔ UTF-8) 깨짐.
   - C5 STREAM_LOCK과 timeout 서브스레드 모델 미결합.
   - C6 Java 경로 exitCode 의미 미정의.
   - C7 CompositeResultLogger 예외 정책 모호.
   - C8 벤치마크 타이밍(JIT 워밍업) 신뢰성 미언급.
   - C9 ProblemLoader 마커 충돌/음성 테스트 부재.
   - C10 build.ps1이 컴파일+실행 혼재.
3. `plan_claude.md` 생성. codex의 골격·좋은 결정 유지 + C1~C10 보완:
   - `JavaJarSolution` 실행 모델 통합 명세(per-call URLClassLoader 재로딩, daemon timeout worker, 락 범위, finally 복구·close, exitCode 표).
   - in-JVM 격리 근본 한계 명문화(SecurityManager JDK24+ 영구 비활성화 → 외부 JVM 권장).
   - 외부 프로세스 UTF-8 강제(PYTHONIOENCODING/-Dfile.encoding).
   - CompositeResultLogger throw 금지 정책 확정.
   - build.ps1/run-demo.ps1 분리, 음성 테스트·격리 회귀 검증 추가.
   - 재평가 요약 표(2.2)로 codex 대비 변경점 명시.

### 현재 상태
- 계획 문서 3종: `plan.md`(초안) → `plan_codex.md`(codex 보완) → `plan_claude.md`(재평가 최종).
- 코드 구현은 사용자 추가 지시 대기 중.

---

## 세션 4 — 2026-06-03 · plan_codex2.md 재평가 → plan_claude2.md 생성 (Opus 4.8)

### 사용자 프롬프트 #6
> @plan_codex2.md 는 너가 짠 plan을 codex로 평가 및 보완 수정 한거야. 이걸 다시 평가해서 수정 보완 해줄래? plan_claude2.md로 생성해줘.

### AI 행동
1. `plan_codex2.md` 전체 정독. 평가 결론: 거의 수렴 — C1~C10 대부분 잘 반영, `process.descendants()` 강제 종료는 오히려 개선.
2. 남은 실제 기술 결함 6개(D1~D6) 식별:
   - D1 (HIGH·인코딩 회귀) 자식 JVM에 `-Dfile.encoding`만 권장 → Java 18+에선 파이프 출력은 `stdout.encoding`(미설정 시 Windows-Korean MS949) 따름. `-Dstdout.encoding=UTF-8` 필요.
   - D2 (MED) JavaJarSolution timeout helper executor `shutdownNow()` 누락 → 스레드 누수.
   - D3 (MED) 외부 프로세스 reader future 무제한 회수 → 손자 프로세스 파이프 점유 시 영구 블로킹. grace timeout 필요.
   - D4 (MED) comparator/ExecutionResult null 안전성 미정의 → stdout null 시 NPE.
   - D5 (MED) CSV append-only로 run 반복 시 행 중복·run 구분 불가. solutionName 도출 규칙 미정의.
   - D6 (LOW) failedCaseIndexes 구분자, 멀티라인 stderr 셀, PowerShell ExecutionPolicy, 헤더 unknown 키.
3. `plan_claude2.md`(완전판) 생성. codex2의 모든 좋은 결정 계승 + D1~D6 보정:
   - 자식 JVM `-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8` 정정.
   - helper executor shutdownNow, reader grace timeout, ExecutionResult non-null/comparator null-safe.
   - CSV run마다 truncate+header, solutionName 도출·유일화 규칙.
   - failedCaseIndexes `;` 구분, errorMessage 첫 줄+200자 truncate, ExecutionPolicy 우회 안내, unknown 헤더 키 무시.
   - 델타 표(§2)와 수렴 메모(§16)로 codex2 대비 변경점 명시.
4. 수렴 판단: 구조·인터페이스·실행 모델 안정화. 다음 단계는 추가 평가보다 실제 구현 착수가 적절하다고 명시.

### 현재 상태
- 계획 문서 계보: `plan.md` → `plan_codex.md` → `plan_claude.md` → `plan_codex2.md` → `plan_claude2.md`(현 최신·수렴).
- 코드 구현은 사용자 추가 지시 대기 중.

---

## 세션 5 — 2026-06-03 · plan_claude2.md 평가 및 plan_codex3.md 생성 (GPT5.5)

### 사용자 프롬프트 #7
> plan_claude2.md를 평가하고 수정 및 보완하여 plan_codex3.md 를 만들어줘.

### AI 행동
1. 작업 디렉토리 확인: `plan_claude2.md`, `plan_codex2.md`, 이전 계획 문서, `ai_rec.md` 존재 확인.
2. `plan_claude2.md`와 `plan_codex2.md`를 정독해 `plan_claude2.md`의 D1~D6 보정 내용을 평가.
3. 로컬 Java 환경 확인:
   - `java -version` 결과: Java 25.0.2 LTS.
   - `java -XshowSettings:properties -version` 결과: `file.encoding=UTF-8`이지만 `stdout.encoding=MS949`, `stderr.encoding=MS949`, `native.encoding=MS949`.
   - 따라서 자식 JVM 출력 인코딩은 `-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8`이 필요하다는 `plan_claude2.md`의 D1 지적이 타당하다고 확인.
4. `plan_claude2.md`의 남은 보완점 8개(E1~E8)를 정리:
   - 절대경로 Java 명령 감지.
   - JVM 인코딩 속성 중복 삽입 방지.
   - CSV truncate 시점의 lazy initialization.
   - solution name 유일화 구현 수단.
   - duplicate known header key 오류 처리.
   - reader executor shutdown과 부분 캡처.
   - in-JVM Java와 외부 프로세스 시간 비교 한계.
   - process descendants 종료 순서 보강.
5. `plan_codex3.md` 생성. 포함 내용:
   - implementation-plan header.
   - `plan_claude2.md` 평가 및 Codex3 보완 표.
   - 최종 제약, PDF 요구사항 매핑, 디렉토리 구조, 문제 포맷.
   - 클래스별 구현 명세.
   - `ExternalProcessSolution` JVM UTF-8 인자 삽입 규칙.
   - `CsvResultLogger` lazy truncate/header 정책.
   - `Main.UniqueNamedSolution` wrapper 지침.
   - task 단위 구현 순서, 검증 계획, README 필수 내용, 금지 사항, 최종 산출물 기준.

---

## 세션 6 — 2026-06-03 · plan_codex3.md 재평가 → plan_claude3.md 생성 (Opus 4.8)

### 사용자 프롬프트 #8
> @plan_codex3.md 는 너가 짠 plan을 codex로 평가 및 보완 수정 한거야. 이걸 다시 평가해서 수정 보완 해줄래? plan_claude3.md로 생성해줘.

### AI 행동
1. `plan_codex3.md` 전체 정독. 평가 결론: **수렴 완료, 구현 착수 가능 상태.** codex3가 D1을 로컬 환경(Java 25, stdout.encoding=MS949) 실측으로 검증, task 체크리스트·UniqueNamedSolution·lazy CSV init·process-tree kill 순서까지 고정. 신규 아키텍처 결함 없음.
2. 정직하게 "critical 결함 날조" 지양. 진짜 남은 보정 5개(G1~G5) 식별:
   - G1 (MED·기능 누락) 풀이 개별 로깅만 있고 풀이 간 **비교/순위 요약**이 없음 — PDF 1.3/3.4의 벤치마킹 본질 산출물이 6회 반복 동안 누락. `BenchmarkSummaryPrinter` 추가(통과 우선→총 시간 오름차순, 최속 정답 강조).
   - G2 (LOW·정밀도) `toMillis()`로 마이크로초대 풀이가 0ms 뭉개짐 → 소수 ms(`toNanos()/1e6`)로 포맷. PDF "정밀한 성능 측정" 충족.
   - G3 (LOW) JavaJarSolution이 스트림 스왑 전 클래스 로드 → static init 출력 미캡처. 순서를 스왑→로드→타이머→invoke로 고정.
   - G4 (LOW) JudgeEngine.shutdown에 awaitTermination→shutdownNow 안전망.
   - G5 (문서) CLAUDE.md 포인터를 plan_claude3.md로.
3. `plan_claude3.md`(완전판) 생성. codex3 전 결정 계승 + G1~G5 반영. agentic task 체크리스트 형식 유지.
4. **수렴 권고 명시**: 추가 평가 라운드는 수확 체감. 다음 단계는 문서 검토가 아니라 plan_claude3.md 기준 실제 구현 착수.

### 현재 상태
- 계획 문서 계보: plan.md → plan_codex.md → plan_claude.md → plan_codex2.md → plan_claude2.md → plan_codex3.md → **plan_claude3.md**(최신·수렴).
- 코드 구현은 사용자 추가 지시 대기 중. (구현 착수 권고)

---

## 세션 7 — 2026-06-06 · 마일스톤 분해 → MILESTONES.md 생성 (Opus 4.8)

### 사용자 프롬프트 #9
> 단계별 작업을 할 수 있도록 계획을 확인해 작업들을 마일스톤별로 나누고 그걸 .md 파일로 저장해줘.

### AI 행동
1. `plan.md`(CLAUDE.md 지정 source of truth) 정독 — §구현 순서(9단계) + §Verification 확인.
2. 9단계 구현 순서를 의존성 기준 8개 마일스톤(M0~M7)으로 재그룹화. 각 마일스톤에 목표·작업·완료 기준(Exit Criteria)·의존성 명시.
   - M0 골격 / M1 도메인+비교기 / M2 로더+샘플문제 / M3 결과+로거 / M4 풀이실행 / M5 엔진 / M6 Main+샘플풀이 / M7 E2E 검증.
   - `TestCase.matches`가 `OutputComparator` 참조 → 도메인+비교기를 M1로 묶음. M3·M4는 M1 위에서 병행 가능 표기.
3. `MILESTONES.md` 생성. 의존성 그래프 + 마일스톤별 산출물/핵심검증 요약 표 포함.
4. 참고: 본 분해는 `plan.md` 기준. 최신 수렴본 `plan_claude3.md`의 추가 항목(G1 BenchmarkSummaryPrinter 풀이 간 비교/순위 요약, G2 ms 소수점 정밀도, G3 스왑→로드 순서, G4 awaitTermination 안전망)은 미반영 — 사용자 선택 시 M3/M4/M5/M6에 반영 필요.

### 현재 상태
- 단계별 실행 문서 `MILESTONES.md` 추가(M0~M7).
- 코드 구현은 사용자 추가 지시 대기 중.

### 사용자 프롬프트 #10
> 반영해

### AI 행동
5. `plan_claude3.md` 정독해 G1~G4 정확 내용 확보. `MILESTONES.md`에 타깃 반영:
   - **G1** `BenchmarkSummaryPrinter`(풀이 간 비교/순위 — 통과 우선→총 시간 오름차순, 최속 정답 강조) → M3 구현 작업·완료 기준 + M6 `Main` 호출 + M7 검증 추가.
   - **G2** 실행 시간 소수 ms(`toNanos()/1_000_000.0`) → M3 Console/CSV formatter + 완료 기준 + M7 검증.
   - **G3** `JavaJarSolution` 스왑→로드(static init 캡처)→타이머→invoke 순서 → M4 작업·완료 기준.
   - **G4** `JudgeEngine.shutdown()` awaitTermination→shutdownNow 안전망 → M5 작업·완료 기준.
   - 문서 상단에 G1~G4 반영 안내, 각 작업에 `(G#)` 태그, 하단 요약 표 갱신.
   - (G5는 구현 후 CLAUDE.md 갱신 항목 — 코드 미착수라 보류.)

### 현재 상태
- `MILESTONES.md` = `plan.md` 9단계 골격 + `plan_claude3.md` G1~G4 반영본.
- 코드 구현은 사용자 추가 지시 대기 중.

### 사용자 프롬프트 #11
> 구현계획 즉 plan과 milestone을 단일 html 문서로 최대한 시각자료를 이용해서 시각화해라.

### AI 행동
6. `frontend-design` 스킬 적용. 단일 파일 `algobench_visual.html` 생성 — 의존성 0, 외부 자원은 Google Fonts(Chakra Petch/IBM Plex Sans KR/JetBrains Mono)만.
7. 테마: 엔지니어링 블루프린트/콘솔(다크 + 그리드 배경 + grain). 시각 자료:
   - Hero(타이틀·제약 chip) + 통계 strip(5단계/6패키지/8마일스톤/4인터페이스).
   - 데이터 파이프라인 5단계 플로우(로드→실행→비교→결과수집→리포팅).
   - 패키지×클래스 맵 6카드 + 인터페이스/Data Class/G 보정 범례.
   - 다형성 트리 SVG(Solution/OutputComparator/ResultLogger → 구현체).
   - 문제 파일 포맷 코드블록(구문 강조).
   - 마일스톤 타임라인 M0~M7(목표·산출물·Exit Criteria·의존성, G1~G4 인라인 태그).
   - 의존성 DAG SVG(M0→M1→{M2,M3,M4}→M5→M6→M7, 병행 표시).
   - G1~G4 보정 카드, 비자명 규칙 6노트, E2E 검증 표 + 실행 명령.
   - IntersectionObserver 스태거 reveal 애니메이션.
8. 내용 출처: plan.md + MILESTONES.md(G1~G4 반영). 클래스/규칙/검증 시나리오 모두 설계 문서와 일치.

### 현재 상태
- 시각화 산출물 `algobench_visual.html` 추가(브라우저로 열어 확인).
- 코드 구현은 사용자 추가 지시 대기 중.

---

## 세션 7 (계속) — 2026-06-06 · M0 구현·실행 (프로젝트 골격) (Opus 4.8)

### 사용자 프롬프트 #12
> M0 구현 실행해

### AI 행동
1. 디렉토리 트리 생성: `src/algobench/{domain,loader,solution,compare,engine,result}`, `problems`, `solutions/{java,python}`, `out`, `out_solutions`, `reports`.
2. 빈 패키지 디렉토리 git 유지용 `.gitkeep` 9개 + `.gitignore`(out/·out_solutions/·reports/·*.class·*.jar·에디터 파일 제외) 생성.
3. `build.ps1` 작성 — 3단계(엔진 컴파일 → 샘플 Java 풀이 컴파일 → 데모 실행). 산출물 디렉토리 `New-Item -Force` 보장, `-NoRun` 스위치(컴파일만), 소스 없으면 graceful 종료. 상단에 콘솔 UTF-8 설정(`chcp 65001` + `[Console]::OutputEncoding`).
4. `README.md` 작성 — 목적, Pure Java SE 제약, build.ps1·수동 javac/java 빌드법, ExecutionPolicy 우회, 디렉토리 구조, 문제 포맷, 풀이 제출 규칙(.class/.jar↔외부), 설계 트레이드오프·한계(전역 스트림/타임아웃/인코딩/시간측정/G1 비교 요약), 기록 규칙.
5. 검증:
   - `powershell -ExecutionPolicy Bypass -File .\build.ps1` → 소스 없어 1단계서 graceful 종료(정상). (콘솔 한글은 호스트 cp949 캡처 아티팩트 — 파일은 UTF-8 정상.)
   - 파일 7종(README/build.ps1/.gitignore/ai_rec/plan/MILESTONES/html) 전부 OK.
   - 패키지 디렉토리 12개 전부 OK.
   - 툴체인: `javac 25.0.2` 확인.

### 현재 상태
- **M0(프로젝트 골격) 완료.** Exit Criteria 충족 — 구조 plan.md 일치, README·build.ps1·ai_rec 존재.
- 다음: M1(도메인 + 비교기 — Problem/TestCase + OutputComparator 3종).
