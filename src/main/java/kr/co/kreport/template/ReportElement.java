package kr.co.kreport.template;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 밴드 안에 배치되는 단위 요소.
 * 좌표(x, y)는 소속 밴드의 좌상단을 원점으로 하는 상대 좌표(pt)다.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReportElement {

    /** 디자이너에서 부여하는 식별자 */
    private String id;

    private ElementType type = ElementType.LABEL;

    private double x;
    private double y;
    private double width = 80;
    private double height = 18;

    /** LABEL 의 고정 문자열 */
    private String text;

    /** TEXT / BARCODE / QRCODE 의 값 표현식 */
    private String expression;

    /**
     * 출력 포맷.
     * 숫자는 {@link java.text.DecimalFormat} 패턴(예: {@code #,##0}),
     * 날짜는 {@link java.time.format.DateTimeFormatter} 패턴(예: {@code yyyy-MM-dd})을 따른다.
     */
    private String format;

    /** 값이 null 이거나 비었을 때 대신 출력할 문자열 */
    private String nullText = "";

    /** 이 표현식이 참일 때만 출력. null 이면 항상 출력 */
    private String printWhen;

    /**
     * 직전 행과 값이 같으면 출력하지 않는다. 그룹 키 컬럼 반복 제거에 쓴다.
     * DETAIL 밴드에서만 의미가 있다.
     */
    private boolean suppressRepeat;

    /** IMAGE 의 원본 경로. classpath: 접두어 또는 http(s) URL */
    private String source;

    /** CHART 의 설정 */
    private ChartSpec chart;

    private ElementStyle style = new ElementStyle();

    public double getRight() {
        return x + width;
    }

    public double getBottom() {
        return y + height;
    }

    public boolean isTextual() {
        return type == ElementType.LABEL || type == ElementType.TEXT;
    }
}
