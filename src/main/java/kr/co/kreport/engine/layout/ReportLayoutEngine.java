package kr.co.kreport.engine.layout;

import kr.co.kreport.engine.chart.ChartDataBuilder;
import kr.co.kreport.engine.chart.ChartDataset;
import kr.co.kreport.engine.chart.ChartLayoutEngine;
import kr.co.kreport.engine.data.DataTable;
import kr.co.kreport.engine.expression.EvalContext;
import kr.co.kreport.engine.expression.ExprNode;
import kr.co.kreport.engine.expression.ExpressionEvaluator;
import kr.co.kreport.engine.expression.ValueFormatter;
import kr.co.kreport.engine.expression.Values;
import kr.co.kreport.template.Band;
import kr.co.kreport.template.ChartSpec;
import kr.co.kreport.template.BandType;
import kr.co.kreport.template.ElementStyle;
import kr.co.kreport.template.ElementType;
import kr.co.kreport.template.GroupDef;
import kr.co.kreport.template.PageSetup;
import kr.co.kreport.template.ReportElement;
import kr.co.kreport.template.ReportTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 밴드 정의와 조회 결과를 좌표가 확정된 페이지 목록으로 바꾼다.
 *
 * <p>출력은 두 번에 나눠 만든다. 1차에서 본문(리포트/그룹/디테일 밴드)을 흘려보내며
 * 페이지를 나누고, 2차에서 머리말과 꼬리말을 얹는다. 전체 페이지 수는 본문을 다 흘려 보내야
 * 알 수 있는데, 꼬리말의 "1 / 12" 같은 표기가 바로 그 값을 필요로 하기 때문이다.</p>
 */
@Slf4j
@Component
public class ReportLayoutEngine {

    /** 밴드 높이가 본문 영역보다 클 때 무한 페이지 생성을 막기 위한 상한 */
    private static final int MAX_PAGES = 20_000;

    public RenderedReport layout(ReportTemplate template, DataTable data, Map<String, Object> parameters) {
        long startedAt = System.currentTimeMillis();

        EvalContext context = new EvalContext();
        context.putParameters(parameters);
        context.setColumnAlias(data.getColumnAlias());
        putStaticVariables(context, template, data);

        GroupIndex groupIndex = template.getGroups().isEmpty()
                ? GroupIndex.empty(data.size())
                : GroupIndex.build(data, template.getGroups(), context);

        AggregateStore aggregates = AggregateStore.compute(
                data, groupIndex, collectAggregateNodes(template), context);
        context.setAggregateLookup(aggregates);

        Session session = new Session(template, data, groupIndex, context);
        flowBody(session);
        paintPageBands(session);

        RenderedReport report = new RenderedReport();
        report.setTemplate(template);
        report.setData(data);
        report.setParameters(parameters);
        report.getPages().addAll(session.pages);
        report.setElapsedMillis(System.currentTimeMillis() - startedAt);

        log.debug("레이아웃 완료: {}행 -> {}페이지, {}ms",
                data.size(), report.getPageCount(), report.getElapsedMillis());
        return report;
    }

    // ---------------------------------------------------------------- 1차: 본문 흘리기

    private void flowBody(Session s) {
        s.newPage();

        s.template.band(BandType.REPORT_HEADER)
                .ifPresent(band -> emit(s, band, -1));

        Optional<Band> detail = s.template.band(BandType.DETAIL);
        int rowCount = s.data.size();

        for (int row = 0; row < rowCount; row++) {
            s.context.setRow(s.data.row(row), row);
            putRowVariables(s, row);

            openGroups(s, row);

            detail.ifPresent(band -> emit(s, band, s.context.getRowIndex()));
            s.lastEmittedRow = row;
            s.currentPage.setLastRowIndex(row);

            closeGroups(s, row);
        }

        if (rowCount == 0) {
            // 데이터가 없어도 머리말/꼬리말과 "자료 없음" 안내는 나와야 한다
            s.context.setRow(s.data.blankRow(), -1);
        }

        s.template.band(BandType.REPORT_FOOTER)
                .ifPresent(band -> emit(s, band, s.lastEmittedRow));
    }

    /** 이 행에서 새로 시작하는 그룹의 머리말을 바깥쪽부터 출력한다 */
    private void openGroups(Session s, int row) {
        for (int level = 0; level < s.groupIndex.levelCount(); level++) {
            if (!s.groupIndex.isGroupStart(level, row)) {
                continue;
            }
            GroupDef group = s.template.getGroups().get(level);

            if (group.isPageBreak() && row > 0) {
                s.newPage(group.isResetPageNumber());
            }
            s.activeGroupLevel = level;
            s.suppressCache.clear();

            s.template.groupBand(BandType.GROUP_HEADER, group.getName())
                    .ifPresent(band -> emit(s, band, row));
        }
    }

    /** 이 행에서 끝나는 그룹의 꼬리말을 안쪽부터 출력한다 */
    private void closeGroups(Session s, int row) {
        for (int level = s.groupIndex.levelCount() - 1; level >= 0; level--) {
            if (!s.groupIndex.isGroupEnd(level, row)) {
                continue;
            }
            GroupDef group = s.template.getGroups().get(level);
            s.template.groupBand(BandType.GROUP_FOOTER, group.getName())
                    .ifPresent(band -> emit(s, band, row));
        }
    }

    /**
     * 밴드 한 개를 현재 커서 위치에 출력한다. 남은 높이가 모자라면 페이지를 넘긴다.
     *
     * @param rowIndex 값 평가에 쓸 데이터 행. 데이터와 무관한 밴드는 -1
     */
    private void emit(Session s, Band band, int rowIndex) {
        if (rowIndex >= 0 && rowIndex < s.data.size()) {
            s.context.setRow(s.data.row(rowIndex), rowIndex);
        }
        if (!ExpressionEvaluator.evalCondition(band.getPrintWhen(), s.context, true)) {
            return;
        }

        double height = band.getHeight();
        if (s.cursorY + height > s.bodyBottom && s.cursorY > s.bodyTop) {
            s.newPage();
            repeatGroupHeaders(s, rowIndex);
        }

        double top = s.cursorY;
        renderBand(s, band, top, rowIndex);
        s.cursorY = top + height;
    }

    /** 페이지가 넘어갔을 때 현재 살아 있는 그룹의 머리말을 다시 찍는다 */
    private void repeatGroupHeaders(Session s, int rowIndex) {
        if (rowIndex < 0 || s.repeatingHeaders) {
            return;
        }
        s.repeatingHeaders = true;
        try {
            for (int level = 0; level <= s.activeGroupLevel && level < s.template.getGroups().size(); level++) {
                GroupDef group = s.template.getGroups().get(level);
                if (!group.isRepeatHeaderOnNewPage()) {
                    continue;
                }
                s.template.groupBand(BandType.GROUP_HEADER, group.getName()).ifPresent(band -> {
                    double top = s.cursorY;
                    renderBand(s, band, top, rowIndex);
                    s.cursorY = top + band.getHeight();
                });
            }
        } finally {
            s.repeatingHeaders = false;
        }
    }

    // ---------------------------------------------------------------- 2차: 머리말/꼬리말

    /**
     * 전체 페이지 수가 확정된 뒤에 페이지 머리말과 꼬리말을 얹는다.
     * 1차에서 이미 각 페이지의 마지막 데이터 행을 기록해 두었으므로 그 행을 복원해 평가한다.
     */
    private void paintPageBands(Session s) {
        Optional<Band> header = s.template.band(BandType.PAGE_HEADER);
        Optional<Band> footer = s.template.band(BandType.PAGE_FOOTER);
        if (header.isEmpty() && footer.isEmpty()) {
            return;
        }

        int pageCount = s.pages.size();
        s.context.putVariable("PAGE_COUNT", pageCount);

        for (RenderedPage page : s.pages) {
            int rowIndex = page.getLastRowIndex();
            if (rowIndex >= 0 && rowIndex < s.data.size()) {
                s.context.setRow(s.data.row(rowIndex), rowIndex);
                putRowVariables(s, rowIndex);
            } else {
                s.context.setRow(s.data.blankRow(), -1);
            }
            s.context.putVariable("PAGE_NO", page.getDisplayPageNo());
            s.context.putVariable("PAGE_COUNT", pageCount);

            RenderedPage target = page;
            header.ifPresent(band -> {
                List<RenderedElement> out = new ArrayList<>();
                renderBandInto(s, band, s.page.getMarginTop(), rowIndex, out);
                target.getElements().addAll(0, out);
            });
            footer.ifPresent(band -> {
                double top = s.page.getPageHeight() - s.page.getMarginBottom() - band.getHeight();
                List<RenderedElement> out = new ArrayList<>();
                renderBandInto(s, band, top, rowIndex, out);
                target.getElements().addAll(out);
            });
        }
    }

    // ---------------------------------------------------------------- 밴드 렌더

    private void renderBand(Session s, Band band, double top, int rowIndex) {
        List<RenderedElement> out = new ArrayList<>();
        renderBandInto(s, band, top, rowIndex, out);
        s.currentPage.addAll(out);
    }

    private void renderBandInto(Session s, Band band, double top, int rowIndex, List<RenderedElement> out) {
        if (band.getBackgroundColor() != null) {
            ElementStyle fill = new ElementStyle();
            fill.setBackgroundColor(band.getBackgroundColor());
            out.add(RenderedElement.of(ElementType.RECT,
                    s.page.getMarginLeft(), top, s.page.getContentWidth(), band.getHeight(), fill));
        }

        for (ReportElement element : band.getElements()) {
            if (!ExpressionEvaluator.evalCondition(element.getPrintWhen(), s.context, true)) {
                continue;
            }
            RenderedElement rendered = renderElement(s, band, element, top, rowIndex);
            if (rendered != null) {
                out.add(rendered);
            }
        }
    }

    private RenderedElement renderElement(Session s, Band band, ReportElement element,
                                          double bandTop, int rowIndex) {
        RenderedElement r = RenderedElement.of(
                element.getType(),
                s.page.getMarginLeft() + element.getX(),
                bandTop + element.getY(),
                element.getWidth(),
                element.getHeight(),
                element.getStyle());
        r.setElementId(element.getId());
        r.setSource(element.getSource());
        r.setBandType(band.getType());
        r.setFormat(element.getFormat());

        switch (element.getType()) {
            case LABEL -> r.setText(ExpressionEvaluator.interpolate(element.getText(), s.context));
            case TEXT, BARCODE, QRCODE -> {
                Object value = ExpressionEvaluator.evalQuietly(element.getExpression(), s.context);
                if (band.getType() == BandType.DETAIL && element.isSuppressRepeat()) {
                    String cacheKey = element.getId() != null ? element.getId() : element.getExpression();
                    Object previous = s.suppressCache.get(cacheKey);
                    s.suppressCache.put(cacheKey, value);
                    if (rowIndex > 0 && Values.equal(previous, value)) {
                        r.setText("");
                        r.setRawValue(null);
                        return r;
                    }
                }
                r.setRawValue(value);
                String text = value == null
                        ? element.getNullText()
                        : ValueFormatter.format(value, element.getFormat());
                r.setText(text == null || text.isEmpty() ? element.getNullText() : text);
            }
            case CHART -> buildChart(s, band, element, r, rowIndex);
            case LINE, RECT, IMAGE -> {
                // 그리기 전용 요소는 텍스트가 없다
            }
        }
        return r;
    }

    /**
     * 차트는 놓인 밴드가 곧 집계 범위다.
     * 그룹 밴드에 있으면 그 그룹의 행만, 그 밖이면 전체 행을 집계한다.
     */
    private void buildChart(Session s, Band band, ReportElement element,
                            RenderedElement rendered, int rowIndex) {
        ChartSpec spec = element.getChart();
        if (spec == null || s.data.isEmpty()) {
            return;
        }

        int from = 0;
        int to = s.data.size() - 1;

        if (band.getType().isGroupScoped() && band.getGroupName() != null) {
            int level = s.groupIndex.levelOf(band.getGroupName());
            int anchor = rowIndex >= 0 ? rowIndex : s.lastEmittedRow;
            if (level >= 0 && anchor >= 0) {
                int[] range = s.groupIndex.rowRange(level, anchor);
                from = range[0];
                to = range[1];
            }
        }

        ChartDataset dataset = ChartDataBuilder.build(spec, s.data, s.context, from, to);
        rendered.setChartShapes(ChartLayoutEngine.layout(
                spec, dataset, element.getWidth(), element.getHeight()));
    }

    // ---------------------------------------------------------------- 변수

    private void putStaticVariables(EvalContext context, ReportTemplate template, DataTable data) {
        context.putVariable("REPORT_NAME", template.getName());
        context.putVariable("REPORT_ID", template.getReportId());
        context.putVariable("TOTAL_ROWS", data.size());
        context.putVariable("PRINT_DATE", LocalDate.now());
        context.putVariable("PRINT_TIME", LocalDateTime.now());
        context.putVariable("PAGE_NO", 1);
        context.putVariable("PAGE_COUNT", 1);
        context.putVariable("ROW_NUM", 0);
        context.putVariable("GROUP_ROW_NUM", 0);
    }

    private void putRowVariables(Session s, int row) {
        s.context.putVariable("ROW_NUM", row + 1);
        s.context.putVariable("PAGE_NO", s.currentPage == null ? 1 : s.currentPage.getDisplayPageNo());
        int innermost = s.groupIndex.levelCount() - 1;
        s.context.putVariable("GROUP_ROW_NUM",
                innermost < 0 ? row + 1 : s.groupIndex.rowNumberInGroup(innermost, row));
    }

    private List<ExprNode.Aggregate> collectAggregateNodes(ReportTemplate template) {
        List<String> expressions = new ArrayList<>();
        List<String> texts = new ArrayList<>();

        for (Band band : template.getBands()) {
            if (band.getPrintWhen() != null) {
                expressions.add(band.getPrintWhen());
            }
            for (ReportElement e : band.getElements()) {
                if (e.getExpression() != null) {
                    expressions.add(e.getExpression());
                }
                if (e.getPrintWhen() != null) {
                    expressions.add(e.getPrintWhen());
                }
                if (e.getText() != null) {
                    texts.add(e.getText());
                }
            }
        }
        return AggregateStore.collect(expressions, texts);
    }

    // ---------------------------------------------------------------- 진행 상태

    /** 한 번의 레이아웃 실행 동안만 사는 가변 상태 */
    private static final class Session {

        final ReportTemplate template;
        final PageSetup page;
        final DataTable data;
        final GroupIndex groupIndex;
        final EvalContext context;

        final List<RenderedPage> pages = new ArrayList<>();
        final Map<String, Object> suppressCache = new HashMap<>();

        final double bodyTop;
        final double bodyBottom;

        RenderedPage currentPage;
        double cursorY;
        int displayPageNo;
        int activeGroupLevel = -1;
        int lastEmittedRow = -1;
        boolean repeatingHeaders;

        Session(ReportTemplate template, DataTable data, GroupIndex groupIndex, EvalContext context) {
            this.template = template;
            this.page = template.getPage();
            this.data = data;
            this.groupIndex = groupIndex;
            this.context = context;

            double headerHeight = template.band(BandType.PAGE_HEADER).map(Band::getHeight).orElse(0.0);
            double footerHeight = template.band(BandType.PAGE_FOOTER).map(Band::getHeight).orElse(0.0);
            this.bodyTop = page.getMarginTop() + headerHeight;
            this.bodyBottom = page.getPageHeight() - page.getMarginBottom() - footerHeight;
        }

        void newPage() {
            newPage(false);
        }

        void newPage(boolean resetPageNumber) {
            if (pages.size() >= MAX_PAGES) {
                throw new IllegalStateException(
                        "출력 페이지가 상한(" + MAX_PAGES + ")을 넘었습니다. 밴드 높이나 조회 범위를 확인하세요.");
            }
            displayPageNo = resetPageNumber ? 1 : displayPageNo + 1;

            RenderedPage p = new RenderedPage();
            p.setPageNo(pages.size() + 1);
            p.setDisplayPageNo(displayPageNo);
            p.setWidth(page.getPageWidth());
            p.setHeight(page.getPageHeight());
            p.setLastRowIndex(lastEmittedRow);

            pages.add(p);
            currentPage = p;
            cursorY = bodyTop;
            suppressCache.clear();
        }
    }
}
