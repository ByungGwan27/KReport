package kr.co.kreport.engine.chart;

import kr.co.kreport.template.ChartSpec;

import java.math.BigDecimal;
import java.util.List;

import static kr.co.kreport.engine.chart.ChartStyle.BAR_CORNER;
import static kr.co.kreport.engine.chart.ChartStyle.MARK_GAP;
import static kr.co.kreport.engine.chart.ChartStyle.VALUE_FONT;

/**
 * 묶은 막대. 항목마다 계열 수만큼 막대를 나란히 세운다.
 *
 * <p>막대는 길이가 곧 값이므로 기준선에서 시작해야 한다. 음수는 기준선 반대쪽으로 뻗는다.</p>
 */
final class BarRenderer {

    private final ChartSpec spec;
    private final ChartDataset data;
    private final ValueAxis scale;
    private final Plot plot;
    private final List<ChartShape> shapes;

    BarRenderer(ChartSpec spec, ChartDataset data, ValueAxis scale, Plot plot, List<ChartShape> shapes) {
        this.spec = spec;
        this.data = data;
        this.scale = scale;
        this.plot = plot;
        this.shapes = shapes;
    }

    void draw() {
        int categories = data.categoryCount();
        int seriesCount = data.seriesCount();

        double slot = plot.categorySlot(categories);
        double slotPad = Math.min(slot * 0.18, 6);
        double band = slot - slotPad * 2;
        double barSize = (band - MARK_GAP * (seriesCount - 1)) / seriesCount;
        if (barSize <= 0.4) {
            barSize = Math.max(0.4, band / seriesCount);
        }

        double zeroRatio = scale.ratio(0);

        for (int c = 0; c < categories; c++) {
            for (int s = 0; s < seriesCount; s++) {
                BigDecimal value = data.value(s, c);
                if (value == null) {
                    continue;
                }
                double bandStart = plot.categoryStart(c, slot) + slotPad + (barSize + MARK_GAP) * s;
                drawBar(value, colorOf(s, c, seriesCount), zeroRatio, bandStart, barSize);
            }
        }
    }

    /** 묶인 항목은 계열이 아니라 나머지 몫이므로 중립색으로 둔다 */
    private String colorOf(int series, int category, int seriesCount) {
        if (seriesCount == 1
                && spec.getOtherLabel() != null
                && spec.getOtherLabel().equals(data.categories().get(category))) {
            return ChartPalette.OTHER;
        }
        return data.series().get(series).color();
    }

    private void drawBar(BigDecimal value, String color, double zeroRatio,
                         double bandStart, double barSize) {
        double valueRatio = scale.ratio(value.doubleValue());
        double from = plot.valueCoordinate(Math.min(zeroRatio, valueRatio));
        double to = plot.valueCoordinate(Math.max(zeroRatio, valueRatio));

        if (plot.horizontal()) {
            shapes.add(new ChartShape.Rect(from, bandStart, Math.max(0.3, to - from), barSize,
                    color, BAR_CORNER, ChartShape.RoundedEnd.RIGHT));
            if (spec.isShowValues()) {
                // 막대 끝 바깥에 붙인다. 안에 넣으면 짧은 막대에서 글자가 막대보다 길어진다.
                shapes.add(valueLabel(value, to + 3, bandStart + barSize / 2,
                        ChartShape.Anchor.START, ChartShape.Baseline.MIDDLE));
            }
        } else {
            // 세로에서는 값이 클수록 좌표가 작다. from 과 to 의 뜻이 뒤집힌다.
            double top = Math.min(from, to);
            double height = Math.abs(to - from);
            shapes.add(new ChartShape.Rect(bandStart, top, barSize, Math.max(0.3, height),
                    color, BAR_CORNER, ChartShape.RoundedEnd.TOP));
            if (spec.isShowValues()) {
                shapes.add(valueLabel(value, bandStart + barSize / 2, top - 2,
                        ChartShape.Anchor.MIDDLE, ChartShape.Baseline.BOTTOM));
            }
        }
    }

    private ChartShape.Text valueLabel(BigDecimal value, double x, double y,
                                       ChartShape.Anchor anchor, ChartShape.Baseline baseline) {
        return new ChartShape.Text(x, y,
                ChartStyle.formatValue(value.doubleValue(), spec.getValueFormat()),
                ChartPalette.TEXT_PRIMARY, VALUE_FONT, false, anchor, baseline);
    }
}
