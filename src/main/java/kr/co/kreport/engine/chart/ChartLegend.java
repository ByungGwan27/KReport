package kr.co.kreport.engine.chart;

import kr.co.kreport.template.ChartSpec;

import java.util.ArrayList;
import java.util.List;

import static kr.co.kreport.engine.chart.ChartStyle.LEGEND_FONT;
import static kr.co.kreport.engine.chart.ChartStyle.LEGEND_ITEM_GAP;
import static kr.co.kreport.engine.chart.ChartStyle.LEGEND_SWATCH;
import static kr.co.kreport.engine.chart.ChartStyle.LEGEND_SWATCH_GAP;
import static kr.co.kreport.engine.chart.ChartStyle.LINE_HEIGHT;

/**
 * 범례. 계열이 둘 이상이면 색만으로 구분하게 두지 않기 위해 붙는다.
 * 원 그래프는 계열이 아니라 항목을 구분해야 하므로 항목명을 싣는다.
 *
 * <p>줄 수를 먼저 재고({@link #lineCount(double)}) 나중에 그리는({@link #draw}) 두 단계로
 * 나뉜다. 범례가 몇 줄이 될지 알아야 플롯에 남길 높이가 정해지고, 플롯 높이가 정해져야
 * 범례를 어디에 놓을지 알 수 있기 때문이다.</p>
 */
final class ChartLegend {

    private final ChartSpec spec;
    private final ChartDataset data;
    private final List<String> labels;

    ChartLegend(ChartSpec spec, ChartDataset data) {
        this.spec = spec;
        this.data = data;
        this.labels = collectLabels(spec, data);
    }

    /**
     * 범례를 붙일지 판단한다. 정의가 아니라 실제로 그려질 데이터를 기준으로 본다.
     *
     * <p>조회 결과에 따라 계열이 하나만 남는 경우가 있는데, 그때 범례 상자를 그대로 두면
     * 제목이 이미 말하고 있는 이름을 한 번 더 적는 군더더기가 된다.</p>
     */
    boolean isVisible() {
        return switch (spec.getLegend()) {
            case SHOW -> true;
            case HIDE -> false;
            case AUTO -> data.seriesCount() > 1 || spec.getType().isCircular();
        };
    }

    /** 주어진 폭에 범례를 흘렸을 때 필요한 줄 수 */
    int lineCount(double width) {
        if (labels.isEmpty() || width <= 0) {
            return 0;
        }
        return wrap(width).size();
    }

    static double lineHeight() {
        return LEGEND_FONT * LINE_HEIGHT;
    }

    /**
     * 범례를 그린다. 한 줄에 안 들어가면 다음 줄로 넘긴다. 잘라 내면 그 색이 무엇인지
     * 알 방법이 없어져 범례를 붙인 이유 자체가 사라진다.
     */
    void draw(List<ChartShape> shapes, double x, double y, double w, double h) {
        List<String> colors = collectColors();
        List<List<Integer>> lines = wrap(w);

        double lineHeight = lineHeight();
        double top = y + (h - lineHeight * lines.size()) / 2;

        for (int row = 0; row < lines.size(); row++) {
            List<Integer> items = lines.get(row);
            // 줄마다 가운데 정렬. 왼쪽 정렬로 두면 마지막 줄이 한쪽으로 쏠려 보인다.
            double cursor = x + Math.max(0, (w - rowWidth(items)) / 2);
            double cy = top + lineHeight * row + lineHeight / 2;

            for (int index : items) {
                shapes.add(new ChartShape.Rect(cursor, cy - LEGEND_SWATCH / 2,
                        LEGEND_SWATCH, LEGEND_SWATCH,
                        colors.get(index), LEGEND_SWATCH * 0.3, ChartShape.RoundedEnd.NONE));
                // 글자는 계열색이 아니라 잉크색으로. 색은 옆의 표식이 나른다.
                shapes.add(new ChartShape.Text(cursor + LEGEND_SWATCH + LEGEND_SWATCH_GAP, cy,
                        labels.get(index),
                        ChartPalette.TEXT_SECONDARY, LEGEND_FONT, false,
                        ChartShape.Anchor.START, ChartShape.Baseline.MIDDLE));
                cursor += itemWidth(labels.get(index)) + LEGEND_ITEM_GAP;
            }
        }
    }

    /** 항목을 줄 단위로 나눠 담는다. 재는 쪽과 그리는 쪽이 같은 결과를 쓰게 한다. */
    private List<List<Integer>> wrap(double width) {
        List<List<Integer>> lines = new ArrayList<>();
        List<Integer> current = new ArrayList<>();
        double used = 0;

        for (int i = 0; i < labels.size(); i++) {
            double item = itemWidth(labels.get(i));
            if (!current.isEmpty() && used + LEGEND_ITEM_GAP + item > width) {
                lines.add(current);
                current = new ArrayList<>();
                used = 0;
            }
            used += (current.isEmpty() ? 0 : LEGEND_ITEM_GAP) + item;
            current.add(i);
        }
        if (!current.isEmpty()) {
            lines.add(current);
        }
        return lines;
    }

    private double rowWidth(List<Integer> items) {
        double total = 0;
        for (int index : items) {
            total += itemWidth(labels.get(index)) + LEGEND_ITEM_GAP;
        }
        return total - LEGEND_ITEM_GAP;
    }

    /** 범례 항목 하나가 차지하는 폭 */
    private static double itemWidth(String label) {
        return LEGEND_SWATCH + LEGEND_SWATCH_GAP + ChartTextMetrics.width(label, LEGEND_FONT);
    }

    private static List<String> collectLabels(ChartSpec spec, ChartDataset data) {
        List<String> labels = new ArrayList<>();
        if (spec.getType().isCircular()) {
            labels.addAll(data.categories());
        } else {
            for (ChartDataset.Series s : data.series()) {
                labels.add(s.name());
            }
        }
        return labels;
    }

    private List<String> collectColors() {
        List<String> colors = new ArrayList<>();
        if (spec.getType().isCircular()) {
            for (int c = 0; c < data.categoryCount(); c++) {
                colors.add(spec.getOtherLabel() != null && spec.getOtherLabel().equals(data.categories().get(c))
                        ? ChartPalette.OTHER
                        : ChartPalette.series(spec.getPalette(), c));
            }
        } else {
            for (ChartDataset.Series s : data.series()) {
                colors.add(s.color());
            }
        }
        return colors;
    }
}
