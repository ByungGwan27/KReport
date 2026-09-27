package kr.co.kreport.support;

/** 요청한 리포트가 없을 때 */
public class ReportNotFoundException extends KReportException {

    public ReportNotFoundException(String reportId) {
        super(ErrorCode.REPORT_NOT_FOUND, "리포트를 찾을 수 없습니다: " + reportId);
    }
}
