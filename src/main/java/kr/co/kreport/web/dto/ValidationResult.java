package kr.co.kreport.web.dto;

import java.util.List;

/** 저장 전 정의 검사 결과 */
public record ValidationResult(boolean valid, List<String> errors) {

    public static ValidationResult of(List<String> errors) {
        return new ValidationResult(errors.isEmpty(), errors);
    }
}
