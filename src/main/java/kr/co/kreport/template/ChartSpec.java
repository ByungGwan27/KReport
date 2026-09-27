package kr.co.kreport.template;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 차트 요소의 설정.
 *
 * <p>차트는 놓인 밴드가 곧 집계 범위다. 리포트 꼬리말에 놓으면 전체를, 그룹 꼬리말에 놓으면
 * 그 그룹의 행만 집계한다. 별도로 범위를 지정하게 하면 "어느 데이터를 그린 건지" 가
 * 정의와 화면 사이에서 어긋나기 쉽다.</p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChartSpec {

    public enum LegendMode {
        /** 계열이 둘 이상이면 표시 */
        AUTO,
        SHOW,
        HIDE
    }

    /** 값 축의 눈금 간격을 정하는 방식 */
    public enum ValueScale {
        /** 같은 차이가 같은 거리. 기본값 */
        LINEAR,
        /**
         * 같은 배수가 같은 거리. 값이 몇 자릿수씩 차이 날 때 쓴다.
         *
         * <p>꺾은선에서만 쓸 수 있다. 막대는 길이로 크기를 말하는데 로그 축에서는
         * 길이 비율이 값 비율과 달라지고, 기준선 0이 축에 존재할 수 없기 때문이다.</p>
         */
        LOG
    }

    private ChartType type = ChartType.COLUMN;

    /** 항목(축 라벨)을 만드는 표현식 */
    private String categoryExpression;

    private List<ChartSeriesDef> series = new ArrayList<>();

    private ChartSortOrder sort = ChartSortOrder.NONE;

    /**
     * 표시할 최대 항목 수. 0 이면 차트 종류의 기본 상한을 쓴다.
     * 상한을 넘으면 큰 값부터 남기고 나머지는 하나로 묶는다.
     */
    private int maxCategories;

    /** 묶인 항목의 이름 */
    private String otherLabel = "기타";

    private LegendMode legend = LegendMode.AUTO;

    /**
     * 값을 마크 옆에 직접 적을지 여부.
     *
     * <p>기본은 끔이다. 모든 점에 숫자를 붙이면 읽히지 않는다. 계열이 하나이고 항목이 적을 때만
     * 켜는 것을 전제로 한다.</p>
     */
    private boolean showValues;

    /** 값 표기 포맷. 축 눈금과 직접 라벨에 함께 쓴다. */
    private String valueFormat = "#,##0";

    private String title;
    private String categoryAxisTitle;
    private String valueAxisTitle;

    /** 값 축 눈금선 표시 */
    private boolean showGrid = true;

    /**
     * 값 축의 눈금 방식.
     *
     * <p>{@code LOG} 로 두어도 데이터에 0 이하가 섞여 있으면 로그를 적용할 수 없으므로
     * 선형으로 되돌려 그린다. 그려지지 않는 것보다 눈금 방식이 달라지는 편이 낫다.</p>
     */
    private ValueScale valueScale = ValueScale.LINEAR;

    /** 계열 색 팔레트. 비우면 내장 팔레트를 쓴다. */
    private List<String> palette = new ArrayList<>();

    @JsonIgnore
    public int effectiveMaxCategories() {
        return maxCategories > 0 ? maxCategories : type.getMaxCategories();
    }

    @JsonIgnore
    public boolean showLegend() {
        return switch (legend) {
            case SHOW -> true;
            case HIDE -> false;
            // 계열이 하나면 제목이 곧 이름이라 범례 상자가 군더더기가 된다.
            // 원 그래프는 계열이 아니라 항목을 구분해야 하므로 범례가 필요하다.
            case AUTO -> series.size() > 1 || type.isCircular();
        };
    }
}
