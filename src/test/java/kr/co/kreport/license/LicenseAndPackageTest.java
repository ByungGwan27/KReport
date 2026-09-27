package kr.co.kreport.license;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import kr.co.kreport.deploy.ReportPackage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 라이선스와 배포 패키지의 서명이 실제로 위조를 막는지.
 *
 * <p>이 묶음이 지키는 것은 하나다 — <b>공개키만 가진 쪽은 라이선스를 새로 만들 수 없고,
 * 발급된 것을 고칠 수도 없다.</b> 대칭키를 썼다면 고객사 서버 안의 검증 키로 라이선스를
 * 위조할 수 있으므로, 비대칭이라는 선택이 실제로 값을 하는지 확인한다.</p>
 */
class LicenseAndPackageTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static KeyPair supplier;
    private static KeyPair impostor;

    @BeforeAll
    static void keys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(SignedEnvelope.ALGORITHM);
        supplier = generator.generateKeyPair();
        impostor = generator.generateKeyPair();
    }

    private String seal(License license) throws Exception {
        return SignedEnvelope.seal(MAPPER.writeValueAsBytes(license), supplier.getPrivate());
    }

    private License open(String sealed) throws Exception {
        return MAPPER.readValue(
                SignedEnvelope.open(sealed, supplier.getPublic()), License.class);
    }

    private License license(LocalDate expires, List<String> hosts, int maxReports) {
        return new License("○○시청", "KR-2026-001", LocalDate.of(2026, 1, 1),
                expires, hosts, maxReports, Set.of());
    }

    // ---------------------------------------------------------------- 라이선스

    @Test
    @DisplayName("발급한 라이선스를 공개키로 열면 내용이 그대로 나온다")
    void issuedLicenseRoundTrips() throws Exception {
        License issued = license(LocalDate.of(2027, 12, 31), List.of("report.city.go.kr"), 50);

        License read = open(seal(issued));

        assertThat(read.licensee()).isEqualTo("○○시청");
        assertThat(read.licenseId()).isEqualTo("KR-2026-001");
        assertThat(read.expiresOn()).isEqualTo(LocalDate.of(2027, 12, 31));
        assertThat(read.maxReports()).isEqualTo(50);
    }

    @Test
    @DisplayName("만료일을 늘려 고치면 서명이 깨진다 - 가장 해 보고 싶은 위조다")
    void tamperingWithExpiryBreaksSignature() throws Exception {
        String sealed = seal(license(LocalDate.of(2026, 12, 31), List.of(), 0));

        // 본문을 풀어 만료일만 바꿔치기한 뒤 원래 서명을 그대로 붙인다
        int dot = sealed.lastIndexOf('.');
        String body = new String(java.util.Base64.getDecoder().decode(sealed.substring(0, dot)));
        String forgedBody = body.replace("2026-12-31", "2099-12-31");
        String forged = java.util.Base64.getEncoder().withoutPadding()
                .encodeToString(forgedBody.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                + sealed.substring(dot);

        assertThatThrownBy(() -> SignedEnvelope.open(forged, supplier.getPublic()))
                .isInstanceOf(LicenseException.class)
                .hasMessageContaining("변조");
    }

    @Test
    @DisplayName("남의 개인키로 발급한 라이선스는 통하지 않는다")
    void licenseFromAnotherKeyIsRejected() throws Exception {
        String forged = SignedEnvelope.seal(
                MAPPER.writeValueAsBytes(license(null, List.of(), 0)), impostor.getPrivate());

        assertThatThrownBy(() -> SignedEnvelope.open(forged, supplier.getPublic()))
                .isInstanceOf(LicenseException.class);
    }

    @Test
    @DisplayName("공개키만으로는 새 라이선스를 만들 수 없다 - 비대칭을 고른 이유")
    void publicKeyCannotSign() {
        assertThatThrownBy(() -> SignedEnvelope.seal("아무 내용".getBytes(),
                // 공개키를 개인키 자리에 넣어 보는 것이 가장 단순한 위조 시도다
                SignedEnvelope.privateKey(
                        SignedEnvelope.encodeKey(supplier.getPublic().getEncoded()))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("잘린 파일은 형식 단계에서 걸러진다")
    void truncatedFileIsRejected() throws Exception {
        String sealed = seal(license(null, List.of(), 0));

        assertThatThrownBy(() -> SignedEnvelope.open(sealed.substring(0, sealed.length() / 2),
                supplier.getPublic()))
                .isInstanceOf(LicenseException.class);
    }

    @Test
    @DisplayName("메일로 주고받다 줄바꿈이 섞여도 열린다")
    void whitespaceIsTolerated() throws Exception {
        String sealed = seal(license(null, List.of(), 0));
        String wrapped = sealed.replaceAll("(.{40})", "$1\n");

        assertThat(open(wrapped).licensee()).isEqualTo("○○시청");
    }

    // ---------------------------------------------------------------- 기간·호스트 판단

    @Test
    @DisplayName("만료 당일까지는 쓸 수 있다")
    void validThroughTheLastDay() {
        License l = license(LocalDate.of(2026, 12, 31), List.of(), 0);

        assertThat(l.isExpired(LocalDate.of(2026, 12, 31))).isFalse();
        assertThat(l.isExpired(LocalDate.of(2027, 1, 1))).isTrue();
    }

    @Test
    @DisplayName("호스트를 적지 않으면 어디서나 - 개발·평가용")
    void emptyHostsMeansAnywhere() {
        assertThat(license(null, List.of(), 0).allowsHost("어디든")).isTrue();
        assertThat(license(null, List.of("a.go.kr"), 0).allowsHost("b.go.kr")).isFalse();
        assertThat(license(null, List.of("A.GO.KR"), 0).allowsHost("a.go.kr")).isTrue();
    }

    // ---------------------------------------------------------------- 배포 패키지

    @Test
    @DisplayName("패키지는 평문으로 읽히지 않는다 - 편집기로 열어도 SQL 이 보이지 않는다")
    void packageIsNotReadableAsText() throws Exception {
        String templateJson = """
                {"reportId":"SECRET","dataSet":{"sql":"select resident_no from citizen"}}""";
        ReportPackage content = new ReportPackage(1, "SECRET", "대외비",
                templateJson, java.time.LocalDateTime.now(), "builder", "KR-2026-001");

        byte[] payload = deflate(MAPPER.writeValueAsBytes(content));
        String packaged = ReportPackage.MAGIC + "." + SignedEnvelope.seal(payload, supplier.getPrivate());

        assertThat(packaged)
                .doesNotContain("resident_no")
                .doesNotContain("select")
                .startsWith("KRPT1.");
    }

    @Test
    @DisplayName("패키지를 한 글자라도 고치면 열리지 않는다")
    void tamperedPackageIsRejected() throws Exception {
        ReportPackage content = new ReportPackage(1, "R1", "리포트",
                "{\"reportId\":\"R1\"}", java.time.LocalDateTime.now(), "builder", "KR-1");
        byte[] payload = deflate(MAPPER.writeValueAsBytes(content));
        String sealed = SignedEnvelope.seal(payload, supplier.getPrivate());

        // 본문 한가운데 한 글자를 바꾼다
        char[] chars = sealed.toCharArray();
        int at = sealed.length() / 3;
        chars[at] = chars[at] == 'A' ? 'B' : 'A';

        assertThatThrownBy(() -> SignedEnvelope.open(new String(chars), supplier.getPublic()))
                .isInstanceOf(LicenseException.class);
    }

    private static byte[] deflate(byte[] raw) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try (java.util.zip.DeflaterOutputStream s = new java.util.zip.DeflaterOutputStream(out)) {
            s.write(raw);
        }
        return out.toByteArray();
    }
}
