package kr.co.kreport.config;

/**
 * 리포트 권한 등급.
 *
 * <p>조회와 편집을 반드시 나눈다. 리포트 정의를 저장할 수 있다는 것은 조회 SQL 을
 * 마음대로 쓸 수 있다는 뜻이고, 그것은 곧 업무 DB 전체를 읽을 수 있다는 뜻이다.
 * 담당자에게 편집 권한을 함께 주면 열람 범위 통제가 무의미해진다.</p>
 */
public final class ReportRole {

    /** 등록된 리포트를 조회하고 파일로 내려받는다 */
    public static final String VIEWER = "VIEWER";

    /**
     * 리포트 정의를 만들고 고친다.
     * 조회 SQL 을 직접 쓰는 자리이므로 DB 열람 권한과 같은 무게로 다뤄야 한다.
     */
    public static final String DESIGNER = "DESIGNER";

    /** 정의 삭제와 실행 이력 열람 */
    public static final String ADMIN = "ADMIN";

    private ReportRole() {
    }
}
