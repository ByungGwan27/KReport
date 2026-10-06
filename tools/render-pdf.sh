#!/usr/bin/env bash
# PDF 한 쪽을 PNG 로 굽는다. 차트·레이아웃이 실제로 어떻게 찍혔는지 눈으로 보기 위한 것.
#
#   tools/render-pdf.sh <pdf> <쪽 번호(0부터)> <png>
#
# 실행할 때는 손으로 짠 클래스패스 대신 빌드된 fat jar 의 라이브러리를 그대로 쓴다.
# 버전 번호를 적어 두면 의존성이 오를 때마다 조용히 깨지기 때문이다 (실제로 깨졌다).
# 컴파일에만 pdfbox jar 가 필요해 로컬 메이븐 저장소에서 찾는다.
# 파일 이름은 ASCII 로 — Git Bash 에서 한글 인자는 자바로 넘어가며 깨진다.
set -euo pipefail
cd "$(dirname "$0")/.."

[ -f target/kreport-1.0.0.jar ] || mvn -o -q package -DskipTests

REPO="${HOME}/.m2/repository/org/apache/pdfbox"
PDFBOX=$(ls -1 "$REPO"/pdfbox/*/pdfbox-*.jar | grep -v sources | sort | tail -1)
PDFBOX_IO=$(ls -1 "$REPO"/pdfbox-io/*/pdfbox-io-*.jar | grep -v sources | sort | tail -1)

mkdir -p target/tools
javac -cp "$(cygpath -w "$PDFBOX");$(cygpath -w "$PDFBOX_IO")" -d target/tools tools/RenderPdf.java

java -cp target/kreport-1.0.0.jar \
     -Dloader.path=target/tools \
     -Dloader.main=RenderPdf \
     org.springframework.boot.loader.launch.PropertiesLauncher "$@" 2>&1 \
  | grep -v "Commons Logging discovery"
