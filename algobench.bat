@echo off
rem ============================================================
rem  algobench.bat - AlgoBench 디버깅/테스트 콘솔 (순수 javac/java/jar)
rem  사용법:
rem    algobench.bat            대화식 메뉴 (더블클릭)
rem    algobench.bat build      컴파일 + jar 패키징만
rem    algobench.bat demo       전체 데모 1회 실행
rem    algobench.bat help       사용법
rem ============================================================
setlocal enabledelayedexpansion
chcp 65001 >nul
cd /d "%~dp0"

set "ENC=-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
set "ENGINE=dist\algobench.jar"
set "PYSOL=python solutions/python/correct_solution.py"
set "_eof=0"

where java  >nul 2>&1 || (echo [오류] java 를 찾을 수 없습니다. JDK PATH 확인. & exit /b 1)
where javac >nul 2>&1 || (echo [오류] javac 를 찾을 수 없습니다. JDK PATH 확인. & exit /b 1)

rem ---------------- 비대화식 인자 모드 ----------------
if /i "%~1"=="build" ( call :build & goto end )
if /i "%~1"=="demo"  ( call :run_demo & goto end )
if /i "%~1"=="gui"   ( call :run_gui & goto end )
if /i "%~1"=="help"  ( call :usage & goto end )
if not "%~1"=="" ( echo 알 수 없는 인자: %~1 & call :usage & goto end )

rem ---------------- 대화식 메뉴 ----------------
:menu
cls
echo ============================================
echo            AlgoBench 테스트 콘솔
echo ============================================
echo   [1] 빌드 (compile + jar)
echo   [2] 전체 데모 실행 (4풀이)
echo   [3] 문제 선택 후 풀이 실행
echo   [4] 단일 풀이 디버그 (verbose)
echo   [5] CSV 리포트 열기
echo   [6] .java 직접 실행 (컴파일 없이)
echo   [7] GUI 실행 (Swing)
echo   [0] 종료
echo --------------------------------------------
set "choice="
set /p "choice=선택> "
if not defined choice goto on_empty
set "_eof=0"
if "%choice%"=="1" ( call :build      & pause & goto menu )
if "%choice%"=="2" ( call :run_demo   & pause & goto menu )
if "%choice%"=="3" ( call :run_pick   & pause & goto menu )
if "%choice%"=="4" ( call :run_debug  & pause & goto menu )
if "%choice%"=="5" ( call :open_csv   & pause & goto menu )
if "%choice%"=="6" ( call :run_source & pause & goto menu )
if "%choice%"=="7" ( call :run_gui    & pause & goto menu )
if "%choice%"=="0" goto end
goto menu

rem 파이프/리다이렉트 입력(EOF)에서 무한루프 방지
:on_empty
set /a _eof+=1
if %_eof% geq 3 goto end
goto menu

rem ================= 서브루틴 =================

:build
echo.
echo [1/3] 엔진 컴파일 (src -^> out)
if not exist out mkdir out
set "SRCS="
for /r "src" %%f in (*.java) do set "SRCS=!SRCS! "%%f""
javac -encoding UTF-8 -d out !SRCS! || (echo   엔진 컴파일 실패 & exit /b 1)
echo   OK
echo [2/3] 샘플 풀이 컴파일 (solutions\java -^> out_solutions)
if not exist out_solutions mkdir out_solutions
set "SSRC="
for %%f in (solutions\java\*.java) do set "SSRC=!SSRC! "%%f""
javac -encoding UTF-8 -d out_solutions !SSRC! || (echo   샘플 컴파일 실패 & exit /b 1)
echo   OK
echo [3/3] jar 패키징 (-^> dist)
if not exist dist mkdir dist
jar --create --file dist\algobench.jar --main-class algobench.Main -C out . || (echo   엔진 jar 실패 & exit /b 1)
jar --create --file dist\CorrectSolution.jar --main-class CorrectSolution -C out_solutions CorrectSolution.class
jar --create --file dist\WrongSolution.jar  --main-class WrongSolution  -C out_solutions WrongSolution.class
jar --create --file dist\TimeoutSolution.jar --main-class TimeoutSolution -C out_solutions TimeoutSolution.class
echo   OK: dist\algobench.jar + 샘플 풀이 jar 3개
exit /b 0

:run_demo
if not exist "%ENGINE%" (echo   먼저 [1] 빌드가 필요합니다. & exit /b 1)
echo.
echo [데모] problems\a_plus_b.txt + 4풀이 (Java jar 3 + Python)
java %ENC% -jar "%ENGINE%" problems\a_plus_b.txt dist\CorrectSolution.jar dist\WrongSolution.jar dist\TimeoutSolution.jar "%PYSOL%"
exit /b 0

:run_pick
if not exist "%ENGINE%" (echo   먼저 [1] 빌드가 필요합니다. & exit /b 1)
echo.
echo 사용 가능 문제 (problems\):
for %%f in (problems\*.txt) do echo    %%~nxf
set "pf="
set /p "pf=문제 파일명> "
if "%pf%"=="" exit /b 0
if not exist "problems\%pf%" (echo   파일 없음: problems\%pf% & exit /b 1)
java %ENC% -jar "%ENGINE%" "problems\%pf%" dist\CorrectSolution.jar dist\WrongSolution.jar dist\TimeoutSolution.jar "%PYSOL%"
exit /b 0

:run_debug
if not exist "%ENGINE%" (echo   먼저 [1] 빌드가 필요합니다. & exit /b 1)
echo.
echo 풀이 선택:  1) CorrectSolution   2) WrongSolution   3) TimeoutSolution   4) Python
set "sn="
set /p "sn=풀이 번호> "
set "SOLARG="
if "%sn%"=="1" set "SOLARG=dist\CorrectSolution.jar"
if "%sn%"=="2" set "SOLARG=dist\WrongSolution.jar"
if "%sn%"=="3" set "SOLARG=dist\TimeoutSolution.jar"
if "%sn%"=="4" set "SOLARG=%PYSOL%"
if "%SOLARG%"=="" (echo   잘못된 선택 & exit /b 1)
set "pf=a_plus_b.txt"
set /p "pf=문제 파일명 [기본 a_plus_b.txt]> "
if not exist "problems\%pf%" (echo   파일 없음: problems\%pf% & exit /b 1)
echo.
echo [디버그] verbose=on  문제=%pf%  풀이=%SOLARG%
java %ENC% -Dalgobench.verbose=true -jar "%ENGINE%" "problems\%pf%" "%SOLARG%"
exit /b 0

:run_source
if not exist "%ENGINE%" (echo   먼저 [1] 빌드가 필요합니다. & exit /b 1)
echo.
echo .java 소스를 컴파일 없이 직접 실행 (java 단일파일 런처 = 외부 프로세스)
set "jp="
set /p "jp=.java 경로> "
if "%jp%"=="" exit /b 0
if not exist "%jp%" (echo   파일 없음: %jp% & exit /b 1)
set "pf=a_plus_b.txt"
set /p "pf=문제 파일명 [기본 a_plus_b.txt]> "
if not exist "problems\%pf%" (echo   파일 없음: problems\%pf% & exit /b 1)
echo.
echo [소스 실행] 문제=%pf%  소스=%jp%  (주의: 케이스마다 재컴파일 -^> 느림, 시간측정 왜곡)
java %ENC% -jar "%ENGINE%" "problems\%pf%" "java %jp%"
exit /b 0

:run_gui
if not exist "out\algobench\gui\AlgoBenchApp.class" (
  echo   GUI 클래스가 없습니다 - 먼저 [1] 빌드를 실행하세요.
  exit /b 1
)
echo.
echo [GUI] Swing 창 실행 (algobench.gui.AlgoBenchApp)
java %ENC% -cp out algobench.gui.AlgoBenchApp
exit /b 0

:open_csv
echo.
if exist reports\result.csv (
  echo --- reports\result.csv ---
  type reports\result.csv
  echo.
  start "" reports\result.csv
) else (
  echo CSV 없음 - 먼저 데모를 실행하세요.
)
exit /b 0

:usage
echo.
echo 사용법: algobench.bat [build ^| demo ^| gui ^| help]
echo   (인자 없이 실행하면 대화식 메뉴 - [6] .java 직접 실행, [7] GUI 실행)
exit /b 0

:end
endlocal
exit /b 0
