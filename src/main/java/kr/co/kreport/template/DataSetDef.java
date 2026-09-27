package kr.co.kreport.template;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 리포트가 소비할 데이터의 정의.
 *
 * <p>SQL 타입은 {@code :파라미터명} 플레이스홀더만 허용하고 실제 값은
 * PreparedStatement 로 바인딩한다. 문자열 치환을 쓰지 않으므로 SQL 주입이 성립하지 않는다.</p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataSetDef {

    public enum SourceType {
        /** JDBC 조회 */
        SQL,
        /** 템플릿에 포함된 고정 데이터. 디자이너 미리보기와 테스트용 */
        STATIC
    }

    private SourceType sourceType = SourceType.SQL;

    /**
     * 조회를 보낼 데이터소스 이름. 비우면 기본 연결({@code main}).
     *
     * <p>거래처나 계열 기관의 DB 를 붙일 때 쓴다. 아무 이름이나 적을 수 있는 것은 아니고,
     * 정의를 저장하거나 미리보기를 돌릴 때 그 사람이 해당 DB 에 조회를 쓸 수 있는지
     * 확인한다. 그러지 않으면 밖의 편집자가 이름만 바꿔 우리 업무 DB 를 읽을 수 있다.</p>
     */
    private String dataSource = "main";

    /** SourceType.SQL 일 때 실행할 조회 쿼리. SELECT 만 허용한다. */
    private String sql;

    /** SourceType.STATIC 일 때 사용할 행 목록 */
    private List<Map<String, Object>> rows = new ArrayList<>();

    /** 디자이너 필드 목록 표시에 쓰는 컬럼 메타. 비어 있으면 실행 시 결과셋에서 추론한다. */
    private List<Column> columns = new ArrayList<>();

    /** 조회 최대 행 수. 대량 출력으로 인한 메모리 고갈을 막는 안전장치. */
    private int maxRows = 50_000;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Column {
        private String name;
        private String label;
        private String dataType = "STRING";
    }
}
