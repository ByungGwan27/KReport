package kr.co.kreport.access;

/**
 * 리포트 열람 통제 방식.
 *
 * <p>규칙이 하나도 없을 때 "전부 공개"로 해석하지 않고 굳이 표시를 따로 두는 이유는,
 * 규칙을 실수로 지웠을 때 리포트가 조용히 열리는 일을 막기 위해서다. {@link #RESTRICTED}
 * 인데 규칙이 비어 있으면 아무도 보지 못한다. 잘못 설정했을 때 <b>닫히는 쪽으로</b>
 * 틀리는 편이, 인사 자료가 전 직원에게 열리는 것보다 낫다.</p>
 */
public enum AccessMode {

    /** 로그인한 조회 권한자면 모두 본다 */
    PUBLIC,

    /** 열람 규칙에 걸린 사람만 본다 */
    RESTRICTED;

    public boolean isRestricted() {
        return this == RESTRICTED;
    }
}
