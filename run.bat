@echo off
REM KReport 개발 서버 실행 (윈도우)
REM
REM chcp 65001 로 콘솔을 UTF-8 로 맞춘다. 이걸 하지 않으면 자바가 UTF-8 로 쓴 한글 로그를
REM CP949 콘솔이 잘못 읽어 "?곗씠?곗뀑" 처럼 깨진다.
REM mvn spring-boot:run 은 출력을 파이프로 받아 자바가 콘솔을 알아채지 못하므로,
REM 여기서는 jar 를 직접 띄운다.

chcp 65001 >nul
set JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8

if not exist target\kreport-1.0.0.jar (
    echo [KReport] 빌드본이 없어 먼저 빌드합니다...
    call mvn -q package -DskipTests || goto :error
)

echo [KReport] http://localhost:8080 에서 뜹니다. 끄려면 Ctrl+C.
java -jar target\kreport-1.0.0.jar %*
goto :eof

:error
echo [KReport] 빌드에 실패했습니다.
exit /b 1
