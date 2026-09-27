package kr.co.kreport.support;

import java.util.List;

/**
 * 이 시스템이 스스로 알아차린 오류의 공통 부모.
 *
 * <p>공통 부모를 두는 이유는 예외 처리기를 줄이려는 것이 아니라, <b>화면에 보여도 되는
 * 오류</b>와 그렇지 않은 오류를 타입으로 가르기 위해서다. 이 예외가 들고 있는 문구는
 * 사용자에게 그대로 나가도 되는 말이고, 그 밖의 예외는 내부 사정이 섞여 있을 수 있으므로
 * 일반 문구로 덮고 로그에만 남긴다.</p>
 *
 * <p>{@code details} 는 사용자가 고칠 수 있는 항목별 안내다. 조회 조건 세 개가 한꺼번에
 * 잘못됐을 때 하나씩 알려 주려면 문구 하나로는 부족하다.</p>
 */
public class KReportException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<String> details;

    public KReportException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage(), List.of(), null);
    }

    public KReportException(ErrorCode errorCode, String message) {
        this(errorCode, message, List.of(), null);
    }

    public KReportException(ErrorCode errorCode, String message, Throwable cause) {
        this(errorCode, message, List.of(), cause);
    }

    public KReportException(ErrorCode errorCode, String message, List<String> details) {
        this(errorCode, message, details, null);
    }

    public KReportException(ErrorCode errorCode, String message, List<String> details,
                            Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /** 사용자가 하나씩 고칠 수 있는 항목별 안내 */
    public List<String> getDetails() {
        return details;
    }
}
