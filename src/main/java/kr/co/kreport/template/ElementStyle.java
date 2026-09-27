package kr.co.kreport.template;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 요소의 표현 속성. 색상은 {@code #RRGGBB} 문자열로 지정한다.
 * 모든 길이 단위는 pt.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ElementStyle {

    private String fontFamily = "default";
    private double fontSize = 9.0;
    private boolean bold;
    private boolean italic;
    private boolean underline;

    /** 글자색 */
    private String color = "#000000";
    /** 배경색. null 이면 투명 */
    private String backgroundColor;

    private HorizontalAlign align = HorizontalAlign.LEFT;
    private VerticalAlign valign = VerticalAlign.MIDDLE;

    /** 테두리 두께(pt). 0 이면 그리지 않는다. */
    private double borderTop;
    private double borderRight;
    private double borderBottom;
    private double borderLeft;
    private String borderColor = "#000000";

    /** 셀 내부 좌우 여백 */
    private double paddingLeft = 2.0;
    private double paddingRight = 2.0;

    /** 폭을 넘는 텍스트의 줄바꿈 여부. false 면 잘라낸다. */
    private boolean wrap;

    /** 네 변 테두리를 한 번에 지정 */
    public void setBorder(double width) {
        this.borderTop = width;
        this.borderRight = width;
        this.borderBottom = width;
        this.borderLeft = width;
    }

    public boolean hasBorder() {
        return borderTop > 0 || borderRight > 0 || borderBottom > 0 || borderLeft > 0;
    }

    public ElementStyle copy() {
        ElementStyle s = new ElementStyle();
        s.fontFamily = fontFamily;
        s.fontSize = fontSize;
        s.bold = bold;
        s.italic = italic;
        s.underline = underline;
        s.color = color;
        s.backgroundColor = backgroundColor;
        s.align = align;
        s.valign = valign;
        s.borderTop = borderTop;
        s.borderRight = borderRight;
        s.borderBottom = borderBottom;
        s.borderLeft = borderLeft;
        s.borderColor = borderColor;
        s.paddingLeft = paddingLeft;
        s.paddingRight = paddingRight;
        s.wrap = wrap;
        return s;
    }
}
