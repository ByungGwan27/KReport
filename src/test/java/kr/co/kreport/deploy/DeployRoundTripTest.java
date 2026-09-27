package kr.co.kreport.deploy;

import kr.co.kreport.access.Viewer;
import kr.co.kreport.config.ReportRole;
import kr.co.kreport.service.ReportDefinitionService;
import kr.co.kreport.support.KReportException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 공급사에서 굽고 고객사에서 설치하는 왕복.
 *
 * <p>키 한 쌍을 시험용으로 넣어 두 역할을 한 서버에서 흉내 낸다. 실제로는 개인키가
 * 공급사 빌드 환경에만 있고, 고객사 배포본에는 공개키만 들어간다.</p>
 *
 * <p><b>아래 키는 이 시험에서만 쓰는 값이다.</b> 저장소에 그대로 적혀 있으므로 어떤
 * 배포본도 이 공개키를 쓰면 안 된다. {@code application.yml} 의 기본값을 비워 둔 것도
 * 같은 이유다 — 짝이 공개된 검증키를 쓰면 누구나 라이선스를 발급할 수 있다.</p>
 */
@SpringBootTest
@TestPropertySource(properties = {
        "kreport.license.public-key=MCowBQYDK2VwAyEA/8uEL9PANtdUaiFiAsDA9J/mFhVJpjOuoOI3d//7Sbg",
        "kreport.license.private-key=MC4CAQAwBQYDK2VwBCIEIMUmLidf/2n8VL1mgKh1mD9PYjqXGnvj6wstoIKzGhT3"
})
class DeployRoundTripTest {

    private static final String REPORT_ID = "BUDGET_EXEC";

    @Autowired
    private DeployService deployService;

    @Autowired
    private ReportPackager packager;

    @Autowired
    private ReportDefinitionService definitionService;

    private final Viewer admin = Viewer.of("admin", Set.of(ReportRole.ADMIN));

    @Test
    @DisplayName("구운 패키지를 다시 설치하면 같은 리포트가 돌아온다")
    void buildThenInstall() {
        DeployService.Built built = deployService.build(REPORT_ID, admin);

        assertThat(built.fileName()).isEqualTo("BUDGET_EXEC.krpt");
        assertThat(built.body()).startsWith("KRPT1.");

        // 고객사 쪽에서 설치
        assertThat(deployService.install(built.body(), admin).getReportId()).isEqualTo(REPORT_ID);

        // 설치된 정의로 실제 리포트가 그려지는지
        assertThat(definitionService.loadTemplate(REPORT_ID).getBands()).isNotEmpty();
    }

    @Test
    @DisplayName("패키지에 원본 SQL 이 평문으로 남지 않는다")
    void packagedBodyHidesSql() {
        String body = deployService.build(REPORT_ID, admin).body();

        assertThat(body)
                .doesNotContain("select")
                .doesNotContain("budget_execution")
                .doesNotContain("reportId");
    }

    @Test
    @DisplayName("패키지를 고치면 설치가 거부된다")
    void tamperedPackageCannotBeInstalled() {
        String body = deployService.build(REPORT_ID, admin).body();
        char[] chars = body.toCharArray();
        int at = body.length() / 2;
        chars[at] = chars[at] == 'A' ? 'B' : 'A';

        assertThatThrownBy(() -> deployService.install(new String(chars), admin))
                .isInstanceOf(KReportException.class);
    }

    @Test
    @DisplayName("KReport 패키지가 아닌 파일은 바로 걸러진다")
    void foreignFileIsRejected() {
        assertThatThrownBy(() -> deployService.install("그냥 텍스트 파일입니다", admin))
                .hasMessageContaining("KReport 패키지 파일이 아닙니다");
    }

    @Test
    @DisplayName("굽는 쪽에는 개인키가 있어야 한다")
    void buildingNeedsPrivateKey() {
        assertThat(packager.canBuild()).isTrue();
    }

    @Test
    @DisplayName("패키지에 누가 언제 구웠는지 남는다 - 납품 이력 추적")
    void packageCarriesProvenance() {
        ReportPackage content = packager.open(deployService.build(REPORT_ID, admin).body());

        assertThat(content.builtBy()).isEqualTo("admin");
        assertThat(content.reportId()).isEqualTo(REPORT_ID);
        assertThat(content.builtAt()).isNotNull();
        assertThat(content.formatVersion()).isEqualTo(ReportPackage.FORMAT_VERSION);
    }
}
