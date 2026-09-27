package kr.co.kreport.engine.chart;

import kr.co.kreport.template.ChartSpec;
import kr.co.kreport.template.ChartType;

import java.math.BigDecimal;
import java.util.List;

import static kr.co.kreport.engine.chart.ChartStyle.MARK_GAP;
import static kr.co.kreport.engine.chart.ChartStyle.PAD;
import static kr.co.kreport.engine.chart.ChartStyle.TITLE_FONT;
import static kr.co.kreport.engine.chart.ChartStyle.VALUE_FONT;

/**
 * 원과 도넛. 전체에서 차지하는 몫을 보이는 그림이라 값이 모두 양수일 때만 뜻이 통한다.
 */
final class CircularRenderer {

    /** 도넛 구멍의 크기. 바깥 반지름에 대한 비율. */
    private static final double DONUT_HOLE = 0.58;

    /** 조각 안에 비율을 적어도 될 최소 각도(도). 이보다 좁으면 글자가 조각을 넘는다. */
    private static final double LABEL_MIN_SWEEP = 28;

    private final ChartSpec spec;
    private final ChartDataset data;
    private final List<ChartShape> shapes;

    CircularRenderer(ChartSpec spec, ChartDataset data, List<ChartShape> shapes) {
        this.spec = spec;
        this.data = data;
        this.shapes = shapes;
    }

    void draw(double x, double y, double w, double h) {
        BigDecimal total = data.firstSeriesTotal();
        if (total.signum() <= 0) {
            return;
        }

        double cx = x + w / 2;
        double cy = y + h / 2;
        double outerR = Math.min(w, h) / 2 - PAD;
        if (outerR <= 2) {
            return;
        }
        double innerR = spec.getType() == ChartType.DONUT ? outerR * DONUT_HOLE : 0;

        // 조각 사이를 지면 폭만큼 띄운다. 테두리를 그리는 대신 틈으로 나눈다.
        double gapAngle = Math.toDegrees(MARK_GAP / outerR);
        int slices = data.categoryCount();
        double usable = Math.max(0, 360 - gapAngle * slices);

        double angle = 0;
        for (int c = 0; c < slices; c++) {
            BigDecimal value = data.value(0, c);
            if (value == null || value.signum() <= 0) {
                continue;
            }
            double sweep = usable * value.doubleValue() / total.doubleValue();
            shapes.add(new ChartShape.Sector(cx, cy, outerR, innerR,
                    angle, angle + sweep, sliceColor(c)));

            if (spec.isShowValues() && sweep >= LABEL_MIN_SWEEP) {
                addSliceLabel(cx, cy, outerR, innerR, angle, sweep,
                        (int) Math.round(value.doubleValue() * 100 / total.doubleValue()));
            }
            angle += sweep + gapAngle;
        }

        if (innerR > 0) {
            addCenterTotal(cx, cy, innerR, total);
        }
    }

    private String sliceColor(int category) {
        return spec.getOtherLabel() != null && spec.getOtherLabel().equals(data.categories().get(category))
                ? ChartPalette.OTHER
                : ChartPalette.series(spec.getPalette(), category);
    }

    /** 조각 안 비율 표시는 글자가 들어갈 만큼 클 때만. 넘치면 범례가 나른다. */
    private void addSliceLabel(double cx, double cy, double outerR, double innerR,
                               double angle, double sweep, int percent) {
        // 0도를 12시로 놓기 위해 90도를 뺀다
        double mid = Math.toRadians(angle + sweep / 2 - 90);
        double labelR = innerR > 0 ? (outerR + innerR) / 2 : outerR * 0.62;
        shapes.add(new ChartShape.Text(cx + Math.cos(mid) * labelR, cy + Math.sin(mid) * labelR,
                percent + "%", ChartPalette.SURFACE, VALUE_FONT, true,
                ChartShape.Anchor.MIDDLE, ChartShape.Baseline.MIDDLE));
    }

    /**
     * 도넛 한가운데의 합계.
     *
     * <p>글자를 구멍 안에 맞춘다. 넘치면 합계가 조각 위로 올라앉아 둘 다 읽기 어려워진다.
     * 그렇게까지 줄여도 안 들어가면 아예 적지 않는다.</p>
     */
    private void addCenterTotal(double cx, double cy, double innerR, BigDecimal total) {
        String text = ChartStyle.formatValue(total.doubleValue(), spec.getValueFormat());
        double units = ChartTextMetrics.width(text, 1);
        double fitted = units > 0 ? innerR * 1.7 / units : TITLE_FONT;
        double fontSize = Math.min(TITLE_FONT, fitted);

        if (fontSize >= 5) {
            shapes.add(new ChartShape.Text(cx, cy, text,
                    ChartPalette.TEXT_PRIMARY, fontSize, true,
                    ChartShape.Anchor.MIDDLE, ChartShape.Baseline.MIDDLE));
        }
    }
}
