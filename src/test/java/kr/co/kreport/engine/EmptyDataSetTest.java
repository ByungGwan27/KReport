package kr.co.kreport.engine;

import kr.co.kreport.engine.layout.RenderedReport;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 조회를 아직 붙이지 않은 리포트도 저장하고 미리 볼 수 있어야 한다.
 *
 * <p>종이 위에 무엇을 어디에 놓을지 먼저 잡고 데이터는 나중에 연결하는 순서가 현장에서
 * 흔하다. 데이터셋이 있어야만 저장되면 그 순서로 일할 수 없다. 대신 <b>적혀 있는데 틀린
 * 경우</b>는 그대로 막아야 한다 &mdash; 오타 난 SQL 을 저장해 두면 실행하는 사람이 원인을
 * 짚기 어렵다.</p>
 */
@SpringBootTest
class EmptyDataSetTest {

    @Autowired
    private ReportEngine engine;

    @Test
    @DisplayName("SQL 을 아직 적지 않아도 검증을 통과한다")
    void blankSqlPassesValidation() {
        assertThat(TemplateValidator.collectErrors(template(""))).isEmpty();
    }

    @Test
    @DisplayName("데이터셋 정의 자체가 없어도 통과한다")
    void missingDataSetPassesValidation() {
        ReportTemplate t = template("");
        t.setDataSet(null);

        assertThat(TemplateValidator.collectErrors(t)).isEmpty();
    }

    @Test
    @DisplayName("빈 데이터셋으로 실행하면 머리말은 그려지고 본문만 비어 있다")
    void runsWithEmptyDataSet() {
        RenderedReport report = engine.run(template(""), Map.of());

        assertThat(report.getRowCount()).isZero();
        assertThat(report.getPageCount()).isEqualTo(1);
        // 제목은 데이터와 무관하게 찍혀야 배치를 확인할 수 있다
        assertThat(report.getPages().get(0).getElements())
                .extracting(e -> e.getText())
                .contains("집행 현황");
    }

    @Test
    @DisplayName("적어 놓은 SQL 이 틀리면 여전히 막는다")
    void brokenSqlIsStillRejected() {
        assertThat(TemplateValidator.collectErrors(template("delete from budget_execution")))
                .isNotEmpty();
    }

    private ReportTemplate template(String sql) {
        ReportTemplate t = new ReportTemplate();
        t.setReportId("DRAFT");
        t.setName("초안");
        t.setPage(new PageSetup());

        DataSetDef def = new DataSetDef();
        def.setSourceType(DataSetDef.SourceType.SQL);
        def.setSql(sql);
        t.setDataSet(def);

        ReportElement title = new ReportElement();
        title.setId("title");
        title.setType(ElementType.LABEL);
        title.setText("집행 현황");
        title.setX(0);
        title.setY(0);
        title.setWidth(200);
        title.setHeight(16);

        Band header = new Band();
        header.setType(BandType.REPORT_HEADER);
        header.setHeight(20);
        header.setElements(List.of(title));

        Band detail = new Band();
        detail.setType(BandType.DETAIL);
        detail.setHeight(16);
        detail.setElements(List.of());

        t.setBands(List.of(header, detail));
        return t;
    }
}
