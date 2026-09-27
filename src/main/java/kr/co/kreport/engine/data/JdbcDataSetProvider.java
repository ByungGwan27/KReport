package kr.co.kreport.engine.data;

import kr.co.kreport.config.KReportProperties;
import kr.co.kreport.template.DataSetDef;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JDBC 조회로 데이터셋을 채운다.
 */
@Slf4j
@Component
public class JdbcDataSetProvider implements DataSetProvider {

    private final DataSourceRegistry registry;

    /** 조회 제한시간(초). 무거운 쿼리가 커넥션을 붙잡는 것을 막는다. */
    private final int queryTimeoutSeconds;

    public JdbcDataSetProvider(DataSourceRegistry registry, KReportProperties properties) {
        this.registry = registry;
        this.queryTimeoutSeconds = properties.getQueryTimeoutSeconds();
    }

    @Override
    public boolean supports(DataSetDef.SourceType type) {
        return type == DataSetDef.SourceType.SQL;
    }

    @Override
    public DataTable fetch(DataSetDef def, Map<String, Object> parameters) {
        SqlGuard.verifySelect(def.getSql());
        NamedParameterSql parsed = NamedParameterSql.parse(def.getSql());

        // 어느 DB 로 보낼지는 정의가 정한다. 그 이름을 쓸 수 있는 사람인지는
        // 정의를 저장하거나 미리보기를 돌리는 시점에 이미 확인했다.
        DataSource dataSource = registry.resolve(def.getDataSource());

        long startedAt = System.currentTimeMillis();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(parsed.sql())) {

            ps.setQueryTimeout(queryTimeoutSeconds);
            ps.setFetchSize(1_000);
            if (def.getMaxRows() > 0) {
                // 드라이버가 서버 측에서 잘라 주므로 결과를 다 읽고 버리는 낭비가 없다
                ps.setMaxRows(def.getMaxRows());
            }
            bind(ps, parsed.parameterNames(), parameters);

            try (ResultSet rs = ps.executeQuery()) {
                DataTable table = read(rs, def.getMaxRows());
                log.debug("데이터셋 조회 완료: {} {}행, {}ms",
                        def.getDataSource(), table.size(), System.currentTimeMillis() - startedAt);
                return table;
            }
        } catch (SQLException e) {
            // 드라이버 메시지에는 테이블명과 컬럼명이 그대로 들어 있다.
            // 조회 조건 입력만으로 스키마를 훑을 수 있으므로 화면에는 내보내지 않고 로그에만 남긴다.
            log.error("데이터셋 조회 실패 (SQLState={})", e.getSQLState(), e);
            throw new DataSetException("데이터셋을 조회하지 못했습니다. 리포트 정의를 확인하세요.", e);
        }
    }

    private void bind(PreparedStatement ps, List<String> names, Map<String, Object> parameters)
            throws SQLException {
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            if (parameters == null || !parameters.containsKey(name)) {
                throw new DataSetException("SQL 파라미터 값이 없습니다: " + name);
            }
            setParameter(ps, i + 1, parameters.get(name));
        }
    }

    private void setParameter(PreparedStatement ps, int index, Object value) throws SQLException {
        switch (value) {
            case null -> ps.setNull(index, Types.VARCHAR);
            case String s -> ps.setString(index, s);
            case BigDecimal d -> ps.setBigDecimal(index, d);
            case Integer n -> ps.setInt(index, n);
            case Long n -> ps.setLong(index, n);
            case Double n -> ps.setDouble(index, n);
            case Boolean b -> ps.setBoolean(index, b);
            case LocalDate d -> ps.setObject(index, d);
            case LocalDateTime d -> ps.setObject(index, d);
            default -> ps.setObject(index, value);
        }
    }

    private DataTable read(ResultSet rs, int maxRows) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();

        List<String> columns = new ArrayList<>(columnCount);
        for (int i = 1; i <= columnCount; i++) {
            String label = meta.getColumnLabel(i);
            columns.add(label == null || label.isBlank() ? meta.getColumnName(i) : label);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        while (rs.next()) {
            if (maxRows > 0 && rows.size() >= maxRows) {
                log.warn("조회 행 수가 상한({})에 도달하여 이후 행을 잘랐습니다.", maxRows);
                break;
            }
            Map<String, Object> row = new LinkedHashMap<>(columnCount * 2);
            for (int i = 1; i <= columnCount; i++) {
                row.put(columns.get(i - 1), normalize(rs.getObject(i)));
            }
            rows.add(row);
        }
        return new DataTable(columns, rows);
    }

    /** 드라이버별 시간 타입을 java.time 으로 맞춰 둔다. 포맷 처리 분기를 줄이기 위함. */
    private Object normalize(Object value) {
        return switch (value) {
            case java.sql.Timestamp ts -> ts.toLocalDateTime();
            case java.sql.Date d -> d.toLocalDate();
            case java.sql.Time t -> t.toLocalTime();
            case null, default -> value;
        };
    }
}
