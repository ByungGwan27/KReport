package kr.co.kreport.engine.chart;

import kr.co.kreport.engine.expression.ValueFormatter;

import java.math.BigDecimal;

/**
 * 차트 한 장의 생김새를 정하는 값들.
 *
 * <p>리포트 차트에는 확대도 툴팁도 없다. 한 장에서 읽히지 않으면 읽을 방법이 없으므로,
 * 눈금선은 지면보다 한 단계만 어둡게 깔고 마크는 얇게 두어 데이터가 앞에 오게 한다.
 * 값은 모든 점에 붙이지 않고 축과 범례가 나르게 한다. 이 원칙이 아래 숫자들에 담겨 있어서,
 * 한 군데 모아 두지 않으면 막대만 고치고 꺾은선은 그대로 두는 식으로 어긋나게 된다.</p>
 *
 * <p>화면 지침은 px 로 적혀 있고 리포트 좌표는 pt 다. 옮길 때는 {@code pt = px × 0.75}.</p>
 */
final class ChartStyle {

    // --- 글자 크기 (pt)
    static final double TITLE_FONT = 9;
    static final double LABEL_FONT = 6.5;
    static final double LEGEND_FONT = 7;
    static final double VALUE_FONT = 6.5;

    /** 글자 한 줄이 차지하는 높이. 글자 크기에 곱해 쓴다. */
    static final double LINE_HEIGHT = 1.7;

    // --- 마크 규격
    /** 마크 사이를 지면으로 끊어 주는 간격 (2px) */
    static final double MARK_GAP = 1.5;
    /** 값이 끝나는 쪽 모서리 둥글기 (4px) */
    static final double BAR_CORNER = 3;
    /** 꺾은선 두께 (2px) */
    static final double LINE_WIDTH = 1.5;
    /** 값 표식 반지름 (지름 8px) */
    static final double MARKER_R = 3;
    static final double AXIS_WIDTH = 0.5;
    static final double GRID_WIDTH = 0.4;

    /** 차트 상자 안쪽 여백 */
    static final double PAD = 4;
    /** 값 축에 놓고 싶은 눈금 개수. 실제 개수는 값의 범위에 따라 달라진다. */
    static final int TARGET_TICKS = 5;

    // --- 범례
    static final double LEGEND_SWATCH = LEGEND_FONT * 0.85;
    static final double LEGEND_ITEM_GAP = 10;
    /** 표식과 글자 사이 */
    static final double LEGEND_SWATCH_GAP = 3;

    private ChartStyle() {
    }

    static String formatValue(double value, String pattern) {
        return ValueFormatter.format(BigDecimal.valueOf(value),
                pattern == null || pattern.isBlank() ? "#,##0.##" : pattern);
    }

    /**
     * 바탕색 위에 얹을 글자색. 밝은 바탕에는 어두운 글자, 어두운 바탕에는 흰 글자.
     *
     * <p>팔레트에는 노랑처럼 밝은 색과 보라처럼 어두운 색이 함께 있어서, 한쪽으로 고정하면
     * 어느 한 계열의 값이 반드시 읽히지 않게 된다.</p>
     */
    static String contrastingInk(String hex) {
        if (hex == null || !hex.startsWith("#") || hex.length() < 7) {
            return ChartPalette.TEXT_PRIMARY;
        }
        try {
            int r = Integer.parseInt(hex.substring(1, 3), 16);
            int g = Integer.parseInt(hex.substring(3, 5), 16);
            int b = Integer.parseInt(hex.substring(5, 7), 16);
            // 눈이 느끼는 밝기는 채널마다 가중치가 다르다
            double luminance = 0.299 * r + 0.587 * g + 0.114 * b;
            return luminance > 150 ? ChartPalette.TEXT_PRIMARY : ChartPalette.SURFACE;
        } catch (NumberFormatException e) {
            return ChartPalette.TEXT_PRIMARY;
        }
    }
}
