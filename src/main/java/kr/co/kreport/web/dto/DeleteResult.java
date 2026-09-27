package kr.co.kreport.web.dto;

/** 정의 삭제 결과 */
public record DeleteResult(String reportId, boolean deleted) {

    public static DeleteResult of(String reportId) {
        return new DeleteResult(reportId, true);
    }
}
