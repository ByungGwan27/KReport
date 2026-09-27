package kr.co.kreport.template;

/** 항목 정렬 기준 */
public enum ChartSortOrder {
    /** 데이터에 나온 순서 그대로. 시간 축처럼 순서 자체가 의미일 때 쓴다. */
    NONE,
    VALUE_DESC,
    VALUE_ASC,
    CATEGORY_ASC,
    CATEGORY_DESC
}
