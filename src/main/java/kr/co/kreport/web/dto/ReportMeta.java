package kr.co.kreport.web.dto;

import kr.co.kreport.template.ParameterDef;
import kr.co.kreport.template.ReportTemplate;

import java.util.List;

/** 조회 조건 입력 폼을 그리기 위한 메타 정보 */
public record ReportMeta(
        String reportId,
        String name,
        String description,
        List<ParameterDef> parameters) {

    public static ReportMeta from(ReportTemplate template) {
        return new ReportMeta(
                template.getReportId(),
                template.getName(),
                template.getDescription(),
                template.getParameters());
    }
}
