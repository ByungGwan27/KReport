package kr.co.kreport.engine.chart;

import kr.co.kreport.template.ChartSpec;

import java.util.ArrayList;
import java.util.List;

import static kr.co.kreport.engine.chart.ChartStyle.LABEL_FONT;
import static kr.co.kreport.engine.chart.ChartStyle.LINE_HEIGHT;
import static kr.co.kreport.engine.chart.ChartStyle.PAD;
import static kr.co.kreport.engine.chart.ChartStyle.TITLE_FONT;

/**
 * 차트 데이터를 좌표가 확정된 도형 목록으로 바꾼다.
 *
 * <p>이 클래스는 상자 하나를 제목·플롯·범례로 나누는 일만 한다. 나눈 자리 안을 채우는
 * 것은 {@link CartesianChartRenderer}(축이 있는 차트)와 {@link CircularRenderer}
 * (원·도넛)가 맡고, 실제 마크는 그 아래의 렌더러들이 그린다. 생김새를 정하는 숫자는
 * {@link ChartStyle} 에, 글자 자리를 재는 셈은 {@link ChartTextMetrics} 에 모여 있다.</p>
 *
 * <p>나오는 것은 좌표와 색만 남은 도형 목록이다. HTML 은 이것을 SVG 로, PDF 는 같은
 * 목록을 PDFBox 명령으로 옮긴다. 두 출력이 어긋나지 않는 이유가 여기에 있다.</p>
 */
public final class ChartLayoutEngine {

    private ChartLayoutEngine() {
    }

    public static List<ChartShape> layout(ChartSpec spec, ChartDataset data,
                                          double width, double height) {
        List<ChartShape> shapes = new ArrayList<>();
        if (width <= 0 || height <= 0) {
            return shapes;
        }
        if (data.isEmpty()) {
            shapes.add(emptyNotice(width, height));
            return shapes;
        }

        double top = PAD + drawTitle(shapes, spec, width);
        double contentWidth = width - PAD * 2;

        ChartLegend legend = new ChartLegend(spec, data);
        // 범례가 몇 줄이 될지 먼저 재야 플롯에 남길 높이가 정해진다
        boolean showLegend = legend.isVisible();
        int legendLines = showLegend ? legend.lineCount(contentWidth) : 0;
        double legendHeight = legendLines * ChartLegend.lineHeight() + (showLegend ? PAD : 0);

        double plotHeight = height - PAD - legendHeight - top;
        if (plotHeight < 10) {
            return shapes;
        }

        if (spec.getType().isCircular()) {
            new CircularRenderer(spec, data, shapes).draw(PAD, top, contentWidth, plotHeight);
        } else {
            new CartesianChartRenderer(spec, data, shapes).draw(PAD, top, contentWidth, plotHeight);
        }

        if (showLegend) {
            legend.draw(shapes, PAD, height - PAD - legendHeight, contentWidth, legendHeight);
        }
        return shapes;
    }

    /** @return 제목이 쓴 높이. 제목이 없으면 0. */
    private static double drawTitle(List<ChartShape> shapes, ChartSpec spec, double width) {
        if (spec.getTitle() == null || spec.getTitle().isBlank()) {
            return 0;
        }
        shapes.add(new ChartShape.Text(width / 2, PAD, spec.getTitle(),
                ChartPalette.TEXT_PRIMARY, TITLE_FONT, true,
                ChartShape.Anchor.MIDDLE, ChartShape.Baseline.TOP));
        return TITLE_FONT * LINE_HEIGHT;
    }

    /**
     * 자료가 없을 때의 안내.
     *
     * <p>빈 상자를 그대로 두면 차트가 깨진 것인지 자료가 없는 것인지 구분할 수 없다.
     * 결재 라인에서 그 차이는 크다.</p>
     */
    private static ChartShape emptyNotice(double width, double height) {
        return new ChartShape.Text(width / 2, height / 2, "표시할 자료가 없습니다.",
                ChartPalette.TEXT_SECONDARY, LABEL_FONT, false,
                ChartShape.Anchor.MIDDLE, ChartShape.Baseline.MIDDLE);
    }
}
