package kr.co.kreport.engine.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import kr.co.kreport.access.Viewer;
import kr.co.kreport.config.KReportProperties;
import kr.co.kreport.support.ErrorCode;
import kr.co.kreport.support.KReportException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 이름으로 부르는 데이터소스 목록과, 누가 어느 DB 에 쿼리를 쓸 수 있는지.
 *
 * <h2>연결을 늘리는 일과 접근을 나누는 일은 한 묶음이다</h2>
 * <p>거래처 DB 를 붙이려고 데이터소스를 여러 개 두면, 거래처 편집자가 정의에
 * {@code dataSource: "main"} 이라고 적는 순간 우리 업무 DB 로 질의가 날아간다. 편집
 * 권한은 곧 임의 SELECT 를 쓸 권한이라, 연결만 늘리고 접근을 나누지 않으면 위험이
 * 늘기만 한다. 그래서 이 클래스가 두 가지를 함께 들고 있다.</p>
 *
 * <h2>기본값이 방향이 다른 이유</h2>
 * <p>{@code main} 은 작성자 목록이 비어 있으면 편집 권한자 모두에게 열린다 — 지금까지의
 * 동작이라, 이 기능을 켜는 것만으로 기존 편집이 멈추지 않게 하기 위해서다. 반면 새로
 * 등록한 외부 DB 는 목록이 비어 있으면 아무도 쓰지 못한다. 추가한 사실을 모르는 사이에
 * 열려 있는 쪽보다, 열려고 한 번 더 손대는 쪽이 낫다.</p>
 */
@Slf4j
@Component
public class DataSourceRegistry {

    /** 이름을 적지 않은 데이터셋이 향하는 곳 */
    public static final String MAIN = "main";

    private final Map<String, DataSource> sources = new LinkedHashMap<>();
    private final Map<String, KReportProperties.Datasource.Writers> writers = new LinkedHashMap<>();
    private final List<HikariDataSource> owned;

    public DataSourceRegistry(DataSource mainDataSource, KReportProperties properties) {
        sources.put(MAIN, mainDataSource);
        writers.put(MAIN, properties.getSecurity().getMainWriters());

        List<HikariDataSource> created = new java.util.ArrayList<>();
        properties.getDatasources().forEach((rawName, config) -> {
            String name = normalize(rawName);
            if (MAIN.equals(name)) {
                throw new IllegalStateException(
                        "데이터소스 이름 'main' 은 기본 연결이 쓰므로 다시 정의할 수 없습니다.");
            }
            HikariDataSource ds = build(name, config);
            created.add(ds);
            sources.put(name, ds);
            writers.put(name, config.getWriters());

            if (config.getWriters().isEmpty()) {
                log.warn("데이터소스 '{}' 에 작성자가 없어 아무도 쿼리를 쓸 수 없습니다. "
                        + "kreport.datasources.{}.writers 를 채우세요.", name, name);
            }
        });
        this.owned = List.copyOf(created);

        if (!owned.isEmpty() && properties.getSecurity().getMainWriters().isEmpty()) {
            // 외부 DB 를 붙였다는 것은 밖의 사람이 편집한다는 뜻일 때가 많다.
            // 그 상태에서 main 이 열려 있으면 이 기능을 붙인 의미가 없다.
            log.warn("외부 데이터소스를 {}개 등록했는데 main 의 작성자 제한이 없습니다. "
                            + "외부 편집자도 업무 DB 에 임의 조회를 쓸 수 있습니다. "
                            + "kreport.security.main-writers 를 설정하세요.", owned.size());
        }
        log.info("데이터소스 {}개 등록: {}", sources.size(), sources.keySet());
    }

    private HikariDataSource build(String name, KReportProperties.Datasource config) {
        if (config.getUrl() == null || config.getUrl().isBlank()) {
            throw new IllegalStateException("데이터소스 '" + name + "' 에 url 이 없습니다.");
        }
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("kreport-" + name);
        hikari.setJdbcUrl(config.getUrl());
        hikari.setUsername(config.getUsername());
        hikari.setPassword(config.getPassword());
        if (config.getDriverClassName() != null && !config.getDriverClassName().isBlank()) {
            hikari.setDriverClassName(config.getDriverClassName());
        }
        hikari.setReadOnly(config.isReadOnly());
        hikari.setMaximumPoolSize(config.getMaxPoolSize());
        hikari.setConnectionTimeout(config.getConnectionTimeoutMillis());
        return new HikariDataSource(hikari);
    }

    /** 이 이름의 연결. 없는 이름이면 예외. */
    public DataSource resolve(String name) {
        String key = normalize(name);
        DataSource ds = sources.get(key);
        if (ds == null) {
            throw new KReportException(ErrorCode.DATASOURCE_UNKNOWN,
                    "등록되지 않은 데이터소스입니다: " + key + " (사용 가능: " + names() + ")");
        }
        return ds;
    }

    public Set<String> names() {
        return new TreeSet<>(sources.keySet());
    }

    /**
     * 이 사람이 이 DB 를 향한 쿼리를 쓸 수 있는가.
     *
     * <p>관리자는 통과한다. 데이터소스를 등록하는 주체라, 등록해 놓고 자기가 못 쓰면
     * 설정이 맞는지 확인할 방법이 없다.</p>
     */
    public boolean canWrite(String name, Viewer viewer) {
        String key = normalize(name);
        if (viewer.hasRole(kr.co.kreport.config.ReportRole.ADMIN)) {
            return true;
        }
        KReportProperties.Datasource.Writers allowed = writers.get(key);
        if (allowed == null) {
            return false;
        }
        // main 만, 설정이 비어 있으면 종전대로 편집 권한자 모두에게 열어 둔다
        if (allowed.isEmpty()) {
            return MAIN.equals(key) && viewer.hasRole(kr.co.kreport.config.ReportRole.DESIGNER);
        }
        return contains(allowed.getUsers(), viewer.username())
                || anyMatch(allowed.getDepartments(), viewer.departments())
                || anyMatch(allowed.getRoles(), viewer.roles());
    }

    /** 쓸 수 없으면 예외. 저장과 미리보기 앞에서 부른다. */
    public void requireWrite(String name, Viewer viewer) {
        if (canWrite(name, viewer)) {
            return;
        }
        String key = normalize(name);
        log.warn("데이터소스 사용 거부: user={} datasource={} departments={}",
                viewer.username(), key, viewer.departments());
        throw new KReportException(ErrorCode.DATASOURCE_FORBIDDEN,
                "이 데이터소스에 조회를 작성할 권한이 없습니다: " + key);
    }

    /** 이 사람이 쓸 수 있는 데이터소스. 디자이너의 선택 목록에 쓴다. */
    public List<String> writableBy(Viewer viewer) {
        return names().stream().filter(n -> canWrite(n, viewer)).toList();
    }

    private static boolean contains(List<String> allowed, String value) {
        return allowed.stream().anyMatch(a -> a.equalsIgnoreCase(value));
    }

    private static boolean anyMatch(List<String> allowed, Set<String> values) {
        return allowed.stream().anyMatch(a -> values.stream().anyMatch(v -> v.equalsIgnoreCase(a)));
    }

    /** 이름은 대소문자를 가리지 않는다. 설정 파일과 정의에서 표기가 흔들려도 같은 곳을 가리키게. */
    private static String normalize(String name) {
        return name == null || name.isBlank()
                ? MAIN
                : name.trim().toLowerCase(Locale.ROOT);
    }

    /** 우리가 만든 풀만 닫는다. 스프링이 만든 기본 연결은 스프링이 닫는다. */
    @PreDestroy
    void close() {
        owned.forEach(HikariDataSource::close);
    }
}
