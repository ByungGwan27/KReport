package kr.co.kreport.engine.expression;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 표현식 어휘를 엔진 밖에서 늘릴 수 있는지, 그리고 늘릴 수 없어야 할 때 막히는지 */
class FunctionRegistryTest {

    /**
     * 앞뒤로 모두 되돌린다.
     *
     * <p>뒤에서만 정리하면 모자란다. 같은 JVM 에서 먼저 돈 스프링 시험이 기동을 마치며
     * 목록을 잠가 두기 때문에, 이 시험이 시작할 때 이미 얼어 있다. 레지스트리를 정적으로
     * 둔 대가가 시험 격리에서 이렇게 드러난다.</p>
     */
    @BeforeEach
    void setUp() {
        FunctionRegistry.resetForTest();
    }

    @AfterEach
    void tearDown() {
        FunctionRegistry.resetForTest();
    }

    /** 기관 전용 함수를 엔진 코드를 고치지 않고 추가하는 경로 */
    private static FunctionLibrary library(String name, ReportFunction... functions) {
        return new FunctionLibrary() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public List<ReportFunction> functions() {
                return List.of(functions);
            }
        };
    }

    @Test
    @DisplayName("등록한 함수를 표현식에서 바로 부를 수 있다")
    void registeredFunctionIsCallable() {
        FunctionRegistry.register(library("회계",
                ReportFunction.of("FISCALYEAR", 1, "FISCALYEAR(날짜)", "3월 시작 회계연도",
                        args -> {
                            var date = Values.toLocalDate(args.get(0));
                            return BigDecimal.valueOf(
                                    date.getMonthValue() >= 3 ? date.getYear() : date.getYear() - 1);
                        })));

        EvalContext context = new EvalContext();
        assertThat(ExpressionEvaluator.eval("FISCALYEAR('2026-02-15')", context))
                .isEqualTo(BigDecimal.valueOf(2025));
        assertThat(ExpressionEvaluator.eval("FISCALYEAR('2026-03-01')", context))
                .isEqualTo(BigDecimal.valueOf(2026));
    }

    @Test
    @DisplayName("내장 함수와 이름이 겹치면 등록을 거부한다 - 같은 식이 서버마다 다른 값을 내면 안 된다")
    void duplicateNameIsRejected() {
        assertThatThrownBy(() -> FunctionRegistry.register(library("장난",
                ReportFunction.of("ROUND", 1, "ROUND(x)", "가짜", args -> BigDecimal.ZERO))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ROUND")
                .hasMessageContaining("내장");

        assertThat(FunctionRegistry.call("ROUND", List.of(new BigDecimal("1.6"))))
                .isEqualTo(new BigDecimal("2"));
    }

    @Test
    @DisplayName("기동이 끝난 뒤에는 어휘가 바뀌지 않는다")
    void frozenRegistryRejectsRegistration() {
        FunctionRegistry.freeze();
        assertThat(FunctionRegistry.isFrozen()).isTrue();

        assertThatThrownBy(() -> FunctionRegistry.register(library("늦둥이",
                ReportFunction.of("LATE", 0, "LATE()", "뒤늦게", args -> "x"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("늦둥이");

        assertThat(FunctionRegistry.exists("LATE")).isFalse();
    }

    @Test
    @DisplayName("인자 수가 맞지 않으면 필요한 개수와 호출 형태를 알려 준다")
    void arityIsCheckedInOnePlace() {
        assertThatThrownBy(() -> FunctionRegistry.call("SUBSTR", List.of("서울특별시")))
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("2~3개")
                .hasMessageContaining("SUBSTR(문자열, 시작위치, 길이)");

        assertThatThrownBy(() -> FunctionRegistry.call("TODAY", List.of("군더더기")))
                .isInstanceOf(ExpressionException.class)
                .hasMessageContaining("0개");
    }

    @Test
    @DisplayName("함수 목록에 호출 형태와 설명이 함께 실린다")
    void descriptorsCarryHelp() {
        ReportFunction mask = FunctionRegistry.descriptors().stream()
                .filter(f -> f.name().equals("MASK"))
                .findFirst()
                .orElseThrow();

        assertThat(mask.signature()).isEqualTo("MASK(값, 앞자리, 뒷자리)");
        assertThat(mask.description()).isNotBlank();
        assertThat(mask.minArgs()).isEqualTo(1);
        assertThat(mask.maxArgs()).isEqualTo(3);
    }

    @Test
    @DisplayName("어느 묶음에서 온 함수인지 남는다")
    void originIsTracked() {
        FunctionRegistry.register(library("민원",
                ReportFunction.of("CIVILCODE", 1, "CIVILCODE(코드)", "민원 코드명", args -> "접수")));

        assertThat(FunctionRegistry.origins())
                .containsEntry("CIVILCODE", "민원")
                .containsEntry("ROUND", "내장");
    }

    @Test
    @DisplayName("소문자로 불러도 같은 함수를 찾는다")
    void lookupIsCaseInsensitive() {
        assertThat(FunctionRegistry.exists("upper")).isTrue();
        assertThat(FunctionRegistry.call("upper", List.of("seoul"))).isEqualTo("SEOUL");
    }
}
