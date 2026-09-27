package kr.co.kreport.web.dto;

import kr.co.kreport.domain.ReportDefinition;

import java.time.LocalDateTime;

/** 목록 화면과 목록 API 가 쓰는 리포트 한 줄 */
public record ReportSummary(
        String reportId,
        String name,
        String description,
        String category,
        boolean active,
        LocalDateTime updatedAt,
        String updatedBy) {

    public static ReportSummary from(ReportDefinition definition) {
        return new ReportSummary(
                definition.getReportId(),
                definition.getName(),
                definition.getDescription(),
                definition.getCategory(),
                definition.isActive(),
                definition.getUpdatedAt(),
                definition.getUpdatedBy());
    }
}
