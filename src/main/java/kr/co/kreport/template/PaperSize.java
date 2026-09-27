package kr.co.kreport.template;

/**
 * 용지 규격. 단위는 pt(1/72 inch)로 통일한다.
 * PDF 좌표계가 pt 기준이므로 내부 전 구간을 pt로 다루면 변환 오차가 생기지 않는다.
 */
public enum PaperSize {

    A3(841.89, 1190.55),
    A4(595.28, 841.89),
    A5(419.53, 595.28),
    B4(708.66, 1000.63),
    B5(498.90, 708.66),
    LETTER(612.00, 792.00),
    LEGAL(612.00, 1008.00);

    private final double width;
    private final double height;

    PaperSize(double width, double height) {
        this.width = width;
        this.height = height;
    }

    /** 세로 방향 기준 너비(pt) */
    public double getWidth() {
        return width;
    }

    /** 세로 방향 기준 높이(pt) */
    public double getHeight() {
        return height;
    }
}
