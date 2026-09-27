package kr.co.kreport.engine.chart;

import kr.co.kreport.template.ChartSpec;

import java.util.List;

import static kr.co.kreport.engine.chart.ChartStyle.AXIS_WIDTH;
import static kr.co.kreport.engine.chart.ChartStyle.GRID_WIDTH;
import static kr.co.kreport.engine.chart.ChartStyle.LABEL_FONT;
import static kr.co.kreport.engine.chart.ChartStyle.PAD;

/**
 * 값 축과 항목 축. 눈금선, 기준선, 그리고 양쪽 축의 라벨을 그린다.
 *
 * <p>여기서 가장 손이 많이 가는 일은 <b>라벨이 서로 겹치지 않게 하는 것</b>이다. 리포트는
 * 한 번 찍히면 끝이라 겹친 글자를 뒤로 미룰 수단이 없다. 자리가 모자라면 몇 개씩 건너뛰며
 * 찍는다. 듬성듬성 읽히는 편이, 전부 찍어 놓고 아무것도 못 읽는 것보다 낫다.</p>
 */
final class AxisRenderer {

    private final ChartSpec spec;
    private final ChartDataset data;
    private final ValueAxis scale;
    private final List<Double> ticks;
    private final Plot plot;
    private final List<ChartShape> shapes;

    /** 축이 값이 아니라 구성비를 나타내는가 (100% 누적) */
    private final boolean percentAxis;

    AxisRenderer(ChartSpec spec, ChartDataset data, ValueAxis scale, List<Double> ticks,
                 Plot plot, List<ChartShape> shapes, boolean percentAxis) {
        this.spec = spec;
        this.data = data;
        this.scale = scale;
        this.ticks = ticks;
        this.plot = plot;
        this.shapes = shapes;
        this.percentAxis = percentAxis;
    }

    /**
     * 값 축의 눈금 표기. 100% 누적은 축이 비율이므로 값 서식 대신 퍼센트로 적는다.
     *
     * <p>자리를 잡는 쪽이 축을 그리기 전에 눈금 글자의 폭을 재야 해서 정적 메서드로 둔다.</p>
     */
    static String tickLabel(double tick, ChartSpec spec, boolean percentAxis) {
        return percentAxis
                ? Math.round(tick) + "%"
                : ChartStyle.formatValue(tick, spec.getValueFormat());
    }

    private String tickLabel(double tick) {
        return tickLabel(tick, spec, percentAxis);
    }

    void drawValueAxis() {
        int labelStep = tickLabelStep();
        int lastLabelled = -labelStep;

        for (int i = 0; i < ticks.size(); i++) {
            double ratio = scale.ratio(ticks.get(i));
            String label = tickLabel(ticks.get(i));

            if (plot.horizontal()) {
                double tx = plot.x() + plot.w() * ratio;
                if (spec.isShowGrid()) {
                    shapes.add(new ChartShape.Line(tx, plot.y(), tx, plot.bottom(),
                            ChartPalette.GRID, GRID_WIDTH));
                }
                // 마지막 눈금은 축이 어디까지인지 알려 주므로 가급적 찍되,
                // 직전에 찍은 것과 붙어 버릴 상황이면 포기한다
                boolean scheduled = i % labelStep == 0;
                boolean last = i == ticks.size() - 1;
                if (scheduled || (last && i - lastLabelled >= labelStep)) {
                    shapes.add(new ChartShape.Text(tx, plot.bottom() + 2, label,
                            ChartPalette.TEXT_SECONDARY, LABEL_FONT, false,
                            ChartShape.Anchor.MIDDLE, ChartShape.Baseline.TOP));
                    lastLabelled = i;
                }
            } else {
                double ty = plot.y() + plot.h() * (1 - ratio);
                if (spec.isShowGrid()) {
                    shapes.add(new ChartShape.Line(plot.x(), ty, plot.right(), ty,
                            ChartPalette.GRID, GRID_WIDTH));
                }
                shapes.add(new ChartShape.Text(plot.x() - 2, ty, label,
                        ChartPalette.TEXT_SECONDARY, LABEL_FONT, false,
                        ChartShape.Anchor.END, ChartShape.Baseline.MIDDLE));
            }
        }
        drawBaseline();
    }

    /**
     * 기준선만 축 색으로 진하게. 어디가 시작인지 한눈에 보이게 한다.
     * 로그 축에는 0이 없으므로 축 시작점이 기준선이 된다.
     */
    private void drawBaseline() {
        double ratio = scale.baselineRatio();
        if (plot.horizontal()) {
            double zx = plot.x() + plot.w() * ratio;
            shapes.add(new ChartShape.Line(zx, plot.y(), zx, plot.bottom(), ChartPalette.AXIS, AXIS_WIDTH));
        } else {
            double zy = plot.y() + plot.h() * (1 - ratio);
            shapes.add(new ChartShape.Line(plot.x(), zy, plot.right(), zy, ChartPalette.AXIS, AXIS_WIDTH));
        }
    }

    /**
     * 눈금 숫자를 몇 개마다 찍을지.
     *
     * <p>가로 값 축은 눈금 숫자가 나란히 놓여 서로 밀어낸다. 금액처럼 자릿수가 긴 값에서는
     * 라벨이 뭉개져 아무 숫자도 못 읽게 되므로, 자리가 모자라면 몇 개씩 건너뛴다. 눈금선
     * 자체는 겹치지 않으니 전부 그린다.</p>
     */
    private int tickLabelStep() {
        if (!plot.horizontal() || ticks.size() <= 1) {
            return 1;
        }
        double widest = 0;
        for (Double tick : ticks) {
            widest = Math.max(widest, ChartTextMetrics.width(tickLabel(tick), LABEL_FONT));
        }
        double spacing = plot.w() / (ticks.size() - 1);
        return widest + 4 > spacing
                ? (int) Math.ceil((widest + 4) / Math.max(1, spacing))
                : 1;
    }

    /**
     * 항목 라벨.
     *
     * @param boxLeft 차트 상자의 왼쪽 끝. 가로 막대에서 항목명에 내줄 수 있는 폭을 잰다.
     */
    void drawCategoryLabels(double boxLeft) {
        int categories = data.categoryCount();
        double slot = plot.categorySlot(categories);
        int step = categoryLabelStep(slot);

        // 가로로 늘어선 항목 라벨은 그려 놓고 보면 이웃과 겹칠 수 있다.
        // 앞의 것을 어디까지 썼는지 들고 다니며, 자리가 없으면 건너뛴다.
        double lastRight = Double.NEGATIVE_INFINITY;

        for (int c = 0; c < categories; c++) {
            if (c % step != 0) {
                continue;
            }
            String label = data.categories().get(c);
            if (plot.horizontal()) {
                shapes.add(new ChartShape.Text(plot.x() - 3, plot.y() + slot * (c + 0.5),
                        ChartTextMetrics.clip(label, LABEL_FONT, plot.x() - boxLeft - PAD),
                        ChartPalette.TEXT_SECONDARY, LABEL_FONT, false,
                        ChartShape.Anchor.END, ChartShape.Baseline.MIDDLE));
            } else {
                lastRight = drawBottomLabel(label, c, slot, step, lastRight);
            }
        }
    }

    /**
     * 플롯 아래에 놓이는 항목 라벨 하나.
     *
     * @return 이 라벨이 차지한 오른쪽 끝. 다음 라벨이 겹치는지 보는 데 쓴다.
     */
    private double drawBottomLabel(String label, int index, double slot, int step, double lastRight) {
        String clipped = ChartTextMetrics.clip(label, LABEL_FONT, slot * step);
        double center = plot.x() + slot * (index + 0.5);
        double half = ChartTextMetrics.width(clipped, LABEL_FONT) / 2;

        // 양 끝 항목의 라벨은 가운데 정렬로 두면 플롯 밖으로 삐져나가,
        // 왼쪽에서는 값 축 눈금 숫자와 겹치고 오른쪽에서는 잘린다.
        // 경계에 닿는 라벨만 안쪽으로 붙여 세운다.
        ChartShape.Anchor anchor = ChartShape.Anchor.MIDDLE;
        double left = center - half;
        double lx = center;
        if (left < plot.x()) {
            anchor = ChartShape.Anchor.START;
            lx = plot.x();
            left = plot.x();
        } else if (center + half > plot.right()) {
            anchor = ChartShape.Anchor.END;
            lx = plot.right();
            left = lx - half * 2;
        }
        // 안으로 붙여 세운 라벨은 원래 자리보다 옆으로 더 뻗으므로 이웃을 밀어낼 수 있다
        if (left < lastRight + 2) {
            return lastRight;
        }
        shapes.add(new ChartShape.Text(lx, plot.bottom() + 2, clipped,
                ChartPalette.TEXT_SECONDARY, LABEL_FONT, false,
                anchor, ChartShape.Baseline.TOP));
        return left + half * 2;
    }

    /** 가장 넓은 항목명이 칸을 넘으면 그 배수만큼 건너뛴다 */
    private int categoryLabelStep(double slot) {
        if (plot.horizontal()) {
            return 1;
        }
        double widest = ChartTextMetrics.widestWidth(data.categories(), LABEL_FONT);
        return widest > slot ? (int) Math.ceil(widest / Math.max(1, slot)) : 1;
    }
}
