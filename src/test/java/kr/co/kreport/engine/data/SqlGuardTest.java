package kr.co.kreport.engine.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqlGuardTest {

    @ParameterizedTest
    @DisplayName("조회문은 통과한다")
    @ValueSource(strings = {
            "select * from budget_execution",
            "SELECT a.* FROM t a WHERE a.created_at > :from",
            "with x as (select 1) select * from x",
            "select 'update 는 문자열 안이라 괜찮다' from dual"
    })
    void allowsSelect(String sql) {
        assertThatCode(() -> SqlGuard.verifySelect(sql)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @DisplayName("변경 구문과 다중 구문은 막는다")
    @ValueSource(strings = {
            "delete from budget_execution",
            "update t set a = 1",
            "select 1; drop table t",
            "select * from t; delete from t",
            "call some_proc()",
            "select * from t where 1=1 /* */ ; truncate table t"
    })
    void blocksMutations(String sql) {
        assertThatThrownBy(() -> SqlGuard.verifySelect(sql))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("키워드를 품은 컬럼명은 오탐하지 않는다")
    void doesNotFalselyMatchColumnNames() {
        assertThatCode(() -> SqlGuard.verifySelect(
                "select created_at, updated_by, deleted_yn from t"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("이름 바인딩은 물음표로 바뀌고 순서가 보존된다")
    void parsesNamedParameters() {
        NamedParameterSql parsed = NamedParameterSql.parse(
                "select * from t where a = :from and b = :to and c = :from");

        assertThat(parsed.sql()).isEqualTo("select * from t where a = ? and b = ? and c = ?");
        assertThat(parsed.parameterNames()).containsExactly("from", "to", "from");
    }

    @Test
    @DisplayName("문자열, 주석, 캐스트 연산자 안쪽은 바인딩으로 보지 않는다")
    void ignoresNonBindingColons() {
        NamedParameterSql parsed = NamedParameterSql.parse("""
                select '12:30' as t,
                       x::text as c,       -- :notParam
                       /* :alsoNot */ y
                  from t
                 where z = :real
                """);

        assertThat(parsed.parameterNames()).containsExactly("real");
        assertThat(parsed.sql()).contains("'12:30'").contains("x::text").contains(":notParam");
    }
}
