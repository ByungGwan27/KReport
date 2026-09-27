package kr.co.kreport.engine.chart;

import java.util.List;

/**
 * 값 축. 값을 플롯 안의 0~1 위치로 바꾸고 눈금 목록을 내놓는다.
 *
 * <p>선형과 로그의 차이를 이 한 겹에 가둬 두면, 마크를 놓는 쪽은 축이 어떤 종류인지
 * 몰라도 된다. 눈금 계산과 좌표 변환이 축마다 흩어지면 한쪽만 고쳐져 어긋나기 쉽다.</p>
 */
public sealed interface ValueAxis permits AxisScale, LogScale {

    /** 축이 담는 최솟값 */
    double min();

    /** 축이 담는 최댓값 */
    double max();

    /**
     * 값의 축 상 위치. 0이 축 시작, 1이 축 끝이다.
     * 범위를 벗어난 값은 잘라 낸다.
     */
    double ratio(double value);

    /** 눈금으로 찍을 값들 */
    List<Double> ticks();

    /**
     * 기준선을 그릴 위치.
     *
     * <p>선형 축에서는 0이지만 로그 축에는 0이 없다. 축 시작점을 기준선으로 삼는다.</p>
     */
    default double baselineRatio() {
        return ratio(0);
    }
}
