package kr.co.kreport.web.dto;

import kr.co.kreport.access.GrantType;
import kr.co.kreport.domain.ReportAccessRule;

import java.time.LocalDateTime;

/** 열람 규칙 한 줄 */
public record AccessRuleView(
        Long id,
        GrantType grantType,
        String grantValue,
        String note,
        String createdBy,
        LocalDateTime createdAt) {

    public static AccessRuleView from(ReportAccessRule rule) {
        return new AccessRuleView(
                rule.getId(),
                rule.getGrantType(),
                rule.getGrantValue(),
                rule.getNote(),
                rule.getCreatedBy(),
                rule.getCreatedAt());
    }
}
