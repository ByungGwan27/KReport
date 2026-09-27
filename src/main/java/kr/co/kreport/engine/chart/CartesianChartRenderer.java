package kr.co.kreport.engine.chart;

import kr.co.kreport.template.ChartSpec;
import kr.co.kreport.template.ChartType;

import java.math.BigDecimal;
import java.util.List;

import static kr.co.kreport.engine.chart.ChartStyle.LABEL_FONT;
import static kr.co.kreport.engine.chart.ChartStyle.PAD;
import static kr.co.kreport.engine.chart.ChartStyle.TARGET_TICKS;
import static kr.co.kreport.engine.chart.ChartStyle.VALUE_FONT;

/**
 * 축이 있는 차트(막대·누적·꺾은선·영역)의 자리 배분.
 *
 * <p>이 클래스가 하는 일은 그리기가 아니라 <b>자리 나누기</b>다. 축 눈금 숫자, 항목명,
 * 막대 끝의 값 라벨이 각각 얼마나 넓은지를 먼저 재서 마크를 그릴 영역을 남긴다. 순서를
 * 뒤집어 마크를 먼저 그리면 라벨이 들어갈 자리가 없어 잘려 나가고, 잘린 축 숫자는 차트를
 * 못 읽게 만든다. 실제로 그리는 일은 {@link AxisRenderer} 와 마크 렌더러들이 맡는다.</p>
 */
final class CartesianChartRenderer {

    /** 세로 막대 위에 값 라벨을 얹기 위해 비워 두는 높이 */
    private static final double VALUE_LABEL_ROOM = VALUE_FONT * 1.4;

    private final ChartSpec spec;
    private final ChartDataset data;
    private final List<ChartShape> shapes;

    CartesianChartRenderer(ChartSpec spec, ChartDataset data, List<ChartShape> shapes) {
        this.spec = spec;
        this.data = data;
        this.shapes = shapes;
    }

    void draw(double x, double y, double w, double h) {
        ChartType type = spec.getType();
        boolean percentAxis = type.isPercentStacked();
        ValueAxis scale = buildScale(percentAxis);
        List<Double> ticks = scale.ticks();

        Plot plot = type.isHorizontalValueAxis()
                ? horizontalPlot(x, y, w, h, ticks, percentAxis)
                : verticalPlot(x, y, w, h, ticks, percentAxis);

        if (plot.tooSmall()) {
            return;
        }

        // 자리가 정해진 뒤에야 축을 그릴 수 있다
        AxisRenderer axis = new AxisRenderer(spec, data, scale, ticks, plot, shapes, percentAxis);
        axis.drawValueAxis();

        if (type.isStacked()) {
            new StackedBarRenderer(spec, data, scale, plot, shapes).draw();
        } else if (type.isBarLike()) {
            new BarRenderer(spec, data, scale, plot, shapes).draw();
        } else {
            new LineRenderer(spec, data, scale, plot, shapes).draw();
        }

        axis.drawCategoryLabels(x);
    }

    /**
     * 값 축의 눈금 방식을 고른다.
     *
     * <p>누적은 쌓인 높이가 축을 넘으면 막대가 잘리므로 항목별 합계로 범위를 잡는다.</p>
     */
    private ValueAxis buildScale(boolean percentAxis) {
        if (percentAxis) {
            // 100% 누적은 항목마다 길이를 맞추므로 축이 0~100 으로 고정된다
            return new AxisScale(0, 100, 25);
        }
        if (useLogScale()) {
            return LogScale.of(data.min(), data.max());
        }
        ChartType type = spec.getType();
        BigDecimal axisMin = type.isStacked() ? data.stackedMin() : data.min();
        BigDecimal axisMax = type.isStacked() ? data.stackedMax() : data.max();
        // 값 서식에 소수 자리가 없으면 눈금도 정수로 놓는다. 건수 차트에서 "2.5건" 이 나오지 않도록.
        boolean integerAxis = spec.getValueFormat() == null || !spec.getValueFormat().contains(".");
        return AxisScale.of(axisMin, axisMax, TARGET_TICKS, type.isBarLike(), integerAxis);
    }

    /**
     * 로그 축을 실제로 쓸 수 있는지 판단한다.
     *
     * <p>설정이 로그여도 그리지 못하는 경우가 있다. 막대는 길이로 크기를 말하므로 로그를
     * 얹으면 길이 비율이 값 비율과 어긋나고, 데이터에 0이나 음수가 섞이면 로그값 자체가
     * 없다. 이럴 때는 선형으로 되돌려 그린다. 눈금 방식이 달라지는 편이,
     * 빈 차트를 내놓거나 틀린 길이를 그리는 것보다 낫다.</p>
     */
    private boolean useLogScale() {
        if (spec.getValueScale() != ChartSpec.ValueScale.LOG) {
            return false;
        }
        // 영역 차트는 채움이 0 기준선까지 내려가야 하는데 로그 축에는 0이 없다
        if (spec.getType() != ChartType.LINE) {
            return false;
        }
        return LogScale.isUsable(data);
    }

    /** 가로 막대: 왼쪽에 항목명, 오른쪽에 값 라벨 자리를 낸다. */
    private Plot horizontalPlot(double x, double y, double w, double h,
                                List<Double> ticks, boolean percentAxis) {
        double categoryWidth = Math.min(
                ChartTextMetrics.widestWidth(data.categories(), LABEL_FONT), w * 0.38);

        // 오른쪽 여백: 마지막 눈금 숫자의 절반(가운데 정렬이라 절반이 밖으로 나간다)과
        // 막대 끝에 붙는 값 라벨 중 넓은 쪽. 재지 않으면 둘 다 잘린다.
        double rightRoom = ChartTextMetrics.width(
                AxisRenderer.tickLabel(ticks.get(ticks.size() - 1), spec, percentAxis),
                LABEL_FONT) / 2;
        if (spec.isShowValues()) {
            // 100% 누적의 막대 끝에는 100%가 아니라 원래 합계가 붙는다
            double widest = percentAxis
                    ? data.stackedMax().doubleValue()
                    : (spec.getType().isStacked() ? data.stackedMax() : data.max()).doubleValue();
            rightRoom = Math.max(rightRoom,
                    ChartTextMetrics.width(
                            ChartStyle.formatValue(widest, spec.getValueFormat()), VALUE_FONT) + 4);
        }
        return new Plot(
                x + categoryWidth + PAD,
                y,
                w - categoryWidth - PAD - rightRoom,
                h - LABEL_FONT * 1.8,
                true);
    }

    /** 세로 막대·꺾은선: 왼쪽에 눈금 숫자, 위에 값 라벨 자리를 낸다. */
    private Plot verticalPlot(double x, double y, double w, double h,
                              List<Double> ticks, boolean percentAxis) {
        double tickWidth = 0;
        for (Double tick : ticks) {
            tickWidth = Math.max(tickWidth, ChartTextMetrics.width(
                    AxisRenderer.tickLabel(tick, spec, percentAxis), LABEL_FONT));
        }

        double top = y;
        double height = h - LABEL_FONT * 1.8;
        // 세로 막대의 값 라벨은 막대 위에 붙으므로 그만큼 위를 비워 둔다
        if (spec.isShowValues() && spec.getType().isBarLike()) {
            double labelRoom = VALUE_LABEL_ROOM;
            top += labelRoom;
            height -= labelRoom;
        }
        // 양 끝 항목 라벨은 그리는 쪽에서 안으로 붙여 세우므로 플롯을 줄이지 않는다
        return new Plot(x + tickWidth + PAD, top, w - tickWidth - PAD * 2, height, false);
    }
}
