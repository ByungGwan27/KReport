package kr.co.kreport.engine.data;

import kr.co.kreport.access.Viewer;
import kr.co.kreport.config.ReportRole;
import kr.co.kreport.service.ReportDefinitionService;
import kr.co.kreport.template.Band;
import kr.co.kreport.template.BandType;
import kr.co.kreport.template.DataSetDef;
import kr.co.kreport.template.ElementType;
import kr.co.kreport.template.PageSetup;
import kr.co.kreport.template.ReportElement;
import kr.co.kreport.template.ReportTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 거래처 DB 를 따로 붙이고, 거래처 편집자가 우리 DB 로 넘어오지 못하게 막는다.
 *
 * <p>이 묶음이 지키는 것은 하나다 — <b>편집 권한은 곧 임의 SELECT 를 쓸 권한</b>이므로,
 * 밖의 사람에게 편집을 열어 줄 때 그 질의가 어느 DB 로 가는지까지 정해 두지 않으면
 * 업무 DB 를 통째로 내주는 것과 같다. 연결을 늘리는 일과 접근을 나누는 일은 한 묶음이다.</p>
 *
 * <p>거래처 DB 는 이 시험 안에서만 쓰는 별도 H2 인메모리로 만든다. 이름이 다르면 서로
 * 다른 DB 라, 한쪽 표를 다른 쪽에서 읽으면 조회가 실패한다.</p>
 */
@SpringBootTest
@TestPropertySource(properties = {
        "kreport.datasources.partner-a.url=jdbc:h2:mem:partnerdb;DB_CLOSE_DELAY=-1;INIT=create table if not exists partner_order(order_no varchar(20) primary key\\\\, amount int)\\\\;insert into partner_order values('PO-1'\\\\, 1000)",
        "kreport.datasources.partner-a.username=sa",
        "kreport.datasources.partner-a.password=",
        "kreport.datasources.partner-a.writers.users=partner1",
        // 거래처를 붙였으므로 우리 DB 는 내부 편집자에게만 연다
        "kreport.security.main-writers.roles=INHOUSE"
})
class DataSourceIsolationTest {

    @Autowired
    private DataSourceRegistry registry;

    @Autowired
    private JdbcDataSetProvider provider;

    @Autowired
    private ReportDefinitionService definitionService;

    /** 거래처 편집자. 편집 권한은 있지만 거래처 DB 에만 쓸 수 있다. */
    private final Viewer partner =
            Viewer.of("partner1", Set.of(ReportRole.VIEWER, ReportRole.DESIGNER));

    /** 내부 편집자 */
    private final Viewer inhouse =
            Viewer.of("staff", Set.of(ReportRole.VIEWER, ReportRole.DESIGNER, "INHOUSE"));

    @Test
    @DisplayName("거래처 DB 가 따로 붙고 그쪽 표를 읽는다")
    void partnerDatabaseIsReachable() {
        DataSetDef def = new DataSetDef();
        def.setDataSource("partner-a");
        def.setSql("select order_no, amount from partner_order");

        DataTable table = provider.fetch(def, Map.of());

        assertThat(table.size()).isEqualTo(1);
        assertThat(table.row(0).get("ORDER_NO")).isEqualTo("PO-1");
    }

    @Test
    @DisplayName("거래처 DB 에는 우리 표가 없다 - 정말 다른 DB 다")
    void partnerDatabaseCannotSeeOurTables() {
        DataSetDef def = new DataSetDef();
        def.setDataSource("partner-a");
        def.setSql("select * from civil_complaint");

        assertThatThrownBy(() -> provider.fetch(def, Map.of()))
                .isInstanceOf(DataSetException.class);
    }

    @Test
    @DisplayName("거래처 편집자는 자기 DB 에만 쿼리를 쓸 수 있다")
    void partnerCanWriteOnlyToItsOwnDatabase() {
        assertThat(registry.canWrite("partner-a", partner)).isTrue();
        assertThat(registry.canWrite("main", partner)).isFalse();
        assertThat(registry.writableBy(partner)).containsExactly("partner-a");
    }

    @Test
    @DisplayName("거래처가 데이터소스 이름만 바꿔 우리 DB 를 읽으려 하면 저장이 거부된다")
    void partnerCannotSaveDefinitionAgainstMain() {
        ReportTemplate hostile = template("main", "select applicant_name from civil_complaint");

        assertThatThrownBy(() -> definitionService.save("PARTNER_PROBE", hostile, "테스트", partner))
                .hasMessageContaining("권한이 없습니다");
    }

    @Test
    @DisplayName("데이터소스를 적지 않으면 우리 DB 로 가므로 역시 거부된다")
    void omittedDataSourceDefaultsToMainAndIsRejected() {
        ReportTemplate hostile = template(null, "select applicant_name from civil_complaint");
        hostile.getDataSet().setDataSource(null);

        assertThatThrownBy(() -> definitionService.save("PARTNER_PROBE2", hostile, "테스트", partner))
                .hasMessageContaining("권한이 없습니다");
    }

    @Test
    @DisplayName("거래처는 자기 DB 를 향한 정의는 저장할 수 있다")
    void partnerCanSaveAgainstItsOwnDatabase() {
        ReportTemplate own = template("partner-a", "select order_no from partner_order");

        assertThat(definitionService.save("PARTNER_OK", own, "거래처", partner).getReportId())
                .isEqualTo("PARTNER_OK");
    }

    @Test
    @DisplayName("내부 편집자는 우리 DB 를 쓰고 거래처 DB 는 못 쓴다")
    void inhouseEditorIsScopedToMain() {
        assertThat(registry.canWrite("main", inhouse)).isTrue();
        assertThat(registry.canWrite("partner-a", inhouse)).isFalse();
    }

    @Test
    @DisplayName("관리자는 모든 데이터소스를 쓴다 - 설정이 맞는지 확인할 사람이 필요하다")
    void adminCanWriteEverywhere() {
        Viewer admin = Viewer.of("admin", Set.of(ReportRole.ADMIN));

        assertThat(registry.writableBy(admin)).containsExactly("main", "partner-a");
    }

    @Test
    @DisplayName("없는 데이터소스를 가리키면 쓸 수 있는 이름을 알려 준다")
    void unknownDataSourceIsRejectedWithHint() {
        DataSetDef def = new DataSetDef();
        def.setDataSource("partner-z");
        def.setSql("select 1");

        assertThatThrownBy(() -> provider.fetch(def, Map.of()))
                .hasMessageContaining("partner-z")
                .hasMessageContaining("partner-a");
    }

    private ReportTemplate template(String dataSource, String sql) {
        ReportTemplate t = new ReportTemplate();
        t.setName("탐색");
        t.setPage(new PageSetup());

        DataSetDef def = new DataSetDef();
        def.setSourceType(DataSetDef.SourceType.SQL);
        if (dataSource != null) {
            def.setDataSource(dataSource);
        }
        def.setSql(sql);
        t.setDataSet(def);

        ReportElement e = new ReportElement();
        e.setId("a");
        e.setType(ElementType.TEXT);
        e.setExpression("'x'");
        e.setX(0);
        e.setY(0);
        e.setWidth(100);
        e.setHeight(14);

        Band detail = new Band();
        detail.setType(BandType.DETAIL);
        detail.setHeight(16);
        detail.setElements(List.of(e));
        t.setBands(List.of(detail));
        return t;
    }
}
