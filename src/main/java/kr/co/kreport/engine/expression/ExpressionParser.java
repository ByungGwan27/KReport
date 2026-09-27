package kr.co.kreport.engine.expression;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 재귀 하강 파서. 우선순위는 낮은 쪽부터
 * 삼항 -> OR -> AND -> 등가 -> 대소 -> 가감 -> 승제 -> 단항 -> 기본 순이다.
 *
 * <p>파싱 결과는 표현식 문자열을 키로 캐시한다. 리포트 한 건에서 같은 식이
 * 행 수만큼 반복 평가되므로 파싱 비용을 매번 치를 이유가 없다.</p>
 */
public final class ExpressionParser {

    /** 집계로 취급할 함수명 */
    public static final Set<String> AGGREGATE_FUNCTIONS =
            Set.of("SUM", "AVG", "MIN", "MAX", "COUNT", "COUNT_DISTINCT");

    private static final Map<String, ExprNode> CACHE = new ConcurrentHashMap<>();
    private static final int CACHE_LIMIT = 4_000;

    private final String src;
    private final List<Lexer.Token> tokens;
    private int index;

    private ExpressionParser(String src) {
        this.src = src;
        this.tokens = new Lexer(src).tokenize();
    }

    public static ExprNode parse(String expression) {
        if (expression == null || expression.isBlank()) {
            return new ExprNode.Literal(null);
        }
        ExprNode cached = CACHE.get(expression);
        if (cached != null) {
            return cached;
        }
        ExpressionParser parser = new ExpressionParser(expression);
        ExprNode node = parser.parseTernary();
        parser.expect(Lexer.Type.EOF);
        if (CACHE.size() < CACHE_LIMIT) {
            CACHE.put(expression, node);
        }
        return node;
    }

    static void clearCache() {
        CACHE.clear();
    }

    // ---------------------------------------------------------------- 우선순위 단계

    private ExprNode parseTernary() {
        ExprNode condition = parseOr();
        if (matchOp("?")) {
            ExprNode whenTrue = parseTernary();
            expectOp(":");
            ExprNode whenFalse = parseTernary();
            return new ExprNode.Ternary(condition, whenTrue, whenFalse);
        }
        return condition;
    }

    private ExprNode parseOr() {
        ExprNode left = parseAnd();
        while (matchOp("||")) {
            left = new ExprNode.Binary("||", left, parseAnd());
        }
        return left;
    }

    private ExprNode parseAnd() {
        ExprNode left = parseEquality();
        while (matchOp("&&")) {
            left = new ExprNode.Binary("&&", left, parseEquality());
        }
        return left;
    }

    private ExprNode parseEquality() {
        ExprNode left = parseRelational();
        while (true) {
            if (matchOp("==")) {
                left = new ExprNode.Binary("==", left, parseRelational());
            } else if (matchOp("!=")) {
                left = new ExprNode.Binary("!=", left, parseRelational());
            } else {
                return left;
            }
        }
    }

    private ExprNode parseRelational() {
        ExprNode left = parseAdditive();
        while (true) {
            if (matchOp(">=")) {
                left = new ExprNode.Binary(">=", left, parseAdditive());
            } else if (matchOp("<=")) {
                left = new ExprNode.Binary("<=", left, parseAdditive());
            } else if (matchOp(">")) {
                left = new ExprNode.Binary(">", left, parseAdditive());
            } else if (matchOp("<")) {
                left = new ExprNode.Binary("<", left, parseAdditive());
            } else {
                return left;
            }
        }
    }

    private ExprNode parseAdditive() {
        ExprNode left = parseMultiplicative();
        while (true) {
            if (matchOp("+")) {
                left = new ExprNode.Binary("+", left, parseMultiplicative());
            } else if (matchOp("-")) {
                left = new ExprNode.Binary("-", left, parseMultiplicative());
            } else {
                return left;
            }
        }
    }

    private ExprNode parseMultiplicative() {
        ExprNode left = parseUnary();
        while (true) {
            if (matchOp("*")) {
                left = new ExprNode.Binary("*", left, parseUnary());
            } else if (matchOp("/")) {
                left = new ExprNode.Binary("/", left, parseUnary());
            } else if (matchOp("%")) {
                left = new ExprNode.Binary("%", left, parseUnary());
            } else {
                return left;
            }
        }
    }

    private ExprNode parseUnary() {
        if (matchOp("!")) {
            return new ExprNode.Unary("!", parseUnary());
        }
        if (matchOp("-")) {
            return new ExprNode.Unary("-", parseUnary());
        }
        if (matchOp("+")) {
            return parseUnary();
        }
        return parsePrimary();
    }

    private ExprNode parsePrimary() {
        Lexer.Token t = peek();
        switch (t.type()) {
            case NUMBER, STRING -> {
                advance();
                return new ExprNode.Literal(t.value());
            }
            case FIELD -> {
                advance();
                return new ExprNode.FieldRef(t.text());
            }
            case PARAM -> {
                advance();
                return new ExprNode.ParamRef(t.text());
            }
            case LPAREN -> {
                advance();
                ExprNode inner = parseTernary();
                expect(Lexer.Type.RPAREN);
                return inner;
            }
            case IDENT -> {
                return parseIdent();
            }
            default -> throw new ExpressionException(
                    "예상치 못한 토큰 " + t + " (" + t.pos() + "번째)", src);
        }
    }

    private ExprNode parseIdent() {
        Lexer.Token t = advance();
        String name = t.text();

        if (peek().type() != Lexer.Type.LPAREN) {
            // 함수 호출이 아니면 리터럴 키워드 또는 내장 변수
            return switch (name.toLowerCase()) {
                case "true" -> new ExprNode.Literal(Boolean.TRUE);
                case "false" -> new ExprNode.Literal(Boolean.FALSE);
                case "null" -> new ExprNode.Literal(null);
                default -> new ExprNode.VarRef(name);
            };
        }

        String upper = name.toUpperCase();
        if (AGGREGATE_FUNCTIONS.contains(upper)) {
            return parseAggregate(upper);
        }

        advance(); // 여는 괄호
        List<ExprNode> args = parseArguments();
        return new ExprNode.FunctionCall(upper, args);
    }

    /**
     * 집계 함수. 두 번째 인자로 그룹명을 주면 해당 그룹 범위로 집계한다.
     * 생략하면 리포트 전체가 범위다.
     *
     * <pre>
     * SUM({amount})              리포트 전체 합계
     * SUM({amount}, 'dept')      dept 그룹 단위 합계
     * COUNT(1)                   건수
     * </pre>
     */
    private ExprNode parseAggregate(String func) {
        advance(); // 여는 괄호
        int innerStart = peek().pos();
        ExprNode inner = parseTernary();
        int innerEnd = peek().pos();
        String innerSource = src.substring(innerStart, Math.min(innerEnd, src.length())).trim();

        String scope = null;
        if (match(Lexer.Type.COMMA)) {
            Lexer.Token scopeToken = peek();
            if (scopeToken.type() != Lexer.Type.STRING) {
                throw new ExpressionException(
                        func + " 의 두 번째 인자는 그룹명 문자열이어야 합니다", src);
            }
            advance();
            scope = scopeToken.text();
        }
        expect(Lexer.Type.RPAREN);

        String key = func + "(" + innerSource + ")@" + (scope == null ? "#REPORT" : scope);
        return new ExprNode.Aggregate(func, inner, scope, key);
    }

    private List<ExprNode> parseArguments() {
        List<ExprNode> args = new ArrayList<>();
        if (match(Lexer.Type.RPAREN)) {
            return args;
        }
        do {
            args.add(parseTernary());
        } while (match(Lexer.Type.COMMA));
        expect(Lexer.Type.RPAREN);
        return args;
    }

    // ---------------------------------------------------------------- 토큰 조작

    private Lexer.Token peek() {
        return tokens.get(index);
    }

    private Lexer.Token advance() {
        return tokens.get(index++);
    }

    private boolean match(Lexer.Type type) {
        if (peek().type() == type) {
            index++;
            return true;
        }
        return false;
    }

    private boolean matchOp(String op) {
        Lexer.Token t = peek();
        if (t.type() == Lexer.Type.OP && t.text().equals(op)) {
            index++;
            return true;
        }
        return false;
    }

    private void expect(Lexer.Type type) {
        if (!match(type)) {
            throw new ExpressionException(
                    type + " 가 와야 하는데 " + peek() + " 가 있습니다 (" + peek().pos() + "번째)", src);
        }
    }

    private void expectOp(String op) {
        if (!matchOp(op)) {
            throw new ExpressionException(
                    "연산자 " + op + " 가 와야 하는데 " + peek() + " 가 있습니다 (" + peek().pos() + "번째)", src);
        }
    }
}
