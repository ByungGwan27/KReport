package kr.co.kreport.engine.expression;

import java.util.HashMap;
import java.util.Map;

/**
 * 표현식 평가에 필요한 주변 상태.
 *
 * <p>한 번의 리포트 실행 동안 인스턴스 하나를 재사용하면서 현재 행과 페이지 정보만
 * 갈아 끼운다. 행마다 컨텍스트를 새로 만들면 수만 행 리포트에서 불필요한 할당이 쌓인다.</p>
 */
public final class EvalContext {

    /** 집계값 조회 창구. 레이아웃 전에 계산된 집계표를 가리킨다. */
    public interface AggregateLookup {
        /**
         * @param key      {@link ExprNode.Aggregate#key()}
         * @param scope    그룹명. null 이면 리포트 전체
         * @param rowIndex 현재 데이터 행 번호(0부터). 그룹 인스턴스를 찾는 데 쓴다.
         */
        Object get(String key, String scope, int rowIndex);
    }

    private static final AggregateLookup NO_AGGREGATE = (key, scope, rowIndex) -> null;

    private Map<String, Object> row = Map.of();
    private final Map<String, Object> parameters = new HashMap<>();
    private final Map<String, Object> variables = new HashMap<>();
    private AggregateLookup aggregateLookup = NO_AGGREGATE;
    private int rowIndex = -1;

    /**
     * 컬럼명을 대소문자 구분 없이 찾기 위한 보조 색인.
     * JDBC 드라이버마다 라벨 대소문자가 달라서 템플릿이 DB 벤더에 묶이는 것을 막는다.
     */
    private Map<String, String> columnAlias = Map.of();

    public void setRow(Map<String, Object> row, int rowIndex) {
        this.row = row == null ? Map.of() : row;
        this.rowIndex = rowIndex;
    }

    public void setColumnAlias(Map<String, String> columnAlias) {
        this.columnAlias = columnAlias == null ? Map.of() : columnAlias;
    }

    public void putParameter(String name, Object value) {
        parameters.put(name, value);
    }

    public void putParameters(Map<String, Object> values) {
        if (values != null) {
            parameters.putAll(values);
        }
    }

    public void putVariable(String name, Object value) {
        variables.put(name, value);
    }

    public void setAggregateLookup(AggregateLookup lookup) {
        this.aggregateLookup = lookup == null ? NO_AGGREGATE : lookup;
    }

    public Map<String, Object> getRow() {
        return row;
    }

    public int getRowIndex() {
        return rowIndex;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public Object field(String name) {
        if (row.containsKey(name)) {
            return row.get(name);
        }
        String actual = columnAlias.get(name.toUpperCase());
        return actual == null ? null : row.get(actual);
    }

    public boolean hasField(String name) {
        return row.containsKey(name) || columnAlias.containsKey(name.toUpperCase());
    }

    public Object parameter(String name) {
        return parameters.get(name);
    }

    public Object variable(String name) {
        if (variables.containsKey(name)) {
            return variables.get(name);
        }
        // 식별자를 컬럼명으로도 한 번 더 찾아 준다. 중괄호를 빠뜨린 식이 흔하기 때문.
        return field(name);
    }

    public Object aggregate(String key, String scope) {
        return aggregateLookup.get(key, scope, rowIndex);
    }
}
