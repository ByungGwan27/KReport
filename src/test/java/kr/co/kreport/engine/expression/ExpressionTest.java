package kr.co.kreport.engine.expression;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExpressionTest {

    private EvalContext context() {
        EvalContext context = new EvalContext();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("DEPT_NAME", "복지정책과");
        row.put("BUDGET_AMT", new BigDecimal("100000"));
        row.put("EXEC_AMT", new BigDecimal("75000"));
        row.put("EXEC_DATE", LocalDate.of(2026, 3, 14));
        row.put("COMPLETED_DATE", null);
        context.setRow(row, 0);
        context.setColumnAlias(Map.of("DEPT_NAME", "DEPT_NAME", "BUDGET_AMT", "BUDGET_AMT",
                "EXEC_AMT", "EXEC_AMT", "EXEC_DATE", "EXEC_DATE", "COMPLETED_DATE", "COMPLETED_DATE"));
        context.putParameter("fiscalYear", new BigDecimal("2026"));
        context.putVariable("PAGE_NO", 3);
        return context;
    }

    private Object eval(String expression) {
        return ExpressionEvaluator.eval(expression, context());
    }

    @Test
    @DisplayName("필드, 파라미터, 내장 변수를 각각의 기호로 참조한다")
    void references() {
        assertThat(eval("{DEPT_NAME}")).isEqualTo("복지정책과");
        assertThat(eval("@fiscalYear")).isEqualTo(new BigDecimal("2026"));
        assertThat(eval("PAGE_NO")).isEqualTo(3);
    }

    @Test
    @DisplayName("컬럼명 대소문자가 달라도 찾는다")
    void columnNameIsCaseInsensitive() {
        assertThat(eval("{dept_name}")).isEqualTo("복지정책과");
    }

    @ParameterizedTest(name = "{0} => {1}")
    @DisplayName("산술과 비교 연산")
    @CsvSource({
            "'1 + 2',            3",
            "'10 - 4 * 2',       2",
            "'(10 - 4) * 2',     12",
            "'7 % 3',            1",
            "'-3 + 5',           2",
    })
    void arithmetic(String expression, String expected) {
        assertThat(eval(expression)).isEqualTo(new BigDecimal(expected));
    }

    @Test
    @DisplayName("나눗셈은 BigDecimal 로 계산해 부동소수 오차가 없다")
    void divisionKeepsPrecision() {
        Object rate = eval("{EXEC_AMT} / {BUDGET_AMT}");
        assertThat(rate).isEqualTo(new BigDecimal("0.75"));
    }

    @Test
    @DisplayName("0 으로 나누면 예외 대신 null 을 돌려준다")
    void divisionByZero() {
        assertThat(eval("{EXEC_AMT} / 0")).isNull();
    }

    @Test
    @DisplayName("양쪽이 숫자면 덧셈, 아니면 문자열 연결")
    void plusOperator() {
        assertThat(eval("'합계: ' + 100")).isEqualTo("합계: 100");
        assertThat(eval("100 + 200")).isEqualTo(new BigDecimal("300"));
    }

    @Test
    @DisplayName("삼항 연산자와 논리 연산")
    void conditional() {
        assertThat(eval("{EXEC_AMT} > 50000 ? '초과' : '미달'")).isEqualTo("초과");
        assertThat(eval("{EXEC_AMT} > 0 && {BUDGET_AMT} > 0")).isEqualTo(true);
        assertThat(eval("!({EXEC_AMT} > 0)")).isEqualTo(false);
    }

    @Test
    @DisplayName("null 비교")
    void nullComparison() {
        assertThat(eval("{COMPLETED_DATE} == null")).isEqualTo(true);
        assertThat(eval("{COMPLETED_DATE} != null")).isEqualTo(false);
    }

    @Test
    @DisplayName("문자열 함수")
    void stringFunctions() {
        assertThat(eval("SUBSTR('공공기관 리포팅', 1, 4)")).isEqualTo("공공기관");
        assertThat(eval("LPAD('7', 3, '0')")).isEqualTo("007");
        assertThat(eval("NVL(null, '-')")).isEqualTo("-");
        assertThat(eval("DECODE('B', 'A', '가', 'B', '나', '기타')")).isEqualTo("나");
    }

    @Test
    @DisplayName("MASK 는 개인정보 컬럼의 가운데를 가린다")
    void maskFunction() {
        assertThat(eval("MASK('홍길동', 1, 1)")).isEqualTo("홍*동");
        assertThat(eval("MASK('01012345678', 3, 4)")).isEqualTo("010****5678");
    }

    @Test
    @DisplayName("날짜 함수")
    void dateFunctions() {
        assertThat(eval("FORMAT({EXEC_DATE}, 'yyyy-MM-dd')")).isEqualTo("2026-03-14");
        assertThat(eval("YEAR({EXEC_DATE})")).isEqualTo(BigDecimal.valueOf(2026));
        assertThat(eval("DATEDIFF({EXEC_DATE}, TODATE('2026-03-20'))")).isEqualTo(BigDecimal.valueOf(6));
    }

    @Test
    @DisplayName("숫자 포맷은 반올림 규칙을 따른다")
    void numberFormat() {
        assertThat(ValueFormatter.format(new BigDecimal("1234567"), "#,##0")).isEqualTo("1,234,567");
        assertThat(ValueFormatter.format(new BigDecimal("0.7654"), "0%")).isEqualTo("77%");
        assertThat(ValueFormatter.format(new BigDecimal("12.345"), "0.00")).isEqualTo("12.35");
    }

    @ParameterizedTest(name = "{0} => {1}")
    @DisplayName("한글 금액 표기")
    @CsvSource({
            "0,          영",
            "1,          일",
            "15,         일십오",
            "1000,       일천",
            "10001,      일만일",
            "12034000,   일천이백삼만사천",
            "100000000,  일억",
            "3202329000, 삼십이억이백삼십이만구천",
    })
    void koreanNumber(String amount, String expected) {
        assertThat(ValueFormatter.toKoreanNumber(new BigDecimal(amount))).isEqualTo(expected);
    }

    @Test
    @DisplayName("고정 문자열 안의 보간식을 평가한다")
    void interpolation() {
        assertThat(ExpressionEvaluator.interpolate("${@fiscalYear}년도 집행 현황", context()))
                .isEqualTo("2026년도 집행 현황");
    }

    @Test
    @DisplayName("문법 오류는 위치를 알려 주는 예외가 된다")
    void syntaxError() {
        assertThatThrownBy(() -> ExpressionParser.parse("1 + "))
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("식: 1 + ");

        assertThatThrownBy(() -> ExpressionParser.parse("{미완성"))
                .isInstanceOf(ExpressionException.class);
    }

    @Test
    @DisplayName("등록되지 않은 함수는 호출할 수 없다")
    void unknownFunctionIsRejected() {
        assertThatThrownBy(() -> eval("GETRUNTIME()"))
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("알 수 없는 함수");
    }

    @Test
    @DisplayName("집계 함수는 범위별로 서로 다른 키를 만든다")
    void aggregateKeys() {
        ExprNode total = ExpressionParser.parse("SUM({EXEC_AMT})");
        ExprNode perGroup = ExpressionParser.parse("SUM({EXEC_AMT},'dept')");

        assertThat(total).isInstanceOf(ExprNode.Aggregate.class);
        assertThat(((ExprNode.Aggregate) total).key()).isEqualTo("SUM({EXEC_AMT})@#REPORT");
        assertThat(((ExprNode.Aggregate) perGroup).key()).isEqualTo("SUM({EXEC_AMT})@dept");
    }
}
