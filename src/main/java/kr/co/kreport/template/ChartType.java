package kr.co.kreport.template;

/**
 * 차트 종류.
 *
 * <p>리포트 차트는 종이와 PDF 로 나가므로 확대나 툴팁이 없다. 한 장에서 읽히지 않으면
 * 읽을 방법이 없기 때문에, 종류별로 감당할 수 있는 계열 수와 항목 수를 함께 정의해 둔다.</p>
 */
public enum ChartType {

    /** 세로 막대. 항목별 크기 비교 */
    COLUMN(8, 20),
    /** 가로 막대. 항목명이 길 때(부서명, 사업명 등) 쓴다 */
    BAR(8, 20),
    /**
     * 세로 누적 막대. 항목별 합계와 그 안의 구성을 한 번에 본다.
     *
     * <p>계열 상한이 낱개 막대보다 낮다. 누적은 조각이 맞붙어 있어 경계를 구별해야 하는데,
     * 조각이 많아지면 가운데 것들이 얇아져 색도 값도 읽히지 않는다.</p>
     */
    STACKED_COLUMN(6, 20),
    /** 가로 누적 막대 */
    STACKED_BAR(6, 20),
    /**
     * 세로 100% 누적 막대. 항목마다 길이를 100%로 맞춰 구성비만 비교한다.
     *
     * <p>총량 차이를 일부러 지우는 그림이므로, 총량이 크게 다른 항목들을 나란히 두면
     * 3건짜리 항목과 300건짜리 항목이 같은 높이로 보인다. 막대 끝에 원래 합계를 함께
     * 적는 이유가 그것이다.</p>
     */
    PERCENT_COLUMN(6, 20),
    /** 가로 100% 누적 막대 */
    PERCENT_BAR(6, 20),
    /** 꺾은선. 시간 흐름 */
    LINE(8, 60),
    /** 영역. 시간 흐름 + 누적감 */
    AREA(4, 60),
    /** 원 그래프. 한눈에 보는 구성비 */
    PIE(1, 6),
    /** 도넛. 구성비 + 가운데 합계 */
    DONUT(1, 6);

    private final int maxSeries;
    private final int maxCategories;

    ChartType(int maxSeries, int maxCategories) {
        this.maxSeries = maxSeries;
        this.maxCategories = maxCategories;
    }

    /** 이 종류가 감당할 수 있는 계열 수 */
    public int getMaxSeries() {
        return maxSeries;
    }

    /** 이 종류가 감당할 수 있는 항목 수. 넘으면 상위 N 개만 두고 나머지는 묶는다. */
    public int getMaxCategories() {
        return maxCategories;
    }

    public boolean isCircular() {
        return this == PIE || this == DONUT;
    }

    /** 막대 계열 전체 (낱개 + 누적) */
    public boolean isBarLike() {
        return this == COLUMN || this == BAR || isStacked();
    }

    /** 계열을 옆에 나란히 두지 않고 위로 쌓는 종류 (절대량 누적 + 100% 누적) */
    public boolean isStacked() {
        return this == STACKED_COLUMN || this == STACKED_BAR || isPercentStacked();
    }

    /** 항목마다 길이를 100%로 맞추는 종류 */
    public boolean isPercentStacked() {
        return this == PERCENT_COLUMN || this == PERCENT_BAR;
    }

    public boolean isLineLike() {
        return this == LINE || this == AREA;
    }

    /** 값 축이 가로인지 여부 (가로 막대 계열만 해당) */
    public boolean isHorizontalValueAxis() {
        return this == BAR || this == STACKED_BAR || this == PERCENT_BAR;
    }
}
