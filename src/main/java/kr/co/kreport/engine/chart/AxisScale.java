package kr.co.kreport.engine.chart;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 값 축의 눈금 범위와 간격.
 *
 * <p>막대 차트의 기준선은 항상 0이다. 0이 아닌 값에서 축을 끊으면 막대 길이의 비율이
 * 실제 값의 비율과 달라져서, 차이가 실제보다 크게 보인다.</p>
 */
public record AxisScale(double min, double max, double step) implements ValueAxis {

    private static final double[] NICE_STEPS = {1, 2, 2.5, 5, 10};

    /**
     * 소수 자리 없이 읽어야 할 때 쓰는 후보.
     *
     * <p>건수처럼 정수인 값에 2.5 간격이 잡히면 눈금이 0·2.5·5·7.5·10 로 놓이고,
     * 이것을 정수 서식으로 찍으면 0·3·5·8·10 이 되어 간격이 들쭉날쭉해 보인다.</p>
     */
    private static final double[] INTEGER_STEPS = {1, 2, 5, 10};

    public static AxisScale of(BigDecimal dataMin, BigDecimal dataMax, int targetTicks,
                               boolean includeZero) {
        return of(dataMin, dataMax, targetTicks, includeZero, false);
    }

    /**
     * 데이터 범위를 사람이 읽기 좋은 눈금으로 맞춘다.
     *
     * @param includeZero 기준선 0을 반드시 포함할지 여부
     * @param integerOnly 눈금을 정수로만 놓을지 여부
     */
    public static AxisScale of(BigDecimal dataMin, BigDecimal dataMax, int targetTicks,
                               boolean includeZero, boolean integerOnly) {
        double lo = dataMin == null ? 0 : dataMin.doubleValue();
        double hi = dataMax == null ? 0 : dataMax.doubleValue();

        if (includeZero) {
            lo = Math.min(0, lo);
            hi = Math.max(0, hi);
        }
        if (lo == hi) {
            // 값이 모두 같으면 눈금을 만들 수 없으므로 최소한의 폭을 준다
            if (hi == 0) {
                return new AxisScale(0, 1, 1);
            }
            hi = hi > 0 ? hi * 1.2 : hi * 0.8;
        }

        int ticks = Math.max(2, targetTicks);
        double rawStep = (hi - lo) / ticks;
        double step = niceStep(rawStep, integerOnly);

        double niceMin = Math.floor(lo / step) * step;
        double niceMax = Math.ceil(hi / step) * step;

        // 부동소수 누적으로 마지막 눈금이 최댓값에 못 미치는 경우를 막는다
        if (niceMax < hi) {
            niceMax += step;
        }
        return new AxisScale(niceMin, niceMax, step);
    }

    /** 1, 2, 2.5, 5, 10 × 10^n 중 rawStep 이상인 가장 작은 값 */
    private static double niceStep(double rawStep, boolean integerOnly) {
        if (rawStep <= 0) {
            return 1;
        }
        double exponent = Math.floor(Math.log10(rawStep));
        double magnitude = Math.pow(10, exponent);
        double normalized = rawStep / magnitude;

        // 자릿수가 1 미만이면 후보에 소수가 섞이므로 정수 제약에서는 최소 1을 쓴다
        double[] candidates = integerOnly && magnitude < 1 ? new double[]{1} : NICE_STEPS;
        if (integerOnly && magnitude >= 1) {
            candidates = INTEGER_STEPS;
        }

        for (double candidate : candidates) {
            double step = candidate * magnitude;
            if (normalized <= candidate) {
                return integerOnly ? Math.max(1, Math.round(step)) : step;
            }
        }
        double step = 10 * magnitude;
        return integerOnly ? Math.max(1, Math.round(step)) : step;
    }

    public double span() {
        return max - min;
    }

    /** 값을 0~1 비율로. 축 범위 밖이면 잘라 낸다. */
    @Override
    public double ratio(double value) {
        double s = span();
        if (s == 0) {
            return 0;
        }
        return Math.max(0, Math.min(1, (value - min) / s));
    }

    @Override
    public List<Double> ticks() {
        List<Double> ticks = new ArrayList<>();
        // 곱셈으로 만들어야 step 을 반복해서 더할 때 생기는 오차가 쌓이지 않는다
        int count = (int) Math.round(span() / step);
        for (int i = 0; i <= count; i++) {
            ticks.add(min + step * i);
        }
        return ticks;
    }
}
