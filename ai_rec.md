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

---

## 세션 7 (계속) — 2026-06-06 · M1 구현·실행 (도메인 + 비교기) (Opus 4.8)

### 사용자 프롬프트 #13
> M1 구현 실행해

### AI 행동
1. CLAUDE.md "테스트 프레임워크 없음, 검증=Main 데모 러너 E2E" 지시가 TDD 스킬보다 우선 → JUnit 미사용. 컴파일 + 임시 스크래치 검증 방식 채택.
2. MILESTONES M1 범위대로 5개 클래스 작성(시그니처는 plan_claude3 §7.1/§7.4 기준). plan_claude3의 CaseInsensitiveComparator는 M1 목록 밖이라 스킵(나중 확장점):
   - `compare/OutputComparator.java` «interface» — `matches(expected, actual)`, 구현체 null→"" 계약 명시.
   - `compare/ExactOutputComparator.java` — CRLF→LF, 줄 끝 공백 제거, 끝 trailing newline 제거 후 정확 비교.
   - `compare/WhitespaceNormalizingComparator.java` — strip + 연속 whitespace 1칸 축약(토큰 비교, NFR-03).
   - `domain/TestCase.java` (불변) — index≥1·input/expected non-null 검증, `matches(actual, comparator)` 위임.
   - `domain/Problem.java` (불변) — title non-blank·timeLimit>0·memoryLimitMb>0·testCases non-empty 검증, `List.copyOf` 방어적 복사.
3. 검증:
   - `javac -encoding UTF-8 -d out` → exit 0, .class 5개 생성.
   - 임시 `_scratch/Verify.java`로 동작 검증 → **26 PASS / 0 FAIL** (비교기 정규화/null-safe, TestCase 위임·검증, Problem 검증·불변·방어적 복사). `-Dstdout.encoding=UTF-8`로 한글 출력 정상 확인.
   - `_scratch` 삭제.

### 현재 상태
- **M1(도메인 + 비교기) 완료.** Exit Criteria 충족 — domain+compare 컴파일 성공, 전부 불변·방어적 복사.
- 다음: M2(ProblemLoader + 샘플 문제 2개) 또는 M3/M4(M1 위 병행 가능).

---

## 세션 7 (계속) — 2026-06-06 · M2·M3·M4 구현·실행 (Opus 4.8)

### 사용자 프롬프트 #14
> /goal M2, M3, M4 구현 완료

### 설계 결정 (기록)
- **`Solution.execute` 시그니처 충돌 해소**: `plan.md`/MILESTONES M4는 `execute(String input)`(타임아웃 생성자 주입 허용), 최신 수렴본 `plan_claude3.md`는 `execute(String input, Duration timeout)`. **후자 채택** — M5 `GradingTask`가 케이스별 `execute(input, timeLimit)` 호출하고 풀이를 무상태로 유지(문제별 재생성 불필요). plan.md가 "타임아웃은 호출측이 별도 전달하거나 생성자 주입" 둘 다 허용하므로 위배 아님.

### AI 행동
1. **M2 — 로더 + 샘플 문제**
   - `loader/ProblemLoader.java`: `Problem loadProblem(String) throws IOException`. UTF-8 읽기 → `\r\n`/`\r`→`\n` 정규화 → 헤더 파싱(`KEY: VALUE`, 필수 TITLE/TIME_LIMIT_MS/MEMORY_LIMIT_MB, 알려진 키 중복 오류·미지정 키 무시) → `###` 케이스 분리 → 케이스별 `INPUT:`/`EXPECTED:` 마커(줄 전체 정확 일치, 1회씩) 파싱 → 본문 내부 줄바꿈 보존·끝 trailing newline 제거. 오류는 `파일:라인 - 메시지`.
   - `problems/a_plus_b.txt`(4케이스), `problems/max_of_three.txt`(4케이스).
2. **M3 — 결과 + 로거/포매터 + 요약**
   - `result/TestCaseResult.java`·`BenchmarkResult.java`(불변, null→"" 보정, `List.copyOf`, `getPassedCount`/`getTotalCount`).
   - `result/ResultLogger.java`(«interface» `log(...) throws IOException`), `ConsoleResultLogger.java`(풀이별 표·**(G2) 소수 ms `toNanos()/1_000_000.0`**·실패 케이스/기대·실제 요약).
   - `result/ResultFormatter.java`(«interface» `header()`/`format()`), `CsvResultFormatter.java`(컬럼 7종·**(G2) 소수 ms**·`failedCaseIndexes` `;`·`firstErrorMessage` 첫줄 200자·RFC 4180 escaping).
   - `result/CsvResultLogger.java`(lazy init: 부모 디렉토리 생성→truncate→헤더 1회, `synchronized` append — run마다 fresh).
   - **(G1) `result/BenchmarkSummaryPrinter.java`**: `printComparison(List<BenchmarkResult>, PrintStream)` — 통과 우선→총 시간 오름차순 정렬, 최속 정답 `★` 강조. 결과만 읽는 순수 함수.
3. **M4 — 풀이 실행**
   - `solution/ExecutionResult.java`(불변·non-null·나노 보존·`isSuccess`), `Solution.java`(«interface» `getName`/`execute(input, timeout)`).
   - `solution/ExternalProcessSolution.java`: `ProcessBuilder`·stdin UTF-8·`PYTHONIOENCODING=utf-8`·첫 토큰 java면 `-Dstdout/stderr.encoding=UTF-8` 삽입(중복 방지)·stdout/stderr 별도 reader 스레드 동시 판독·`waitFor(timeout)` 초과 시 descendants+root `destroyForcibly`·reader grace 500ms·tokenizer(quote 묶음, unmatched 예외).
   - `solution/JavaJarSolution.java`: per-call `URLClassLoader`(부모=platform 로더, static 상태 격리)·`STREAM_LOCK`으로 스트림 교체 직렬화·**(G3) 스왑→로드(static init 캡처)→타이머→invoke 순서**·데몬 단일 워커 `Future.get(timeout)` best-effort·`finally` 스트림 복구→`shutdownNow`→`loader.close()`. `.jar`는 manifest `Main-Class`. 생성 시 main 시그니처 1회 검증.
4. **검증** — `javac -encoding UTF-8 -d out` 전체 컴파일 성공(에러 0). 임시 `_scratch/Verify.java` + 샘플 클래스(AddTwo/StaticInit/Boom/Loop)로 E2E 스모크: **24 PASS / 0 FAIL**.
   - M2: title/시간/메모리/케이스수·case1 input·expected 파싱 정확, 음성(EXPECTED 누락→라인번호 포함 예외).
   - M4: AddTwo 정상(stdout=3·success·time>0), **(G3) static-init 출력 캡처**, Boom 예외 격리(exitCode 1·stderr 스택트레이스), Loop 타임아웃(timedOut·메인 계속), ExternalProcessSolution java 자식으로 AddTwo 실행(stdout=30, **한글/숫자 정상**) — 다형성 확인.
   - M3: ConsoleResultLogger 출력, CSV 헤더+3행, **(G2) 소수 ms `1.200`**(0 아님), **(G1) 요약 정렬 통과 우선→시간 오름차순·최속 정답 AddTwo `★`**.
   - `_scratch` 삭제.

### 현재 상태
- **M2·M3·M4 완료.** Exit Criteria 전부 충족(컴파일·불변성·파싱 스모크·타임아웃/스트림 락·G1/G2/G3). `out/algobench/{loader,result,solution}` 산출.
- 다음: M5(engine — GradingTask + JudgeEngine, G4). M5는 M1/M3/M4 의존 충족됨.

---

## 세션 7 (계속) — 2026-06-06 · M5·M6·M7 구현·E2E 검증 (Opus 4.8)

### 사용자 프롬프트 #15
> /goal M7 까지 구현해줘

### AI 행동
1. **M5 — 엔진**
   - `engine/GradingTask.java` `implements Callable<BenchmarkResult>`: 케이스마다 `execute(input, timeLimit)` → 판정 우선순위 **TIMEOUT → RUNTIME_ERROR(exitCode≠0, stderr 첫줄 200자) → WRONG_ANSWER → 통과**. `actualOutput`엔 stdout 항상 기록, `totalExecutionTime`=케이스 합(나노 보존). 미캡슐 예외는 `EXCEPTION`으로 케이스 단위 격리 후 계속(NFR-04).
   - `engine/JudgeEngine.java`: `newFixedThreadPool(max(1, availableProcessors()))` + `ResultLogger` DI. `evaluateAllAsync`(제출 순서 Future), `evaluateAll`(제출 순서 수집 + **메인 스레드 순차 `logger.log`** — NFR-05, IOException은 경고 후 계속), **(G4) `shutdown()` = shutdown→awaitTermination(2s)→shutdownNow**.
2. **M6 — Main + 샘플 + 빌드**
   - `result/CompositeResultLogger.java`(신규): 등록 순서 위임, 한 로거 실패해도 계속·예외는 `System.err` 경고만(재throw 금지).
   - `Main.java`: 인자 `<problemFile> <solution...>`, 확장자 판별(`.class`/`.jar`→`JavaJarSolution`, else→`ExternalProcessSolution`), `UniqueNamedSolution` wrapper로 이름 유일화(`name#2`…), comparator=`ExactOutputComparator`, logger=`CompositeResultLogger[Console, Csv]`, `evaluateAll` → **(G1) `BenchmarkSummaryPrinter.printComparison`** → `finally`에서 `shutdown`.
   - 샘플: `CorrectSolution`(A+B 정답), `WrongSolution`(절댓값 버그 → 음수 케이스 오답), `TimeoutSolution`(무한루프), `solutions/python/correct_solution.py`(외부 정답).
   - `build.ps1`은 M0에서 3단계(엔진→샘플→데모) 골격 완성 상태 — 그대로 사용.
3. **M7 — E2E 검증**
   - 컴파일: 엔진 22 파일 + 샘플 Java 3 OK. Python 3.13.11 확인.
   - 데모 `java ... algobench.Main problems/a_plus_b.txt <4풀이>`:
     - `CorrectSolution` ALL PASS 4/4 (9.6ms), **최속 정답 `★`**.
     - `WrongSolution` FAIL 3/4 — 케이스 3 WRONG_ANSWER(기대 7 / 실제 13).
     - `TimeoutSolution` FAIL 0/4 — 케이스별 TIMEOUT(2000ms), 총 8001ms, **프로그램 계속 진행**(NFR-04).
     - `correct_solution.py` ALL PASS 4/4 (외부 프로세스 다형성, 한글 정상).
   - **(G1)** 비교 요약: 통과 우선→총 시간 오름차순 정렬, `CorrectSolution ★ 최속 정답` 정확.
   - **(G2)** 소수 ms(9.618/3.037/8001.250 등) — 0 뭉개짐 없음.
   - `reports/result.csv`: 헤더+4행, `failedCaseIndexes` `;` 결합, UTF-8 한글 정상.
   - **`build.ps1` 전체 실행 통과**(엔진 22 + 샘플 3 컴파일 + 데모). 콘솔 한글은 PowerShell 호스트 캡처 시 cp949 깨짐 아티팩트 — 파일(CSV)·직접 `java -Dstdout.encoding=UTF-8` 실행은 정상.
   - **CSV fresh**: 2회 실행 후에도 4행 유지(누적 아님 — lazy truncate 동작).
   - **NFR-03**: comparator는 `evaluateAll(problem, solutions, comparator)` 주입점이라 엔진/Task/Solution 수정 없이 교체 가능(구조적 충족).
4. 문서 갱신: `CLAUDE.md` 현재 상태(설계 단계 → 구현 완료 M0~M7 + execute 시그니처 결정 메모), `README.md` 상태줄.

### 현재 상태
- **M0~M7 전부 완료.** AlgoBench end-to-end 동작. 검증 시나리오(정답/오답/타임아웃/외부 다형성) + CSV + G1/G2 전부 확인.
- 미반영(차후 선택): plan_claude3의 추가 확장(`CaseInsensitiveComparator`, `malformed_example.txt` 음성 파일, `SlowInterruptibleSolution`/`timeout_solution.py`, `run-demo.ps1` 분리) — M0~M7 범위 밖.

---

## 세션 8 — 2026-06-06 · JAR 빌드 + 디버깅/테스트 bat (Opus 4.8)

### 사용자 프롬프트 #16
> 이 프로젝트를 jar로 빌드한 뒤 디버깅/테스트 용 bat 프로그램 구현 / (커밋·푸시) / git 커밋 / 푸시

### 결정 (AskUserQuestion)
- bat 형태: **인터랙티브 메뉴**.
- jar 범위: **엔진 + 샘플 풀이 jar** (JavaJarSolution의 `.jar` manifest Main-Class 로드 경로 시연).

### AI 행동
1. `Main.java`에 디버그 플래그 추가(엔진 무수정·additive): `-Dalgobench.verbose=true` 시 통과 케이스 포함 전 케이스 stdin 결과(기대/실제/사유) 상세 출력.
2. `algobench.bat`(신규, UTF-8/CRLF) 작성:
   - 대화식 메뉴 [1]빌드 [2]전체데모 [3]문제선택 [4]단일풀이 디버그(verbose) [5]CSV [0]종료.
   - 비대화식 인자 모드 `build`/`demo`/`help`(스크립트·자동 테스트용).
   - 빌드: `javac`(엔진 `for /r` 열거 + 따옴표 → 공백·한글 경로 안전, 샘플 `for` glob) → `jar --create --main-class`로 `dist\algobench.jar` + 샘플 풀이 jar 3개.
   - EOF 가드(리다이렉트/파이프 무한루프 방지).
3. **디버깅 이력(체계적 격리)**:
   - 1차: bat이 **LF 줄끝** → cmd 라인 토막(`out`→`ut`, `java`→`va`). → **CRLF(UTF-8 no-BOM) 변환**으로 해결.
   - 2차: 리다이렉트 입력서 메뉴 무한루프. probe로 격리 → **`chcp 65001` + 리다이렉트 stdin → `set /p` 빈값 읽는 cmd 제약** 확인(키보드 입력은 정상). → 인자 모드 + EOF 가드로 대응.
   - 3차: javac argfile 절대경로의 공백(`자프 과제`)서 토막 + cmd `*.java` glob 미확장. → **`for` 열거 + 따옴표(Win32 argv는 공백·한글 안전) + delayed expansion**으로 해결.
4. **검증**: `algobench.bat build` → 엔진/샘플 컴파일 + jar 4개(exit 0, 한글 클린). `algobench.bat demo` → 4풀이 시나리오 정상(Correct ★ / Wrong 케이스3 / Timeout 계속 / Python). `java -jar dist\algobench.jar`로 엔진 jar + 샘플 풀이 `.jar` 로드(manifest Main-Class) 정상. verbose 단일 풀이 전 케이스 상세 확인.
5. 문서: `README.md` JAR+bat 섹션(산출물 표·실행 예시·chcp/set-p 제약 주의), `.gitignore`에 `dist/` 추가.

### 현재 상태
- JAR 빌드(`dist/algobench.jar` + 샘플 풀이 jar) + 디버깅/테스트 콘솔 `algobench.bat` 완료·검증.
- `dist/`·`*.jar`은 gitignore — bat·Main 변경·문서만 커밋 대상.

### 사용자 프롬프트 #17
> 이러면 매 풀이마다 컴파일 해야되는거 아님?

### AI 행동
1. 답변: `.class`/`.jar`(JavaJarSolution)은 바이트코드라 사전 컴파일 필요(단, `build`가 `solutions/java/*.java` 일괄 컴파일 → 풀이별 개별 컴파일 아님). 컴파일 없이 돌리려면 `"java X.java"`(외부 프로세스 = Java 단일파일 소스 런처). 검증: 컴파일 없이 ALL PASS, 단 1085ms(컴파일본 9ms 대비 ~120배 — 케이스마다 재컴파일). → 탐색=소스 런처, 벤치마크=사전 컴파일.
2. 사용자 선택(AskUserQuestion): bat에 **소스 직실행 옵션 추가**.
3. `algobench.bat`에 메뉴 `[6] .java 직접 실행(컴파일 없이)` 추가 → `java %ENC% -jar dist\algobench.jar <문제> "java <경로>"`로 외부 프로세스 실행. 검증: `-jar` + 소스 런처 ALL PASS, `help`/구문 무결성 OK.
4. `README.md`에 "컴파일 필요 여부" 표(JavaJarSolution vs 소스 런처) + `[6]` 메뉴 반영.
   - 참고: PowerShell 툴에서 `-Dstdout.encoding=UTF-8` 직접 전달 시 인자 토막 아티팩트 관찰(cmd/bash는 정상) — bat은 cmd 실행이라 무관.

### 현재 상태
- `algobench.bat` [6] 소스 직실행 추가·검증. 컴파일 마찰 해소 경로 문서화.

### 사용자 프롬프트 #18
> 백준 처럼 문제를 골라 소스코드를 적거나 첨부하는 형식의 GUI를 만들어줘. 프레임워크는 Java Swing으로 모던하고 심플하게. 코어는 현재프로젝트 AlgoBench를 써.

### 설계 결정 (Plan 모드 + AskUserQuestion)
- 다중 비교(여러 풀이 한 화면 채점) / Java+외부 언어 / **에디터 Java는 외부 프로세스 격리 실행** / 커스텀 플랫 라이트 테마 — 4지선다 모두 권장안 채택.
- 코어 무수정(NFR-03): GUI는 새 패키지 `algobench.gui`에만 추가, 채점은 기존 `ProblemLoader`/`Solution`/`JudgeEngine.evaluateAll` 호출.

### AI 행동
1. `src/algobench/gui` 9개 클래스 신규 작성(순수 Swing + `javax.tools`, 외부 의존성 0):
   - `FlatTheme`(Nimbus+라이트 팔레트/폰트/팩토리) · `FlatButton`(둥근 플랫 버튼).
   - `ProblemListPanel`(`problems/*.txt` 스캔·열기·`ProblemLoader` 콜백) · `ProblemDetailPanel`(제목/제한/케이스 표).
   - `JavaSourceCompiler`(in-process 컴파일, `package`+public 클래스 파싱→FQN, 진단 수집, 임시디렉토리 shutdown-hook 정리).
   - `SubmissionPanel`(유형 콤보=에디터/파일/명령, EDT 스냅샷 `Spec`→백그라운드 `buildSolution`) · `SubmissionsPanel`(탭 다중 풀이).
   - `ResultsPanel`(풀이 요약표 + 케이스표 + 기대/실제/사유 상세, PASS/FAIL 색) · `BenchmarkRunner`(`SwingWorker`로 빌드+`evaluateAll`, 풀이별 빌드 실패 격리, 이름 유일화).
   - `AlgoBenchApp`(헤더+비교기 콤보[Exact/Whitespace]+채점 버튼, split 레이아웃, 배선).
2. 비자명 처리: EDT 금지(SwingWorker), 공백 경로 → `java -cp "<tmp>" FQN` 따옴표(코어 토크나이저 quote 그룹화 활용), 컴파일 진단 패널 표시, 첨부 `.class/.jar`만 같은 JVM(`JavaJarSolution`).
3. 빌드 통합: `build.ps1 -Gui` 스위치 + `algobench.bat` `[7] GUI 실행`/`gui` 인자 추가.

### 검증
1. 전체 컴파일 `javac src/** -> out` exit 0.
2. **헤드리스 스모크**(임시 `Smoke`, 코어-호출 체인 직접 호출 후 삭제): 에디터Java ALL PASS 4/4(262ms) · 파이썬 ALL PASS 4/4(117ms) · 첨부 TimeoutSolution.class FAIL 0/4 TIMEOUT(8003ms) — 3경로(컴파일→외부java / 외부 python / JavaJarSolution) 모두 정상.
3. GUI 시작 스모크: 백그라운드 실행 후 생존 확인(생성자/배선 무크래시) → 종료.
   - 콘솔 한글 깨짐은 PowerShell cp949 표시 한정(Swing 폰트 렌더·`build.ps1 chcp 65001`과 무관).

### 현재 상태
- AlgoBench Swing GUI(`algobench.gui`) 완료·검증. 코어 무수정, 순수 SE. 실행: `.\build.ps1 -Gui` 또는 `java -cp out algobench.gui.AlgoBenchApp`.

### 사용자 프롬프트 #19
> 폰트가 깨져

### AI 행동
1. 원인 격리: 폰트 probe(`canDisplay('가')`) — `Segoe UI`/`Consolas` = **false**(한글 글리프 없음 → tofu). `Malgun Gothic`/논리 `Monospaced`/`Dialog` = true.
2. `FlatTheme` 폰트 상수 교체: UI → `Malgun Gothic`(없으면 논리 `Dialog` fallback), 모노 → 논리 `Monospaced`(한글 fallback 합성). `pickUiFamily()` 런타임 가용성 체크 추가.
3. 검증: 재빌드 exit 0, GUI 시작 ALIVE. probe로 한글 렌더 가능 확정.

### 사용자 프롬프트 #20
> 현재 프로젝트의 정보와 구조를 최대한 시각자료를 이용해서 HTML 문서로 만들줘어

### AI 행동
1. 구조 수집(glob): 코어 22 + GUI 10 = **32 .java**, 7 패키지. 문제 포맷 예시 `a_plus_b.txt` 확인.
2. `docs/algobench-overview.html` 작성 — 단일 파일 자기완결(인라인 CSS + **인라인 SVG 다이어그램**, 외부 CDN 0 = 프로젝트 순수성 유지). 섹션 12개:
   개요(KPI) · 5단계 파이프라인 SVG · 패키지 맵 카드 · 디렉토리 트리 · 핵심 클래스 표 · Solution 다형성 클래스도 · 채점 시퀀스도(EDT/백그라운드) · GUI 레이아웃 목업 SVG · 문제 포맷 spec+예시 · 판정 우선순위(TIMEOUT→RE→WA→PASS) · 빌드/실행 · 제약·NFR.
3. 검증: 파일 생성(39.6KB), 기본 브라우저로 렌더 확인.

### 현재 상태
- GUI 한글 폰트 수정 완료. 시각 HTML 문서 `docs/algobench-overview.html` 산출(자기완결·오프라인).
