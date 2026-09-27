package kr.co.kreport.engine.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 리포트가 소비하는 조회 결과.
 *
 * <p>컬럼명 대소문자는 DB 벤더마다 다르게 돌아온다(H2 는 대문자, PostgreSQL 은 소문자).
 * 템플릿이 특정 벤더 표기에 묶이지 않도록 대문자 색인을 따로 들고 있다가
 * 원래 이름으로 찾지 못하면 그쪽으로 한 번 더 조회한다.</p>
 */
public final class DataTable {

    private final List<String> columns;
    private final List<Map<String, Object>> rows;
    private final Map<String, String> columnAlias;

    public DataTable(List<String> columns, List<Map<String, Object>> rows) {
        this.columns = List.copyOf(columns);
        this.rows = rows;
        this.columnAlias = new HashMap<>();
        for (String c : columns) {
            columnAlias.putIfAbsent(c.toUpperCase(), c);
        }
    }

    public static DataTable empty() {
        return new DataTable(List.of(), new ArrayList<>());
    }

    public List<String> getColumns() {
        return columns;
    }

    public List<Map<String, Object>> getRows() {
        return rows;
    }

    /** 대문자 컬럼명 -> 실제 컬럼명 */
    public Map<String, String> getColumnAlias() {
        return columnAlias;
    }

    public int size() {
        return rows.size();
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    public Map<String, Object> row(int index) {
        return rows.get(index);
    }

    /** 컬럼 순서를 유지한 빈 행. 데이터가 없을 때 헤더만 있는 리포트를 그리는 데 쓴다. */
    public Map<String, Object> blankRow() {
        Map<String, Object> row = new LinkedHashMap<>();
        for (String c : columns) {
            row.put(c, null);
        }
        return row;
    }
}
