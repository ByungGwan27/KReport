package kr.co.kreport.engine.chart;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 밑이 10인 로그 축.
 *
 * <p>값이 몇 자릿수씩 차이 나는 데이터에서 쓴다. 선형 축에서는 큰 값 하나가 축을 독차지해
 * 나머지가 모두 바닥에 깔려 버리는데, 로그 축은 <b>비율이 같으면 같은 간격</b>으로 놓이므로
 * 작은 값들 사이의 차이도 함께 보인다.</p>
 *
 * <p>대신 읽는 사람이 축을 확인하지 않으면 오해한다. 눈으로 보이는 거리가 값의 차이가 아니라
 * 배수를 뜻하기 때문이다. 그래서 길이로 크기를 말하는 막대에는 쓰지 않는다.
 * 그 판단은 {@link ChartLayoutEngine} 과 템플릿 검증이 맡는다.</p>
 */
public record LogScale(double min, double max) implements ValueAxis {

    /** 눈금 개수가 이보다 많아지면 배수 후보를 줄인다 */
    private static final int TICK_LIMIT = 9;

    /**
     * 데이터 범위를 10의 거듭제곱 경계까지 넓힌 로그 축을 만든다.
     *
     * @param dataMin 0보다 커야 한다
     */
    public static LogScale of(BigDecimal dataMin, BigDecimal dataMax) {
        double lo = dataMin == null ? 1 : dataMin.doubleValue();
        double hi = dataMax == null ? 1 : dataMax.doubleValue();

        if (lo <= 0 || hi <= 0) {
            throw new IllegalArgumentException("로그 축은 0보다 큰 값만 담을 수 있습니다.");
        }
        if (lo > hi) {
            double swap = lo;
            lo = hi;
            hi = swap;
        }

        double niceMin = Math.pow(10, Math.floor(Math.log10(lo)));
        double niceMax = Math.pow(10, Math.ceil(Math.log10(hi)));

        // 데이터가 정확히 10의 거듭제곱이면 위아래가 같아져 축의 폭이 0이 된다
        if (niceMax <= niceMin) {
            niceMax = niceMin * 10;
        }
        return new LogScale(niceMin, niceMax);
    }

    /** 데이터에 0 이하가 섞여 있으면 로그 축을 쓸 수 없다 */
    public static boolean isUsable(ChartDataset data) {
        boolean anyValue = false;
        for (ChartDataset.Series s : data.series()) {
            for (BigDecimal v : s.values()) {
                if (v == null) {
                    continue;
                }
                if (v.signum() <= 0) {
                    return false;
                }
                anyValue = true;
            }
        }
        return anyValue;
    }

    @Override
    public double ratio(double value) {
        if (value <= 0) {
            return 0;
        }
        double span = Math.log10(max) - Math.log10(min);
        if (span <= 0) {
            return 0;
        }
        double position = (Math.log10(value) - Math.log10(min)) / span;
        return Math.max(0, Math.min(1, position));
    }

    /**
     * 10의 거듭제곱마다 눈금을 놓고, 자릿수 폭이 좁으면 그 사이에 2와 5를 더 넣는다.
     * 거듭제곱 눈금만으로는 범위가 좁을 때 눈금이 두어 개밖에 남지 않는다.
     */
    @Override
    public List<Double> ticks() {
        int lo = (int) Math.round(Math.log10(min));
        int hi = (int) Math.round(Math.log10(max));
        int decades = Math.max(1, hi - lo);

        int[] multipliers = decades <= 2
                ? new int[]{1, 2, 5}
                : (decades <= 4 ? new int[]{1, 5} : new int[]{1});

        List<Double> ticks = collect(lo, hi, multipliers);
        if (ticks.size() > TICK_LIMIT) {
            ticks = collect(lo, hi, new int[]{1});
        }
        return ticks;
    }

    private List<Double> collect(int lo, int hi, int[] multipliers) {
        List<Double> ticks = new ArrayList<>();
        for (int exponent = lo; exponent <= hi; exponent++) {
            double decade = Math.pow(10, exponent);
            for (int m : multipliers) {
                double tick = decade * m;
                if (tick >= min - 1e-9 && tick <= max + 1e-9) {
                    ticks.add(tick);
                }
            }
        }
        return ticks;
    }

    /** 로그 축에는 0이 없으므로 축 시작점이 기준선이다 */
    @Override
    public double baselineRatio() {
        return 0;
    }
}
