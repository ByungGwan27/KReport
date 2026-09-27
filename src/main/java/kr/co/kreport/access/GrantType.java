package kr.co.kreport.access;

/** 열람 규칙이 누구를 가리키는지 */
public enum GrantType {

    /** 부서 코드. 조직 개편이 있어도 규칙을 하나씩 고치지 않아도 되는 가장 흔한 단위 */
    DEPARTMENT,

    /** 사용자 아이디. 부서로 묶이지 않는 예외를 한 명씩 열어 줄 때 */
    USER,

    /** 권한 등급. 감사 담당처럼 직무로 열람 범위가 정해지는 경우 */
    ROLE
}
