package kr.co.kreport.web.dto;

import java.util.List;

/** 디자이너가 필드 칩을 그리는 데 쓰는 컬럼 목록 */
public record FieldList(List<String> columns) {
}
