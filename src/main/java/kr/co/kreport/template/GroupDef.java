package kr.co.kreport.template;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 그룹 정의. 정렬은 데이터셋 SQL 이 책임지며, 엔진은 인접한 동일 키를 한 그룹으로 본다.
 * (SQL 의 ORDER BY 가 그룹 기준과 어긋나면 그룹이 쪼개지므로 검증 단계에서 경고한다.)
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GroupDef {

    /** 밴드에서 참조할 그룹명 */
    private String name;

    /** 그룹 키 표현식. 보통 {@code {컬럼명}} 형태 */
    private String expression;

    /** 그룹이 바뀔 때 페이지를 넘길지 여부 */
    private boolean pageBreak;

    /** pageBreak 시 페이지 번호를 1부터 다시 매길지 여부 */
    private boolean resetPageNumber;

    /**
     * 그룹 헤더를 페이지가 넘어가도 반복 출력할지 여부.
     * 긴 그룹이 여러 페이지에 걸칠 때 항목 제목을 유지하는 용도.
     */
    private boolean repeatHeaderOnNewPage = true;
}
