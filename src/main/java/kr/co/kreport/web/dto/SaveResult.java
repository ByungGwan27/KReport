package kr.co.kreport.web.dto;

import kr.co.kreport.domain.ReportDefinition;

import java.time.LocalDateTime;

/**
 * 정의 저장 결과.
 *
 * @param rowVersion 낙관적 잠금 버전. 디자이너가 다음 저장 때 되돌려 보내면
 *                   그사이 남이 고쳤는지 가려낼 수 있다.
 */
public record SaveResult(String reportId, LocalDateTime updatedAt, Long rowVersion) {

    public static SaveResult from(ReportDefinition definition) {
        return new SaveResult(
                definition.getReportId(),
                definition.getUpdatedAt(),
                definition.getRowVersion());
    }
}
