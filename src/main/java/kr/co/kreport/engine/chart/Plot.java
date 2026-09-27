package kr.co.kreport.engine.chart;

/**
 * 마크를 그릴 수 있는 사각 영역과, 값·항목을 그 안의 좌표로 옮기는 셈.
 *
 * <p>가로 막대는 값이 x 로, 세로 막대와 꺾은선은 값이 y 로 간다. 이 뒤집힘을 그리는
 * 쪽마다 {@code if (horizontal)} 로 적으면 같은 분기가 막대·누적·라벨에 흩어져,
 * 한 곳만 고치고 나머지를 놓치기 쉽다. 방향을 아는 것은 여기 하나로 둔다.</p>
 *
 * <p>y 는 위에서 아래로 커진다. 값 축은 반대로 위로 갈수록 커야 하므로 세로 방향의
 * 셈에는 모두 {@code 1 - ratio} 가 들어간다.</p>
 *
 * @param x          왼쪽
 * @param y          위쪽
 * @param w          너비
 * @param h          높이
 * @param horizontal 값 축이 가로로 눕는지 (가로 막대)
 */
record Plot(double x, double y, double w, double h, boolean horizontal) {

    double right() {
        return x + w;
    }

    double bottom() {
        return y + h;
    }

    /** 값 축을 따라 잰 길이 */
    double valueLength() {
        return horizontal ? w : h;
    }

    /** 항목이 늘어서는 축을 따라 잰 길이 */
    double categoryLength() {
        return horizontal ? h : w;
    }

    /** 항목 하나에 주어지는 폭 */
    double categorySlot(int categoryCount) {
        return categoryLength() / Math.max(1, categoryCount);
    }

    /** 항목 칸이 시작하는 좌표. 가로 막대면 y, 세로면 x. */
    double categoryStart(int index, double slot) {
        return (horizontal ? y : x) + slot * index;
    }

    /** 비율(0~1)을 값 축 위의 좌표로. 가로면 x, 세로면 y. */
    double valueCoordinate(double ratio) {
        return horizontal
                ? x + w * ratio
                : y + h * (1 - ratio);
    }

    /** 값을 축 위의 좌표로 */
    double valueCoordinate(ValueAxis scale, double value) {
        return valueCoordinate(scale.ratio(value));
    }

    boolean tooSmall() {
        return w <= 4 || h <= 4;
    }
}
