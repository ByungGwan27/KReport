package kr.co.kreport.engine.expression;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 구문 트리를 실제 값으로 평가한다.
 *
 * <p>상태를 갖지 않으므로 스레드 안전하다. 가변 상태는 모두 {@link EvalContext} 쪽에 있다.</p>
 */
public final class ExpressionEvaluator {

    /** 고정 문자열 안의 {@code ${...}} 보간 패턴 */
    private static final Pattern INTERPOLATION = Pattern.compile("\\$\\{([^}]*)}");

    private ExpressionEvaluator() {
    }

    public static Object eval(String expression, EvalContext context) {
        return eval(ExpressionParser.parse(expression), context);
    }

    /** 평가 실패를 예외 대신 null 로 흘려보낸다. 한 셀의 식 오류가 리포트 전체를 막지 않도록. */
    public static Object evalQuietly(String expression, EvalContext context) {
        try {
            return eval(expression, context);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static Object evalQuietly(ExprNode node, EvalContext context) {
        try {
            return eval(node, context);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static boolean evalCondition(String expression, EvalContext context, boolean defaultValue) {
        if (expression == null || expression.isBlank()) {
            return defaultValue;
        }
        return Values.toBool(evalQuietly(expression, context));
    }

    /**
     * 고정 문자열 안의 {@code ${식}} 을 평가해 끼워 넣는다.
     * 라벨에 조회 기준일 등을 섞어 넣을 때 쓴다.
     */
    public static String interpolate(String text, EvalContext context) {
        if (text == null || text.isEmpty() || text.indexOf("${") < 0) {
            return text;
        }
        Matcher m = INTERPOLATION.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            Object value = evalQuietly(m.group(1), context);
            m.appendReplacement(sb, Matcher.quoteReplacement(Values.toStr(value)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static Object eval(ExprNode node, EvalContext context) {
        return switch (node) {
            case ExprNode.Literal n -> n.value();
            case ExprNode.FieldRef n -> context.field(n.name());
            case ExprNode.ParamRef n -> context.parameter(n.name());
            case ExprNode.VarRef n -> context.variable(n.name());
            case ExprNode.Unary n -> evalUnary(n, context);
            case ExprNode.Binary n -> evalBinary(n, context);
            case ExprNode.Ternary n -> Values.toBool(eval(n.condition(), context))
                    ? eval(n.whenTrue(), context)
                    : eval(n.whenFalse(), context);
            case ExprNode.FunctionCall n -> evalFunction(n, context);
            case ExprNode.Aggregate n -> context.aggregate(n.key(), n.scope());
        };
    }

    private static Object evalUnary(ExprNode.Unary node, EvalContext context) {
        Object v = eval(node.operand(), context);
        return switch (node.op()) {
            case "!" -> !Values.toBool(v);
            case "-" -> Values.toDecimalOrZero(v).negate();
            default -> throw new ExpressionException("지원하지 않는 단항 연산자", node.op());
        };
    }

    private static Object evalBinary(ExprNode.Binary node, EvalContext context) {
        String op = node.op();

        // 논리 연산은 단축 평가한다
        if ("&&".equals(op)) {
            return Values.toBool(eval(node.left(), context)) && Values.toBool(eval(node.right(), context));
        }
        if ("||".equals(op)) {
            return Values.toBool(eval(node.left(), context)) || Values.toBool(eval(node.right(), context));
        }

        Object l = eval(node.left(), context);
        Object r = eval(node.right(), context);

        return switch (op) {
            case "==" -> Values.equal(l, r);
            case "!=" -> !Values.equal(l, r);
            case ">" -> Values.compare(l, r) > 0;
            case ">=" -> Values.compare(l, r) >= 0;
            case "<" -> Values.compare(l, r) < 0;
            case "<=" -> Values.compare(l, r) <= 0;
            case "+" -> add(l, r);
            case "-" -> Values.toDecimalOrZero(l).subtract(Values.toDecimalOrZero(r));
            case "*" -> Values.toDecimalOrZero(l).multiply(Values.toDecimalOrZero(r));
            case "/" -> Values.divide(Values.toDecimalOrZero(l), Values.toDecimal(r));
            case "%" -> {
                BigDecimal divisor = Values.toDecimal(r);
                yield (divisor == null || divisor.signum() == 0)
                        ? null
                        : Values.toDecimalOrZero(l).remainder(divisor);
            }
            default -> throw new ExpressionException("지원하지 않는 연산자", op);
        };
    }

    /**
     * 덧셈. 양쪽이 모두 수치로 읽히면 수치 덧셈, 아니면 문자열 이어붙이기.
     * 리포트 식에서는 라벨과 값을 이어 붙이는 쪽이 더 잦다.
     */
    private static Object add(Object l, Object r) {
        BigDecimal dl = Values.toDecimal(l);
        BigDecimal dr = Values.toDecimal(r);
        if (dl != null && dr != null) {
            return dl.add(dr);
        }
        return Values.toStr(l) + Values.toStr(r);
    }

    private static Object evalFunction(ExprNode.FunctionCall node, EvalContext context) {
        List<Object> args = new ArrayList<>(node.args().size());
        for (ExprNode arg : node.args()) {
            args.add(eval(arg, context));
        }
        return Functions.call(node.name(), args);
    }

    /**
     * 구문 트리에서 집계 노드를 모두 찾아낸다. 레이아웃 전 집계 사전 계산에 쓴다.
     */
    public static void collectAggregates(ExprNode node, List<ExprNode.Aggregate> out) {
        switch (node) {
            case ExprNode.Aggregate n -> {
                out.add(n);
                collectAggregates(n.inner(), out);
            }
            case ExprNode.Unary n -> collectAggregates(n.operand(), out);
            case ExprNode.Binary n -> {
                collectAggregates(n.left(), out);
                collectAggregates(n.right(), out);
            }
            case ExprNode.Ternary n -> {
                collectAggregates(n.condition(), out);
                collectAggregates(n.whenTrue(), out);
                collectAggregates(n.whenFalse(), out);
            }
            case ExprNode.FunctionCall n -> n.args().forEach(a -> collectAggregates(a, out));
            case ExprNode.Literal ignored -> {
            }
            case ExprNode.FieldRef ignored -> {
            }
            case ExprNode.ParamRef ignored -> {
            }
            case ExprNode.VarRef ignored -> {
            }
        }
    }

    /** 문자열 표현식에서 집계 노드를 수집한다. 보간 문자열도 함께 훑는다. */
    public static void collectAggregatesFromText(String text, List<ExprNode.Aggregate> out) {
        if (text == null || text.isBlank()) {
            return;
        }
        Matcher m = INTERPOLATION.matcher(text);
        while (m.find()) {
            try {
                collectAggregates(ExpressionParser.parse(m.group(1)), out);
            } catch (RuntimeException ignored) {
                // 보간식이 깨져 있으면 출력 시점에 빈 값으로 처리된다
            }
        }
    }
}
