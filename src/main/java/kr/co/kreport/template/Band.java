package kr.co.kreport.template;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 리포트의 수평 구획. 높이는 고정이며 내부 요소를 상대 좌표로 담는다.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Band {

    private BandType type = BandType.DETAIL;

    /**
     * GROUP_HEADER / GROUP_FOOTER 일 때 대상 그룹명.
     * {@link ReportTemplate#getGroups()} 의 name 과 일치해야 한다.
     */
    private String groupName;

    private double height = 20;

    /** 이 표현식이 참일 때만 출력. null 이면 항상 출력 */
    private String printWhen;

    /** 밴드 배경색. null 이면 투명 */
    private String backgroundColor;

    private List<ReportElement> elements = new ArrayList<>();

    public Band add(ReportElement element) {
        this.elements.add(element);
        return this;
    }

    /** 요소들이 실제로 차지하는 최하단 y 좌표 */
    public double contentBottom() {
        double bottom = 0;
        for (ReportElement e : elements) {
            bottom = Math.max(bottom, e.getBottom());
        }
        return bottom;
    }
}
