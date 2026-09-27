package kr.co.kreport.web.dto;

import kr.co.kreport.support.ErrorCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 오류 응답.
 *
 * @param code    기관 연계에서 쓰는 오류 코드. 문구는 다듬느라 바뀌어도 이 값은 계약이라 유지된다.
 * @param message 화면에 그대로 띄울 수 있는 문구
 * @param details 사용자가 하나씩 고칠 수 있는 항목별 안내
 */
public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String code,
        String message,
        List<String> details) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return of(errorCode, errorCode.getDefaultMessage(), List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return of(errorCode, message, List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, String message, List<String> details) {
        return new ErrorResponse(
                LocalDateTime.now(),
                errorCode.getStatus().value(),
                errorCode.getCode(),
                message == null || message.isBlank() ? errorCode.getDefaultMessage() : message,
                details == null ? List.of() : details);
    }
}
