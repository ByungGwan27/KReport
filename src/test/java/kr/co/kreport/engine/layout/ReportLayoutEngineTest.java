package kr.co.kreport.engine.layout;

import kr.co.kreport.engine.data.DataTable;
import kr.co.kreport.template.Band;
import kr.co.kreport.template.BandType;
import kr.co.kreport.template.ElementType;
import kr.co.kreport.template.GroupDef;
import kr.co.kreport.template.HorizontalAlign;
import kr.co.kreport.template.ReportElement;
import kr.co.kreport.template.ReportTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReportLayoutEngineTest {

    private final ReportLayoutEngine engine = new ReportLayoutEngine();

    // ---------------------------------------------------------------- 픽스처

    private DataTable data(int rowsPerDept, String... depts) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String dept : depts) {
            for (int i = 1; i <= rowsPerDept; i++) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("DEPT", dept);
                row.put("AMT", BigDecimal.valueOf(1000L * i));
                rows.add(row);
            }
        }
        return new DataTable(List.of("DEPT", "AMT"), rows);
    }

    private ReportElement text(String id, String expression, double x, double width) {
        ReportElement e = new ReportElement();
        e.setId(id);
        e.setType(ElementType.TEXT);
        e.setExpression(expression);
        e.setX(x);
        e.setY(0);
        e.setWidth(width);
        e.setHeight(14);
        e.getStyle().setAlign(HorizontalAlign.LEFT);
        return e;
    }

    private Band band(BandType type, double height, ReportElement... elements) {
        Band band = new Band();
        band.setType(type);
        band.setHeight(height);
        for (ReportElement e : elements) {
            band.add(e);
        }
        return band;
    }

    private ReportTemplate template() {
        ReportTemplate t = new ReportTemplate();
        t.setReportId("TEST");
        t.setName("테스트 리포트");
        return t;
    }

    private List<String> texts(RenderedReport report) {
        List<String> out = new ArrayList<>();
        for (RenderedPage page : report.getPages()) {
            for (RenderedElement e : page.getElements()) {
                if (e.isTextual() && e.getText() != null && !e.getText().isEmpty()) {
                    out.add(e.getText());
                }
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- 테스트

    @Test
    @DisplayName("본문 밴드가 페이지를 넘기며 행을 채운다")
    void paginatesDetailRows() {
        ReportTemplate t = template();
        t.getBands().add(band(BandType.DETAIL, 20, text("v", "{AMT}", 0, 100)));

        // 본문 영역 높이 = 841.89 - 36 - 36 = 769.89 -> 20pt 밴드 38행/쪽
        RenderedReport report = engine.layout(t, data(50, "A"), Map.of());

        assertThat(report.getRowCount()).isEqualTo(50);
        assertThat(report.getPageCount()).isEqualTo(2);
        assertThat(report.getPages().get(0).getElements()).hasSize(38);
        assertThat(report.getPages().get(1).getElements()).hasSize(12);
    }

    @Test
    @DisplayName("그룹이 바뀌면 머리말과 꼬리말이 끼어든다")
    void emitsGroupBands() {
        ReportTemplate t = template();
        GroupDef group = new GroupDef();
        group.setName("dept");
        group.setExpression("{DEPT}");
        t.getGroups().add(group);

        Band header = band(BandType.GROUP_HEADER, 16, text("gh", "{DEPT}", 0, 100));
        header.setGroupName("dept");
        Band footer = band(BandType.GROUP_FOOTER, 16, text("gf", "'소계 ' + SUM({AMT},'dept')", 0, 200));
        footer.setGroupName("dept");

        t.getBands().add(header);
        t.getBands().add(band(BandType.DETAIL, 16, text("v", "{AMT}", 0, 100)));
        t.getBands().add(footer);

        RenderedReport report = engine.layout(t, data(3, "복지정책과", "환경관리과"), Map.of());
        List<String> out = texts(report);

        assertThat(out).containsSubsequence(
                "복지정책과", "1000", "2000", "3000", "소계 6000",
                "환경관리과", "1000", "2000", "3000", "소계 6000");
    }

    @Test
    @DisplayName("전체 합계는 첫 페이지에서도 확정된 값이 찍힌다")
    void reportLevelAggregateIsAvailableUpFront() {
        ReportTemplate t = template();
        t.getBands().add(band(BandType.REPORT_HEADER, 20, text("total", "'총액 ' + SUM({AMT})", 0, 200)));
        t.getBands().add(band(BandType.DETAIL, 16, text("v", "{AMT}", 0, 100)));

        RenderedReport report = engine.layout(t, data(3, "A", "B"), Map.of());

        // 1000+2000+3000 을 두 부서 => 12000
        assertThat(texts(report)).first().isEqualTo("총액 12000");
    }

    @Test
    @DisplayName("전체 페이지 수는 마지막까지 흘려 본 뒤 꼬리말에 반영된다")
    void pageCountIsResolvedAfterFlow() {
        ReportTemplate t = template();
        t.getBands().add(band(BandType.DETAIL, 20, text("v", "{AMT}", 0, 100)));
        t.getBands().add(band(BandType.PAGE_FOOTER, 20,
                text("pn", "PAGE_NO + ' / ' + PAGE_COUNT", 0, 100)));

        RenderedReport report = engine.layout(t, data(60, "A"), Map.of());

        List<String> footers = new ArrayList<>();
        for (RenderedPage page : report.getPages()) {
            page.getElements().stream()
                    .filter(e -> "pn".equals(e.getElementId()))
                    .forEach(e -> footers.add(e.getText()));
        }
        assertThat(footers).containsExactly("1 / 2", "2 / 2");
    }

    @Test
    @DisplayName("그룹에 페이지 나눔을 걸면 그룹마다 새 쪽에서 시작한다")
    void groupPageBreak() {
        ReportTemplate t = template();
        GroupDef group = new GroupDef();
        group.setName("dept");
        group.setExpression("{DEPT}");
        group.setPageBreak(true);
        t.getGroups().add(group);

        Band header = band(BandType.GROUP_HEADER, 16, text("gh", "{DEPT}", 0, 100));
        header.setGroupName("dept");
        t.getBands().add(header);
        t.getBands().add(band(BandType.DETAIL, 16, text("v", "{AMT}", 0, 100)));

        RenderedReport report = engine.layout(t, data(2, "A", "B", "C"), Map.of());

        assertThat(report.getPageCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("반복값 제거를 켠 컬럼은 같은 값이 이어지면 비워 둔다")
    void suppressRepeat() {
        ReportTemplate t = template();
        ReportElement dept = text("dept", "{DEPT}", 0, 100);
        dept.setSuppressRepeat(true);
        t.getBands().add(band(BandType.DETAIL, 16, dept));

        RenderedReport report = engine.layout(t, data(3, "복지정책과"), Map.of());

        List<String> printed = new ArrayList<>();
        for (RenderedElement e : report.getPages().get(0).getElements()) {
            printed.add(e.getText());
        }
        assertThat(printed).containsExactly("복지정책과", "", "");
    }

    @Test
    @DisplayName("데이터가 없어도 머리말과 꼬리말은 출력된다")
    void emptyData() {
        ReportTemplate t = template();
        t.getBands().add(band(BandType.REPORT_HEADER, 20, text("h", "'조회 결과 없음'", 0, 200)));
        t.getBands().add(band(BandType.DETAIL, 16, text("v", "{AMT}", 0, 100)));

        RenderedReport report = engine.layout(t, DataTable.empty(), Map.of());

        assertThat(report.getPageCount()).isEqualTo(1);
        assertThat(texts(report)).containsExactly("조회 결과 없음");
    }

    @Test
    @DisplayName("렌더된 요소는 출처 밴드를 기억한다")
    void keepsBandOrigin() {
        ReportTemplate t = template();
        t.getBands().add(band(BandType.REPORT_HEADER, 20, text("h", "'머리말'", 0, 200)));
        t.getBands().add(band(BandType.DETAIL, 16, text("v", "{AMT}", 0, 100)));

        RenderedReport report = engine.layout(t, data(2, "A"), Map.of());
        List<RenderedElement> elements = report.getPages().get(0).getElements();

        assertThat(elements.get(0).getBandType()).isEqualTo(BandType.REPORT_HEADER);
        assertThat(elements.get(1).getBandType()).isEqualTo(BandType.DETAIL);
    }
}
