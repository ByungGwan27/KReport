package kr.co.kreport.engine.data;

import kr.co.kreport.support.ErrorCode;
import kr.co.kreport.support.KReportException;

import java.util.List;

/**
 * 조회 조건 검증 실패.
 *
 * <p>조건이 여럿 잘못됐을 때 하나씩 고칠 수 있도록 항목별 안내를 함께 싣는다.</p>
 */
public class ParameterBindingException extends KReportException {

    public ParameterBindingException(List<String> errors) {
        super(ErrorCode.PARAMETER_INVALID, ErrorCode.PARAMETER_INVALID.getDefaultMessage(), errors);
    }

    /** @deprecated 항목별 안내는 {@link #getDetails()} 로 읽는다 */
    @Deprecated(forRemoval = true)
    public List<String> getErrors() {
        return getDetails();
    }
}
