package kr.co.kreport.web.dto;

import kr.co.kreport.access.AccessMode;

import java.util.List;

/**
 * 리포트 한 건의 열람 설정 전체.
 *
 * @param mode  PUBLIC 이면 아래 규칙은 적용되지 않는다. 규칙을 지우지 않고 잠시 공개로
 *              돌릴 수 있게, 모드를 바꿔도 규칙은 남겨 둔다.
 * @param rules 등록된 규칙
 */
public record AccessSettings(String reportId, AccessMode mode, List<AccessRuleView> rules) {
}
