package kr.co.kreport.engine.chart;

import kr.co.kreport.template.ChartSpec;

import java.math.BigDecimal;
import java.util.List;

import static kr.co.kreport.engine.chart.ChartStyle.BAR_CORNER;
import static kr.co.kreport.engine.chart.ChartStyle.MARK_GAP;
import static kr.co.kreport.engine.chart.ChartStyle.VALUE_FONT;

/**
 * 누적 막대. 항목마다 계열 값을 순서대로 쌓는다.
 *
 * <p>양수와 음수는 기준선을 사이에 두고 각각 위아래로 쌓는다. 섞어서 더하면
 * 조각 길이가 실제 값과 무관해져 그림이 데이터를 설명하지 못하게 된다.</p>
 *
 * <p>조각 사이는 테두리를 그리지 않고 지면 폭만큼 띄워 나눈다. 테두리는 조각마다
 * 색을 하나 더 얹는 셈이라, 얇은 조각에서는 테두리가 조각 자체를 덮어 버린다.</p>
 */
final class StackedBarRenderer {

    private final ChartSpec spec;
    private final ChartDataset data;
    private final ValueAxis scale;
    private final Plot plot;
    private final List<ChartShape> shapes;

    /** 100% 누적인가. 길이를 항목마다 맞추므로 값이 아니라 구성비를 쌓는다. */
    private final boolean percent;

    StackedBarRenderer(ChartSpec spec, ChartDataset data, ValueAxis scale, Plot plot,
                       List<ChartShape> shapes) {
        this.spec = spec;
        this.data = data;
        this.scale = scale;
        this.plot = plot;
        this.shapes = shapes;
        this.percent = spec.getType().isPercentStacked();
    }

    void draw() {
        int categories = data.categoryCount();
        double slot = plot.categorySlot(categories);
        double slotPad = Math.min(slot * 0.18, 8);
        // 누적은 계열을 옆에 늘어놓지 않으므로 막대 하나가 칸을 통째로 쓴다
        double barSize = Math.max(0.5, slot - slotPad * 2);

        for (int c = 0; c < categories; c++) {
            drawCategory(c, plot.categoryStart(c, slot) + slotPad, barSize);
        }
    }

    private void drawCategory(int category, double bandStart, double barSize) {
        BigDecimal categoryTotal = data.positiveTotal(category);
        if (percent && categoryTotal.signum() <= 0) {
            // 나눌 총량이 없으면 비율을 만들 수 없다
            return;
        }

        Ends ends = findOutermost(category);
        double positiveCursor = 0;
        double negativeCursor = 0;

        for (int s = 0; s < data.seriesCount(); s++) {
            BigDecimal value = data.value(s, category);
            if (value == null || value.signum() == 0) {
                continue;
            }
            // 100% 누적은 구성비를 보는 그림이라 음수가 들어갈 자리가 없다
            if (percent && value.signum() < 0) {
                continue;
            }
            double amount = percent
                    ? value.doubleValue() / categoryTotal.doubleValue() * 100
                    : value.doubleValue();

            double from;
            double to;
            boolean outermost;
            if (amount > 0) {
                from = positiveCursor;
                to = positiveCursor + amount;
                positiveCursor = to;
                outermost = s == ends.topPositive();
            } else {
                to = negativeCursor;
                from = negativeCursor + amount;
                negativeCursor = from;
                outermost = s == ends.bottomNegative();
            }

            ChartShape.Rect segment = segment(from, to, amount, outermost,
                    data.series().get(s).color(), bandStart, barSize);
            if (segment == null) {
                continue;
            }
            shapes.add(segment);

            if (spec.isShowValues()) {
                // 100% 누적의 조각은 원래 값이 아니라 비율을 말해야 한다
                addSegmentLabel(segment, percent
                        ? Math.round(amount) + "%"
                        : ChartStyle.formatValue(value.doubleValue(), spec.getValueFormat()));
            }
        }

        if (spec.isShowValues()) {
            addTotalLabel(category, bandStart, barSize);
        }
    }

    /**
     * 조각 하나를 만든다. 자리가 없을 만큼 짧으면 null.
     *
     * @param amount 부호가 붙은 크기. 어느 쪽으로 쌓이는지 알려 준다.
     */
    private ChartShape.Rect segment(double from, double to, double amount, boolean outermost,
                                    String color, double bandStart, double barSize) {
        double pxFrom = plot.valueLength() * scale.ratio(from);
        double pxTo = plot.valueLength() * scale.ratio(to);
        double length = Math.abs(pxTo - pxFrom);

        // 바깥쪽 조각이 아니면 다음 조각과의 사이를 지면으로 끊는다
        if (!outermost) {
            length -= MARK_GAP;
        }
        if (length <= 0.2) {
            return null;
        }

        double corner = outermost ? BAR_CORNER : 0;
        // 둥근 모서리는 값이 끝나는 쪽에만. 기준선 쪽은 각져야 축에 붙어 보인다.
        ChartShape.RoundedEnd end = outermost && amount > 0
                ? (plot.horizontal() ? ChartShape.RoundedEnd.RIGHT : ChartShape.RoundedEnd.TOP)
                : ChartShape.RoundedEnd.NONE;

        // 간격은 다음 조각과 맞닿는 쪽에서만 덜어 낸다. 반대쪽에서 덜면
        // 기준선에 붙어 있어야 할 첫 조각이 축에서 떠 버린다.
        boolean trimmed = !outermost;

        if (plot.horizontal()) {
            double left = plot.x() + Math.min(pxFrom, pxTo);
            if (trimmed && amount < 0) {
                left += MARK_GAP;
            }
            return new ChartShape.Rect(left, bandStart, length, barSize, color, corner, end);
        }
        double top = plot.bottom() - Math.max(pxFrom, pxTo);
        if (trimmed && amount > 0) {
            top += MARK_GAP;
        }
        return new ChartShape.Rect(bandStart, top, barSize, length, color, corner, end);
    }

    /**
     * 조각 안에 값을 적는다. 들어갈 자리가 없으면 적지 않는다.
     * 넘치도록 그려 놓으면 옆 조각의 값과 겹쳐 둘 다 못 읽게 된다.
     */
    private void addSegmentLabel(ChartShape.Rect segment, String text) {
        if (segment.width() < ChartTextMetrics.width(text, VALUE_FONT) + 4
                || segment.height() < VALUE_FONT * 1.4) {
            return;
        }
        shapes.add(new ChartShape.Text(
                segment.x() + segment.width() / 2,
                segment.y() + segment.height() / 2,
                text,
                // 조각 색 위에 얹히므로 바탕 밝기에 맞춰 글자색을 고른다
                ChartStyle.contrastingInk(segment.fill()),
                VALUE_FONT, false,
                ChartShape.Anchor.MIDDLE, ChartShape.Baseline.MIDDLE));
    }

    /**
     * 막대 바깥 끝에 쌓인 합계를 적는다. 누적에서 가장 자주 묻는 값이다.
     *
     * <p>100% 누적에서는 특히 중요하다. 길이를 맞추는 순간 총량 차이가 그림에서 지워지므로,
     * 3건을 나눈 비율과 300건을 나눈 비율이 똑같은 막대로 보인다. 원래 합계를 곁들이지 않으면
     * 비율만 보고 판단하게 된다.</p>
     */
    private void addTotalLabel(int category, double bandStart, double barSize) {
        BigDecimal total = data.positiveTotal(category);
        if (total.signum() <= 0) {
            return;
        }
        String text = ChartStyle.formatValue(total.doubleValue(), spec.getValueFormat());
        // 100% 누적은 막대가 항상 끝까지 차 있으므로 합계를 축 끝에 붙인다
        double end = plot.valueCoordinate(scale, percent ? scale.max() : total.doubleValue());

        if (plot.horizontal()) {
            shapes.add(new ChartShape.Text(end + 3, bandStart + barSize / 2, text,
                    ChartPalette.TEXT_PRIMARY, VALUE_FONT, true,
                    ChartShape.Anchor.START, ChartShape.Baseline.MIDDLE));
        } else {
            shapes.add(new ChartShape.Text(bandStart + barSize / 2, end - 2, text,
                    ChartPalette.TEXT_PRIMARY, VALUE_FONT, true,
                    ChartShape.Anchor.MIDDLE, ChartShape.Baseline.BOTTOM));
        }
    }

    /**
     * 각 방향에서 가장 바깥에 놓일 계열을 찾는다. 그 조각만 모서리가 둥글고
     * 이웃과의 간격을 덜어 내지 않는다.
     */
    private Ends findOutermost(int category) {
        int topPositive = -1;
        int bottomNegative = -1;
        for (int s = 0; s < data.seriesCount(); s++) {
            BigDecimal v = data.value(s, category);
            if (v == null) {
                continue;
            }
            if (v.signum() > 0) {
                topPositive = s;
            } else if (v.signum() < 0 && !percent) {
                bottomNegative = s;
            }
        }
        return new Ends(topPositive, bottomNegative);
    }

    private record Ends(int topPositive, int bottomNegative) {
    }
}
