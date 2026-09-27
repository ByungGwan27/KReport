package kr.co.kreport.template;

public enum ElementType {

    /** 고정 문자열 */
    LABEL,
    /** 표현식 평가 결과를 출력 */
    TEXT,
    /** 직선 */
    LINE,
    /** 사각형(테두리/배경) */
    RECT,
    /** 이미지(URL 또는 classpath 리소스) */
    IMAGE,
    /** 1차원 바코드 (CODE128) */
    BARCODE,
    /** QR 코드 */
    QRCODE,
    /** 차트 */
    CHART
}
