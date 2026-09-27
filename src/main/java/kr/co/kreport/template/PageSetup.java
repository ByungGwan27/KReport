package kr.co.kreport.template;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 용지 및 여백 설정. 모든 길이 단위는 pt.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PageSetup {

    private PaperSize paperSize = PaperSize.A4;
    private Orientation orientation = Orientation.PORTRAIT;

    private double marginTop = 36;
    private double marginRight = 28;
    private double marginBottom = 36;
    private double marginLeft = 28;

    /** 용지 전체 너비 */
    @JsonIgnore
    public double getPageWidth() {
        return orientation == Orientation.LANDSCAPE ? paperSize.getHeight() : paperSize.getWidth();
    }

    /** 용지 전체 높이 */
    @JsonIgnore
    public double getPageHeight() {
        return orientation == Orientation.LANDSCAPE ? paperSize.getWidth() : paperSize.getHeight();
    }

    /** 여백을 제외한 인쇄 가능 너비 */
    @JsonIgnore
    public double getContentWidth() {
        return getPageWidth() - marginLeft - marginRight;
    }

    /** 여백을 제외한 인쇄 가능 높이 */
    @JsonIgnore
    public double getContentHeight() {
        return getPageHeight() - marginTop - marginBottom;
    }
}
