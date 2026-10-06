# KReport — 작업 인수인계

공공기관용 **밴드 기반 웹 리포팅 엔진** (UBIReport 계열). 정의(JSON) + 조회 조건 → 쪽 좌표 확정 →
HTML/PDF/XLSX/CSV. 웹 디자이너·뷰어·열람 권한·다중 DB·서명 패키지·라이선스까지 있다.
사용자 문서는 `README.md`(설치·개발), `docs/USER-GUIDE.md`(최종 사용자).

## 작업 규칙

- 답변·설명은 한국어, **커밋 메시지는 영어**. 무엇을 왜 바꿨는지 본문에 적는다.
- 작업 단위가 끝날 때마다 **테스트 → 커밋 → `git push origin main`**. 원격: `github.com/ByungGwan27/KReport` (**공개 저장소**)
- 코드 주석은 한국어, "무엇"이 아니라 **"왜"** 를 적는 문체. 주변 코드 밀도에 맞춘다.
- 동작 확인은 테스트만으로 끝내지 않는다 → `.claude/skills/kreport-verify` 참고.

## 명령

```bash
mvn -o test                                  # 테스트 (현재 176건, 전부 통과해야 함)
mvn -o clean compile                         # 시그니처를 바꿨으면 반드시 clean — 증분 컴파일이 타입 오류를 놓친 적 있음
mvn spring-boot:run                          # 기본: H2 인메모리, 껐다 켜면 초기화
mvn spring-boot:run -Dspring-boot.run.profiles=local   # ./data 에 H2 파일, 작업물 유지
run.bat [local]                              # 윈도우. 어느 폴더에서 불러도 됨, 콘솔 UTF-8
```

로그인(개발용): `viewer / viewer1234!`, `designer / designer1234!`, `admin / admin1234!`
로그는 `logs/kreport.log` (UTF-8). 콘솔은 CP949라 `mvn spring-boot:run` 출력 한글이 깨질 수 있음 — 정상.

## 구조 (src/main/java/kr/co/kreport)

| 패키지 | 내용 |
|---|---|
| `template/` | 정의 모델 (Jackson 직렬화 대상) |
| `engine/expression/` | **자체** 재귀하강 파서. SpEL 금지(임의 코드 실행 통로). 함수는 `FunctionLibrary` SPI |
| `engine/layout/` | 두 번 훑는 레이아웃 (본문 → 쪽 수 확정 → 머리말·꼬리말) |
| `engine/chart/` | `ChartLayoutEngine`(자리 나누기) → `Cartesian/CircularRenderer` → 마크 렌더러. 치수는 `ChartStyle`, 좌표는 `Plot` |
| `engine/data/` | `DataSourceRegistry`(다중 DB + 작성 권한), `SqlGuard`(SELECT만) |
| `export/` | `RenderedPage` 만 본다. 밴드·표현식을 모른다 ← **이 경계를 깨지 말 것** |
| `access/` | 리포트별 열람 권한, `ViewerDirectory` SPI(소속 부서) |
| `license/`, `deploy/` | Ed25519 서명 라이선스, `.krpt` 패키지, 임베드 |
| `support/` | `ErrorCode` + `KReportException` (화면에 나가도 되는 오류만 이 타입) |

## 설계상 지켜야 할 것

- **편집 권한 = DB 열람 권한.** 정의에 임의 SELECT 를 쓸 수 있으므로. 그래서 DESIGNER 는 열람 통제에서 면제(막아도 우회됨). 데이터소스 작성 권한은 **저장·`/preview`·`/fields` 세 곳**에서 검사.
- 열람 검사는 목록·실행·내보내기·메타·**정의 원본**·화면 전부. 경로 하나 빠지면 통제가 없는 것과 같다.
- `loadTemplate()` 은 캐시 인스턴스를 그대로 준다(속도). 실행 경로에서 템플릿을 고치면 안 됨 — `TemplateCacheContractTest` 가 지킴. 밖으로 나가는 건 `loadEditableTemplate()`.
- 라이선스는 **비대칭**. `application.yml` 의 `public-key` 는 일부러 비워 둠 — `DeployRoundTripTest` 에 시험용 개인키가 공개돼 있어, 그 짝을 기본값으로 두면 누구나 위조 가능.
- 차트: 계열 7개 초과·원 그래프 다계열·막대+로그축은 저장 시 거부. 이중 축은 넣지 않기로 결정.
- 화면 스크립트는 **인라인 금지**(CSP `script-src 'self'`). `static/js/*.js` 로.
- 배치 파일(`.bat`)은 **ASCII 전용**. cmd 가 CP949로 읽어 한글 주석이 명령으로 실행된 적 있음.
- `@ConditionalOnMissingBean` 은 `@Component` 에 안 먹는다 → `@Bean` 메서드로 (`AccessConfig` 참고).
- `FunctionRegistry` 는 정적 + 기동 후 freeze. 테스트에서는 `@BeforeEach` 에서도 `resetForTest()`.

## 남은 일 (우선순위 순)

1. **`ValueFormatter` 의 `DecimalFormat` 공유** — thread-safe 아님. 재현 실패했지만 터지면 금액이 조용히 틀림. 캐시에서 꺼낼 때 `clone()`. `ValueFormatterConcurrencyTest` 가 이미 있음.
2. **조회 상한 5만 행 실측** — 근거 없는 숫자. `RenderedElement` 전부 힙에 올라감. 시간·힙 재고 상한 조정 또는 스트리밍.
3. **`ExpressionParser.CACHE` 무제한** — 상한/만료.
4. 기능: 행 수준 보안, 가변 높이 밴드, 서브리포트·크로스탭, 비동기 대용량 출력.
5. 결정 대기: 설치형 전환(검토 문서 있음, A안 = jpackage + 트레이, 1~2주), 라이선스 머신 바인딩 여부, 코드서명 인증서.

## 문서 (Claude Docs)

- 개발 경과 보고: https://claude.ai/code/artifact/89207c5c-e66a-4f5f-a0b9-072458292f31
- 설치형 전환 검토: https://claude.ai/code/artifact/1ccacbc5-ee40-42b8-8bb3-9a748d4440cf
- 과제 제출용 보고서: https://claude.ai/code/artifact/1891807c-5c9e-42dd-aff2-f32cb207c3c9

PDF 가 필요하면 Claude Docs `export` (format=pdf, paper=a4). 결과가 커서 파일로 떨어지므로
JSON 의 `data.bytes_b64` 를 base64 디코딩해 저장한다.

## 도구

- `tools/render-pdf.sh <pdf> <쪽> <png>` — PDF 쪽을 PNG 로. 차트 확인용.
- `.claude/skills/kreport-verify` — 변경 검증 절차 (정리 → 테스트 → 기동 → 실물 확인 → 커밋).

작업이 끝나면 위 문서 중 해당하는 것과 이 파일의 "남은 일"을 함께 갱신한다.
