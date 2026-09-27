package kr.co.kreport.export;

import kr.co.kreport.config.KReportProperties;
import kr.co.kreport.engine.data.DataTable;
import kr.co.kreport.engine.layout.RenderedReport;
import kr.co.kreport.engine.layout.ReportLayoutEngine;
import kr.co.kreport.template.Band;
import kr.co.kreport.template.ChartAggregation;
import kr.co.kreport.template.ChartSeriesDef;
import kr.co.kreport.template.ChartSpec;
import kr.co.kreport.template.ChartType;
import kr.co.kreport.template.BandType;
import kr.co.kreport.template.ElementType;
import kr.co.kreport.template.GroupDef;
import kr.co.kreport.template.HorizontalAlign;
import kr.co.kreport.template.ReportElement;
import kr.co.kreport.template.ReportTemplate;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExporterTest {

    private final ReportLayoutEngine engine = new ReportLayoutEngine();
    private RenderedReport report;

    @BeforeEach
    void setUp() {
        report = engine.layout(template(), data(), Map.of("year", 2026));
    }

    // ---------------------------------------------------------------- 픽스처

    private DataTable data() {
        List<Map<String, Object>> rows = new ArrayList<>();
        Object[][] source = {
                {"복지정책과", "기초연금 지급", new BigDecimal("120000000"), new BigDecimal("0.85")},
                {"복지정책과", "아동수당 지급", new BigDecimal("80000000"), new BigDecimal("1.00")},
                {"환경관리과", "생활폐기물 처리", new BigDecimal("45000000"), new BigDecimal("0.60")},
        };
        for (Object[] s : source) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("DEPT", s[0]);
            row.put("PROGRAM", s[1]);
            row.put("AMT", s[2]);
            row.put("RATE", s[3]);
            rows.add(row);
        }
        return new DataTable(List.of("DEPT", "PROGRAM", "AMT", "RATE"), rows);
    }

    private ReportElement element(ElementType type, String id, double x, double width) {
        ReportElement e = new ReportElement();
        e.setId(id);
        e.setType(type);
        e.setX(x);
        e.setY(0);
        e.setWidth(width);
        e.setHeight(14);
        return e;
    }

    private ReportElement label(String id, String text, double x, double width) {
        ReportElement e = element(ElementType.LABEL, id, x, width);
        e.setText(text);
        e.getStyle().setAlign(HorizontalAlign.CENTER);
        return e;
    }

    private ReportElement text(String id, String expression, double x, double width, String format) {
        ReportElement e = element(ElementType.TEXT, id, x, width);
        e.setExpression(expression);
        e.setFormat(format);
        return e;
    }

    private ReportTemplate template() {
        ReportTemplate t = new ReportTemplate();
        t.setReportId("EXPORT_TEST");
        t.setName("수출 검증 리포트");

        GroupDef group = new GroupDef();
        group.setName("dept");
        group.setExpression("{DEPT}");
        t.getGroups().add(group);

        Band header = new Band();
        header.setType(BandType.PAGE_HEADER);
        header.setHeight(40);
        header.add(label("h_title", "부서별 집행 내역", 0, 300));
        ReportElement hp = label("h_program", "사업명", 0, 200);
        hp.setY(20);
        ReportElement ha = label("h_amt", "집행액", 200, 100);
        ha.setY(20);
        ReportElement hr = label("h_rate", "집행률", 300, 60);
        hr.setY(20);
        header.add(hp);
        header.add(ha);
        header.add(hr);

        Band groupHeader = new Band();
        groupHeader.setType(BandType.GROUP_HEADER);
        groupHeader.setGroupName("dept");
        groupHeader.setHeight(16);
        groupHeader.add(text("g_dept", "{DEPT}", 0, 200, null));

        Band detail = new Band();
        detail.setType(BandType.DETAIL);
        detail.setHeight(16);
        detail.add(text("d_program", "{PROGRAM}", 0, 200, null));
        ReportElement amt = text("d_amt", "{AMT}", 200, 100, "#,##0");
        amt.getStyle().setAlign(HorizontalAlign.RIGHT);
        ReportElement rate = text("d_rate", "{RATE}", 300, 60, "0%");
        rate.getStyle().setAlign(HorizontalAlign.RIGHT);
        detail.add(amt);
        detail.add(rate);

        Band groupFooter = new Band();
        groupFooter.setType(BandType.GROUP_FOOTER);
        groupFooter.setGroupName("dept");
        groupFooter.setHeight(16);
        groupFooter.add(text("gf_sum", "SUM({AMT},'dept')", 200, 100, "#,##0"));

        t.getBands().add(header);
        t.getBands().add(groupHeader);
        t.getBands().add(detail);
        t.getBands().add(groupFooter);
        return t;
    }

    private byte[] export(ReportExporter exporter) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.export(report, out);
        return out.toByteArray();
    }

    // ---------------------------------------------------------------- 표 복원

    @Test
    @DisplayName("표로 되뽑을 때 본문 행만 남고 머리말과 소계는 빠진다")
    void tabularExtractionTakesDetailOnly() {
        TabularExtractor.Table table = TabularExtractor.extract(report);

        assertThat(table.columns()).extracting(TabularExtractor.Column::title)
                .containsExactly("사업명", "집행액", "집행률");
        assertThat(table.rows()).hasSize(3);
        assertThat(table.rows().get(0).get(0).getText()).isEqualTo("기초연금 지급");
    }

    // ---------------------------------------------------------------- CSV

    @Test
    @DisplayName("CSV 는 BOM 을 붙이고 숫자는 서식 없이 원본으로 쓴다")
    void csvExport() throws Exception {
        byte[] bytes = export(new CsvExporter());

        assertThat(bytes[0] & 0xFF).isEqualTo(0xEF);
        String csv = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        List<String> lines = csv.lines().toList();

        assertThat(lines).hasSize(4);
        assertThat(lines.get(0)).isEqualTo("사업명,집행액,집행률");
        assertThat(lines.get(1)).isEqualTo("기초연금 지급,120000000,0.85");
    }

    @Test
    @DisplayName("쉼표와 따옴표가 든 값은 CSV 규칙대로 감싼다")
    void csvQuoting() throws Exception {
        report.getPages().get(0).getElements().stream()
                .filter(e -> "d_program".equals(e.getElementId()))
                .findFirst()
                .ifPresent(e -> e.setText("사업, \"특별\" 지원"));

        String csv = new String(export(new CsvExporter()), StandardCharsets.UTF_8);
        assertThat(csv).contains("\"사업, \"\"특별\"\" 지원\"");
    }

    // ---------------------------------------------------------------- XLSX

    @Test
    @DisplayName("엑셀은 숫자를 수치 셀로 넣는다")
    void xlsxExport() throws Exception {
        byte[] bytes = export(new XlsxExporter());
        assertThat(bytes).hasSizeGreaterThan(1000);

        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                new java.io.ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheetAt(0);

            // 0: 제목, 1: 조회조건, 2: 빈 줄, 3: 헤더, 4~: 데이터
            assertThat(sheet.getRow(3).getCell(0).getStringCellValue()).isEqualTo("사업명");
            assertThat(sheet.getRow(4).getCell(0).getStringCellValue()).isEqualTo("기초연금 지급");
            assertThat(sheet.getRow(4).getCell(1).getNumericCellValue()).isEqualTo(120_000_000d);
            assertThat(sheet.getRow(4).getCell(2).getCellStyle().getDataFormatString()).contains("%");
        }
    }

    // ---------------------------------------------------------------- HTML

    @Test
    @DisplayName("HTML 은 쪽마다 컨테이너를 만들고 값을 이스케이프한다")
    void htmlExport() throws Exception {
        HtmlExporter exporter = new HtmlExporter();
        String html = new String(export(exporter), StandardCharsets.UTF_8);

        assertThat(html).contains("<div class=\"kr-page\" data-page=\"1\"");
        assertThat(html).contains("기초연금 지급");
        assertThat(html).contains("120,000,000");
        assertThat(html).contains("85%");
        assertThat(HtmlExporter.escape("<script>alert('x')</script>"))
                .isEqualTo("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;");
    }

    // ---------------------------------------------------------------- PDF

    @Test
    @DisplayName("차트가 있어도 표 내보내기는 본문 행만 담는다")
    void chartIsNotPartOfTabularExport() throws Exception {
        RenderedReport withChart = engine.layout(templateWithChart(), data(), Map.of());
        TabularExtractor.Table table = TabularExtractor.extract(withChart);

        assertThat(table.rows()).hasSize(3);
        String csv = new String(export(new CsvExporter(), withChart), StandardCharsets.UTF_8);
        assertThat(csv.lines().count()).isEqualTo(4);
    }

    @Test
    @DisplayName("차트는 HTML 에서 SVG 로, PDF 에서 도형으로 그려진다")
    void chartRendersInBothFormats() throws Exception {
        RenderedReport withChart = engine.layout(templateWithChart(), data(), Map.of());

        String html = new String(export(new HtmlExporter(), withChart), StandardCharsets.UTF_8);
        assertThat(html).contains("<svg").contains("부서별 집행액");

        PdfFontProvider fontProvider = new PdfFontProvider(new KReportProperties());
        fontProvider.loadFontBytes();
        byte[] pdf = export(new PdfExporter(fontProvider, new ReportImageLoader(new KReportProperties())), withChart);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("부서별 집행액");
        }
    }

    /** 리포트 꼬리말에 가로 막대 차트를 하나 얹은 템플릿 */
    private ReportTemplate templateWithChart() {
        ReportTemplate t = template();

        ChartSeriesDef series = new ChartSeriesDef();
        series.setName("집행액");
        series.setExpression("{AMT}");
        series.setAggregation(ChartAggregation.SUM);

        ChartSpec chart = new ChartSpec();
        chart.setType(ChartType.BAR);
        chart.setTitle("부서별 집행액");
        chart.setCategoryExpression("{DEPT}");
        chart.getSeries().add(series);

        ReportElement element = element(ElementType.CHART, "rf_chart", 0, 300);
        element.setHeight(120);
        element.setChart(chart);

        Band footer = new Band();
        footer.setType(BandType.REPORT_FOOTER);
        footer.setHeight(126);
        footer.add(element);
        t.getBands().add(footer);
        return t;
    }

    private byte[] export(ReportExporter exporter, RenderedReport target) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.export(target, out);
        return out.toByteArray();
    }

    @Test
    @DisplayName("PDF 에 한글이 깨지지 않고 들어간다")
    void pdfExportKeepsKorean() throws Exception {
        PdfFontProvider fontProvider = new PdfFontProvider(new KReportProperties());
        fontProvider.loadFontBytes();

        try (PDDocument probe = new PDDocument()) {
            Assumptions.assumeTrue(fontProvider.fontsFor(probe).koreanCapable(),
                    "한글 TTF 가 없는 환경이라 건너뜁니다");
        }

        byte[] bytes = export(new PdfExporter(fontProvider, new ReportImageLoader(new KReportProperties())));
        assertThat(new String(bytes, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");

        try (PDDocument document = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(document);

            assertThat(document.getNumberOfPages()).isEqualTo(1);
            assertThat(text)
                    .contains("부서별 집행 내역")
                    .contains("복지정책과")
                    .contains("기초연금 지급")
                    .contains("120,000,000")
                    .contains("85%");
        }
    }
}
