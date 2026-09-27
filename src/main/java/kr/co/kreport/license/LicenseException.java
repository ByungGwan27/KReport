package kr.co.kreport.license;

import kr.co.kreport.support.ErrorCode;
import kr.co.kreport.support.KReportException;

/** 라이선스가 없거나, 만료되었거나, 서명이 맞지 않을 때 */
public class LicenseException extends KReportException {

    public LicenseException(String message) {
        super(ErrorCode.LICENSE_INVALID, message);
    }

    public LicenseException(ErrorCode code, String message) {
        super(code, message);
    }
}
