package kr.co.kreport.engine.chart;

import java.util.List;

/**
 * 차트 색과 차트 크롬(축, 눈금선, 글자) 색을 모아 둔 곳.
 *
 * <p>계열 색은 <b>고정 순서로 배정하고 순환시키지 않는다</b>. 9번째 계열에 색을 새로
 * 만들어 붙이면 8번째까지 지켜 온 구분 보장이 깨지므로, 상한을 넘는 항목은 색을 늘리는 대신
 * "기타"로 묶는다.</p>
 *
 * <p>여기 실린 순서는 인접 계열 간 색각 이상 분리도(OKLab ΔE ≥ 8)와 정상 시야 분리도(≥ 15)를
 * 모두 통과하도록 정해진 것이다. 임의로 재배열하면 그 보장이 사라진다.</p>
 */
public final class ChartPalette {

    /** 계열 색. 흰 지면 기준으로 선택된 값이다. */
    public static final List<String> SERIES = List.of(
            "#2a78d6",  // blue
            "#eb6834",  // orange
            "#1baf7a",  // aqua
            "#eda100",  // yellow
            "#e87ba4",  // magenta
            "#008300",  // green
            "#4a3aa7",  // violet
            "#e34948"   // red
    );

    /** 상한을 넘겨 묶인 "기타" 항목 색. 계열 색과 겹치지 않는 중립 회색. */
    public static final String OTHER = "#9aa3ad";

    // --- 차트 크롬. 데이터보다 뒤로 물러나야 하므로 모두 옅은 잉크 계열이다.

    /** 눈금선. 지면에서 한 단계만 어두운 실선 헤어라인 */
    public static final String GRID = "#e5e7eb";
    /** 축선 */
    public static final String AXIS = "#9ca3af";
    /** 항목명, 눈금 숫자 */
    public static final String TEXT_SECONDARY = "#52514e";
    /** 차트 제목, 직접 라벨 값 */
    public static final String TEXT_PRIMARY = "#1f2937";
    /** 지면 색. 마크 사이 간격과 겹침 테두리에 쓴다. */
    public static final String SURFACE = "#ffffff";

    private ChartPalette() {
    }

    /**
     * 슬롯 번호에 해당하는 색.
     *
     * @param index 0부터. 팔레트를 넘어서면 마지막 색을 그대로 쓴다(순환하지 않는다).
     */
    public static String series(int index) {
        if (index < 0) {
            return SERIES.get(0);
        }
        return index < SERIES.size() ? SERIES.get(index) : SERIES.get(SERIES.size() - 1);
    }

    /** 사용자 지정 팔레트가 있으면 그쪽을, 없으면 기본 팔레트를 쓴다 */
    public static String series(List<String> custom, int index) {
        if (custom != null && !custom.isEmpty()) {
            return custom.get(Math.min(index, custom.size() - 1));
        }
        return series(index);
    }
}
