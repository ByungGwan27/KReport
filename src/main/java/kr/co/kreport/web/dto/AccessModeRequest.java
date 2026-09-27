package kr.co.kreport.web.dto;

import kr.co.kreport.access.AccessMode;

/** 열람 통제 방식 변경 요청 */
public record AccessModeRequest(AccessMode mode) {
}
