package kr.co.kreport.web.dto;

import kr.co.kreport.access.GrantType;

/** 열람 규칙 추가 요청 */
public record GrantRequest(GrantType grantType, String grantValue, String note) {
}
