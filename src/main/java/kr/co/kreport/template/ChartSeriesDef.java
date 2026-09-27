package kr.co.kreport.template;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 차트의 계열 하나.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChartSeriesDef {

    /** 범례에 표시할 이름 */
    private String name;

    /** 행마다 평가할 값 표현식. 보통 {@code {금액}} 형태 */
    private String expression;

    private ChartAggregation aggregation = ChartAggregation.SUM;

    /**
     * 계열 색. 비우면 팔레트에서 슬롯 순서대로 가져온다.
     *
     * <p>순서를 바꿔 쓰지 않는 이유가 있다. 색은 순위가 아니라 대상을 따라가야 해서,
     * 조건이 바뀌어 계열이 하나 빠졌을 때 남은 계열의 색이 바뀌면 안 된다.</p>
     */
    private String color;
}
