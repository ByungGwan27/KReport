package kr.co.kreport.engine.chart;

import kr.co.kreport.template.ChartSpec;
import kr.co.kreport.template.ChartType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static kr.co.kreport.engine.chart.ChartStyle.LINE_WIDTH;
import static kr.co.kreport.engine.chart.ChartStyle.MARK_GAP;
import static kr.co.kreport.engine.chart.ChartStyle.MARKER_R;
import static kr.co.kreport.engine.chart.ChartStyle.VALUE_FONT;

/**
 * 꺾은선과 영역. 값을 길이가 아니라 위치로 나타내므로 기준선이 0이 아니어도 되고,
 * 로그 축을 얹을 수 있는 유일한 형태이기도 하다.
 */
final class LineRenderer {

    /** 영역 채움의 진하기. 겹친 계열이 서로를 가리지 않을 만큼만. */
    private static final double AREA_OPACITY = 0.18;

    private final ChartSpec spec;
    private final ChartDataset data;
    private final ValueAxis scale;
    private final Plot plot;
    private final List<ChartShape> shapes;

    LineRenderer(ChartSpec spec, ChartDataset data, ValueAxis scale, Plot plot, List<ChartShape> shapes) {
        this.spec = spec;
        this.data = data;
        this.scale = scale;
        this.plot = plot;
        this.shapes = shapes;
    }

    void draw() {
        boolean area = spec.getType() == ChartType.AREA;
        double slot = plot.categorySlot(data.categoryCount());

        for (int s = 0; s < data.seriesCount(); s++) {
            List<double[]> points = pointsOf(s, slot);
            if (points.isEmpty()) {
                continue;
            }
            String color = data.series().get(s).color();

            if (area) {
                shapes.add(new ChartShape.Polyline(closeToBaseline(points), null, 0, color, AREA_OPACITY));
            }
            shapes.add(new ChartShape.Polyline(points, color, LINE_WIDTH, null, 0));

            for (double[] p : points) {
                // 표식에 지면색 테두리를 둘러 계열이 겹쳐도 앞뒤가 구분되게 한다
                shapes.add(new ChartShape.Circle(p[0], p[1], MARKER_R,
                        color, ChartPalette.SURFACE, MARK_GAP));
            }

            if (spec.isShowValues()) {
                addEndLabel(s, points);
            }
        }
    }

    /** 값이 있는 항목만 점으로. 빈 항목은 건너뛰어 선을 잇는다. */
    private List<double[]> pointsOf(int series, double slot) {
        List<double[]> points = new ArrayList<>();
        for (int c = 0; c < data.categoryCount(); c++) {
            BigDecimal value = data.value(series, c);
            if (value == null) {
                continue;
            }
            points.add(new double[]{
                    plot.x() + slot * (c + 0.5),
                    plot.valueCoordinate(scale, value.doubleValue())});
        }
        return points;
    }

    /** 선 아래를 기준선까지 내려 닫은 다각형 */
    private List<double[]> closeToBaseline(List<double[]> points) {
        List<double[]> polygon = new ArrayList<>(points);
        double baseY = plot.valueCoordinate(scale.baselineRatio());
        polygon.add(new double[]{points.get(points.size() - 1)[0], baseY});
        polygon.add(new double[]{points.get(0)[0], baseY});
        return polygon;
    }

    /**
     * 모든 점에 숫자를 붙이면 읽히지 않는다. 켜져 있어도 끝점 하나만 적는다.
     * 꺾은선에서 가장 자주 묻는 값이 "그래서 지금 얼마인가" 이기 때문이다.
     */
    private void addEndLabel(int series, List<double[]> points) {
        BigDecimal lastValue = null;
        for (int c = data.categoryCount() - 1; c >= 0; c--) {
            if (data.value(series, c) != null) {
                lastValue = data.value(series, c);
                break;
            }
        }
        if (lastValue == null) {
            return;
        }
        double[] last = points.get(points.size() - 1);
        shapes.add(new ChartShape.Text(last[0], last[1] - MARKER_R - 2,
                ChartStyle.formatValue(lastValue.doubleValue(), spec.getValueFormat()),
                ChartPalette.TEXT_PRIMARY, VALUE_FONT, false,
                ChartShape.Anchor.MIDDLE, ChartShape.Baseline.BOTTOM));
    }
}
