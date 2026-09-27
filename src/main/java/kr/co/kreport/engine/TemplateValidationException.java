package kr.co.kreport.engine;

import kr.co.kreport.support.ErrorCode;
import kr.co.kreport.support.KReportException;

import java.util.List;

/** 리포트 정의 검증 실패 */
public class TemplateValidationException extends KReportException {

    public TemplateValidationException(List<String> errors) {
        super(ErrorCode.TEMPLATE_INVALID, ErrorCode.TEMPLATE_INVALID.getDefaultMessage(), errors);
    }

    /** @deprecated 항목별 안내는 {@link #getDetails()} 로 읽는다 */
    @Deprecated(forRemoval = true)
    public List<String> getErrors() {
        return getDetails();
    }
}
