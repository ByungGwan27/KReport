package kr.co.kreport.template;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 리포트 실행 파라미터. 조회 조건 입력 폼과 SQL 바인딩에 함께 쓰인다.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ParameterDef {

    public enum DataType {
        STRING, NUMBER, DATE, BOOLEAN
    }

    /** SQL 에서 {@code :name} 으로, 표현식에서 {@code @name} 으로 참조한다 */
    private String name;

    /** 입력 폼에 표시할 이름 */
    private String label;

    private DataType dataType = DataType.STRING;

    private boolean required;

    /** 문자열로 표기한 기본값. DATE 는 yyyy-MM-dd */
    private String defaultValue;

    /** 값을 코드 목록에서 고르게 할 때 사용 */
    private List<Option> options = new ArrayList<>();

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Option {
        private String value;
        private String label;
    }
}
