package kr.co.kreport.engine.data;

import kr.co.kreport.support.ErrorCode;
import kr.co.kreport.support.KReportException;

/** 데이터셋 조회 실패 */
public class DataSetException extends KReportException {

    public DataSetException(String message) {
        super(ErrorCode.DATASET_FAILED, message);
    }

    public DataSetException(String message, Throwable cause) {
        super(ErrorCode.DATASET_FAILED, message, cause);
    }

    public DataSetException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
