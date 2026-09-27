package kr.co.kreport.template;

/**
 * 밴드 종류. 출력 순서와 반복 규칙이 종류별로 다르다.
 *
 * <pre>
 * REPORT_HEADER  최초 1회
 * PAGE_HEADER    매 페이지 상단
 * GROUP_HEADER   그룹 시작 시
 * DETAIL         데이터 행마다
 * GROUP_FOOTER   그룹 종료 시
 * PAGE_FOOTER    매 페이지 하단
 * REPORT_FOOTER  최종 1회
 * </pre>
 */
public enum BandType {

    REPORT_HEADER(false),
    PAGE_HEADER(true),
    GROUP_HEADER(false),
    DETAIL(false),
    GROUP_FOOTER(false),
    PAGE_FOOTER(true),
    REPORT_FOOTER(false);

    private final boolean pageScoped;

    BandType(boolean pageScoped) {
        this.pageScoped = pageScoped;
    }

    /** 페이지마다 고정 출력되는 밴드인지 여부 */
    public boolean isPageScoped() {
        return pageScoped;
    }

    public boolean isGroupScoped() {
        return this == GROUP_HEADER || this == GROUP_FOOTER;
    }
}
