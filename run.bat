@echo off
REM ===========================================================================
REM  KReport dev server launcher (Windows)
REM
REM    run.bat          in-memory DB  - resets to a clean state on every start
REM    run.bat local    file DB       - keeps your reports under .\data
REM
REM  NOTE: this file is intentionally ASCII-only.
REM  cmd.exe reads .bat files using the console code page (949 on Korean
REM  Windows), so UTF-8 Hangul in here would be garbled -- and a garbled REM
REM  line stops being a comment and gets run as a command. Korean text belongs
REM  in README.md and docs/USER-GUIDE.md, not here.
REM
REM  "cd /d %~dp0" below moves to the folder holding this file, so the script
REM  works no matter where it is launched from.
REM
REM  "chcp 65001" switches the console to UTF-8 so the server's Korean log
REM  lines render correctly. Without it they appear as mojibake.
REM ===========================================================================

cd /d "%~dp0"
chcp 65001 >nul
set JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8

set PROFILE=
set PROFILE_LABEL=in-memory (resets on restart)
if not "%~1"=="" (
    set PROFILE=--spring.profiles.active=%~1
    set PROFILE_LABEL=%~1
)

if not exist "target\kreport-1.0.0.jar" (
    echo [KReport] No build found. Building first, this takes a few minutes...
    call mvn -q package -DskipTests
    if errorlevel 1 goto :error
)

echo.
echo   KReport    http://localhost:8080
echo   folder     %CD%
echo   profile    %PROFILE_LABEL%
echo   stop with  Ctrl+C
echo.

java -jar "target\kreport-1.0.0.jar" %PROFILE%
goto :eof

:error
echo.
echo [KReport] Build failed. Check the errors above.
echo           Make sure Java 21 is installed and mvn is on PATH.
pause
exit /b 1
