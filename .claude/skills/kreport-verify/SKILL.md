---
name: kreport-verify
description: KReport 변경을 실제로 확인하는 절차. 테스트 통과 후 서버를 띄워 PDF를 PNG로 구워 눈으로 보고, 화면 변경은 브라우저로 동작시킨 뒤 커밋·푸시한다. 차트·레이아웃·익스포터·디자이너 JS·보안 경로·실행 스크립트를 고쳤을 때 쓴다.
---

# KReport 변경 검증

이 프로젝트에서는 **테스트가 전부 통과해도 깨져 있던 적이 세 번** 있었다 — 차트 라벨 겹침,
교차 출처 임베드 차단, `run.bat` 인코딩. 모두 서버 밖(브라우저·콘솔·파일)에서만 드러난다.
그래서 규칙은 시험으로, 보이는 것은 실물로 확인한다.

## 1. 정리 — 포트와 jar 잠금

이전 서버가 살아 있으면 `Port 8080 was already in use` 로 기동이 실패하고, `mvn clean` 은 jar 잠금으로 실패한다.

```powershell
Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
  Where-Object { $_.CommandLine -like '*kreport*' } |
  ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
```

## 2. 컴파일과 테스트

```bash
mvn -o clean compile     # 메서드 시그니처를 바꿨으면 clean 필수
mvn -o test              # 전부 통과해야 함. 건수가 줄었으면 이유를 확인
```

새 규칙(권한, 캐시, 거부 조건)을 넣었으면 그 규칙을 지키는 시험도 함께 넣는다.

## 3. 서버 기동

```bash
mvn -o -q package -DskipTests
java -jar target/kreport-1.0.0.jar > /tmp/kr.log 2>&1 &   # 백그라운드
# "Tomcat started" 가 로그에 찍힐 때까지 기다린다 (약 7초)
```

로그인 세션이 필요한 API 는 curl 로:

```bash
T=$(curl -s -c cj http://localhost:8080/login | grep -o 'name="_csrf" value="[^"]*"' | sed 's/.*value="//;s/"//')
curl -s -b cj -c cj -o /dev/null -d "username=admin&password=admin1234!&_csrf=$T" http://localhost:8080/login
X=$(grep -o 'XSRF-TOKEN[[:space:]]*[^[:space:]]*$' cj | awk '{print $NF}')   # POST 에는 -H "X-XSRF-TOKEN: $X"
```

URL 질의에 한글을 넣으면 400 이 난다 — 인코딩하거나 빼고 보낸다.

## 4. 차트·레이아웃·PDF 를 고쳤으면 — 눈으로 본다

```bash
mkdir -p target/rp
curl -s -b cj -o target/rp/out.pdf "http://localhost:8080/api/reports/BUDGET_EXEC/export?format=PDF"
tools/render-pdf.sh target/rp/out.pdf 3 target/rp/page4.png     # 쪽 번호는 0부터
```

PNG 를 Read 로 열어 확인한다. `render-pdf.sh` 는 fat jar 의 라이브러리를 그대로 쓴다 —
클래스패스를 손으로 짜면 버전·경로 문제로 깨진다(`/tmp` 는 윈도우 자바가 못 읽고,
commons-logging 이 빠지면 `NoClassDefFoundError`). 파일 이름은 ASCII 로.

차트가 몰린 쪽:

| 리포트 | 쪽 | 차트 |
|---|---|---|
| BUDGET_EXEC | 마지막(4쪽, index 3) | 가로막대·도넛·꺾은선·100% 누적 |
| CIVIL_STATUS | 마지막(3쪽, index 2) | 세로막대·원·누적 가로막대 |
| CONTRACT_STATUS | 마지막(2쪽, index 1) | 가로막대·**로그 축** 꺾은선 |

볼 것: 축·항목 라벨 겹침/잘림, 범례 줄바꿈, 막대가 기준선에 붙었는지, 도넛 가운데 합계가 구멍 안인지.

## 5. 디자이너·뷰어·관리 화면을 고쳤으면 — 브라우저로 동작시킨다

- `node --check src/main/resources/static/js/designer.js` 로 문법부터. python heredoc 으로 JS 를 고치면
  `\n`·정규식 이스케이프가 실제 개행으로 바뀐 적이 있다 — 그런 수정은 Edit 도구나 별도 .py 파일로.
- claude-in-chrome 으로 로그인 → 화면 열기 → `javascript_tool` 로 이벤트를 보내고 DOM 을 확인.
- 재기동하면 세션이 끊긴다. 다시 로그인할 것.
- 인라인 `<script>` 는 CSP 에 막혀 **아무 오류 없이** 안 돈다. 함수가 `undefined` 면 이걸 의심.

## 6. 보안·권한 경로를 고쳤으면

권한이 **없는** 계정으로 직접 두드려 403 을 확인한다 (`viewer` 는 부서 1100).
목록에서 사라지는지와 주소 직접 입력이 막히는지는 별개로 본다.

## 7. 커밋·푸시

```bash
git add <바꾼 파일>        # docs/*.pdf, data/, logs/ 는 넣지 않는다
git commit                 # 영어. 제목 한 줄 + 왜 바꿨는지 본문
git push origin main
```

공개 저장소다. 개인키·실제 비밀번호·`license.key` 가 들어가지 않았는지 `git diff --cached` 로 본다.

끝나면 `CLAUDE.md` 의 "남은 일"과 해당 보고서를 갱신한다. 서버는 꺼 둔다.
