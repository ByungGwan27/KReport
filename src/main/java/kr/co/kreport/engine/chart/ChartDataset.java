package kr.co.kreport.engine.chart;

import java.math.BigDecimal;
import java.util.List;

/**
 * 집계가 끝난 차트 데이터. 레이아웃 계산의 입력이다.
 */
public record ChartDataset(List<String> categories, List<Series> series) {

    /**
     * @param values 항목 수와 길이가 같다. 값이 없는 칸은 null.
     */
    public record Series(String name, List<BigDecimal> values, String color) {
    }

    public static ChartDataset empty() {
        return new ChartDataset(List.of(), List.of());
    }

    public boolean isEmpty() {
        return categories.isEmpty() || series.isEmpty();
    }

    public int categoryCount() {
        return categories.size();
    }

    public int seriesCount() {
        return series.size();
    }

    public BigDecimal value(int seriesIndex, int categoryIndex) {
        List<BigDecimal> values = series.get(seriesIndex).values();
        return categoryIndex < values.size() ? values.get(categoryIndex) : null;
    }

    /** 모든 계열을 통틀어 가장 큰 값. 없으면 0 */
    public BigDecimal max() {
        BigDecimal max = null;
        for (Series s : series) {
            for (BigDecimal v : s.values()) {
                if (v != null && (max == null || v.compareTo(max) > 0)) {
                    max = v;
                }
            }
        }
        return max == null ? BigDecimal.ZERO : max;
    }

    /** 모든 계열을 통틀어 가장 작은 값. 없으면 0 */
    public BigDecimal min() {
        BigDecimal min = null;
        for (Series s : series) {
            for (BigDecimal v : s.values()) {
                if (v != null && (min == null || v.compareTo(min) < 0)) {
                    min = v;
                }
            }
        }
        return min == null ? BigDecimal.ZERO : min;
    }

    /**
     * 한 항목에 쌓인 양수의 합. 누적 막대의 길이가 된다.
     */
    public BigDecimal positiveTotal(int categoryIndex) {
        BigDecimal total = BigDecimal.ZERO;
        for (Series s : series) {
            BigDecimal v = categoryIndex < s.values().size() ? s.values().get(categoryIndex) : null;
            if (v != null && v.signum() > 0) {
                total = total.add(v);
            }
        }
        return total;
    }

    /** 한 항목에 쌓인 음수의 합 */
    public BigDecimal negativeTotal(int categoryIndex) {
        BigDecimal total = BigDecimal.ZERO;
        for (Series s : series) {
            BigDecimal v = categoryIndex < s.values().size() ? s.values().get(categoryIndex) : null;
            if (v != null && v.signum() < 0) {
                total = total.add(v);
            }
        }
        return total;
    }

    /**
     * 누적했을 때 축이 담아야 할 최댓값.
     *
     * <p>낱개 막대는 개별 값의 최댓값이면 충분하지만, 누적은 쌓인 높이가 축을 넘어서면
     * 막대가 잘린다. 그래서 축 범위를 항목별 합계로 잡아야 한다.</p>
     */
    public BigDecimal stackedMax() {
        BigDecimal max = BigDecimal.ZERO;
        for (int c = 0; c < categoryCount(); c++) {
            BigDecimal total = positiveTotal(c);
            if (total.compareTo(max) > 0) {
                max = total;
            }
        }
        return max;
    }

    /** 누적했을 때 축이 담아야 할 최솟값 (음수 계열이 있을 때) */
    public BigDecimal stackedMin() {
        BigDecimal min = BigDecimal.ZERO;
        for (int c = 0; c < categoryCount(); c++) {
            BigDecimal total = negativeTotal(c);
            if (total.compareTo(min) < 0) {
                min = total;
            }
        }
        return min;
    }

    /** 첫 계열의 값 합계. 원 그래프의 전체량으로 쓴다. */
    public BigDecimal firstSeriesTotal() {
        if (series.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal v : series.get(0).values()) {
            if (v != null) {
                total = total.add(v);
            }
        }
        return total;
    }
}
