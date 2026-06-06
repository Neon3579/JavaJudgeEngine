<#
  build.ps1 — AlgoBench 컴파일 + 데모 실행 헬퍼 (빌드 도구 없음, 순수 javac/java)

  단계:
    1) 엔진 컴파일      : src/**.java        -> out/
    2) 샘플 풀이 컴파일  : solutions/java/**.java -> out_solutions/  (URLClassLoader가 로드할 .class)
    3) 데모 실행        : java -cp out algobench.Main <problemFile> <solution...>

  실행:
    powershell -ExecutionPolicy Bypass -File .\build.ps1
    .\build.ps1 -NoRun        # 컴파일만 (실행 생략)

  주의: 소스가 아직 없는 마일스톤(M0)에서는 javac 단계에서 멈춘다(정상).
#>

param(
  [switch]$NoRun
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
Set-Location $root

# 콘솔 UTF-8 — 한글 출력/입력 깨짐 방지 (Windows cp949 회피)
try { chcp 65001 > $null } catch {}
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

function Section($msg){ Write-Host "`n=== $msg ===" -ForegroundColor Cyan }

# 산출물 디렉토리 보장
foreach ($d in @("out","out_solutions","reports")) {
  New-Item -ItemType Directory -Force -Path (Join-Path $root $d) | Out-Null
}

# ---- 1) 엔진 컴파일 ----
Section "1/3  엔진 컴파일 (src -> out)"
$engineSources = Get-ChildItem -Recurse -Path (Join-Path $root "src") -Filter *.java -ErrorAction SilentlyContinue
if (-not $engineSources) {
  Write-Host "  (src에 .java 없음 — 아직 구현 전. M2~M8 진행 후 다시 실행)" -ForegroundColor Yellow
  exit 0
}
javac -encoding UTF-8 -d (Join-Path $root "out") $engineSources.FullName
if ($LASTEXITCODE -ne 0) { Write-Host "엔진 컴파일 실패" -ForegroundColor Red; exit $LASTEXITCODE }
Write-Host "  OK ($($engineSources.Count) files)" -ForegroundColor Green

# ---- 2) 샘플 Java 풀이 컴파일 ----
Section "2/3  샘플 Java 풀이 컴파일 (solutions/java -> out_solutions)"
$solSources = Get-ChildItem -Recurse -Path (Join-Path $root "solutions\java") -Filter *.java -ErrorAction SilentlyContinue
if ($solSources) {
  javac -encoding UTF-8 -d (Join-Path $root "out_solutions") $solSources.FullName
  if ($LASTEXITCODE -ne 0) { Write-Host "샘플 풀이 컴파일 실패" -ForegroundColor Red; exit $LASTEXITCODE }
  Write-Host "  OK ($($solSources.Count) files)" -ForegroundColor Green
} else {
  Write-Host "  (샘플 Java 풀이 없음 — M6에서 추가)" -ForegroundColor Yellow
}

if ($NoRun) { Section "완료 (컴파일만)"; exit 0 }

# ---- 3) 데모 실행 ----
Section "3/3  데모 실행"
$problem = Join-Path $root "problems\a_plus_b.txt"
if (-not (Test-Path $problem)) {
  Write-Host "  (problems\a_plus_b.txt 없음 — M2에서 추가. 실행 생략)" -ForegroundColor Yellow
  exit 0
}
java -cp (Join-Path $root "out") algobench.Main $problem `
     (Join-Path $root "out_solutions\CorrectSolution.class") `
     (Join-Path $root "out_solutions\WrongSolution.class") `
     (Join-Path $root "out_solutions\TimeoutSolution.class") `
     "python solutions/python/correct_solution.py"
