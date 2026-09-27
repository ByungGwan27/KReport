package kr.co.kreport.engine.expression;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 표현식 문자열을 토큰 열로 분해한다.
 */
final class Lexer {

    enum Type {
        NUMBER, STRING, IDENT, FIELD, PARAM, OP, LPAREN, RPAREN, COMMA, EOF
    }

    record Token(Type type, String text, Object value, int pos) {
        @Override
        public String toString() {
            return type + "(" + text + ")";
        }
    }

    private static final String[] OPERATORS = {
            "||", "&&", "==", "!=", ">=", "<=",
            "+", "-", "*", "/", "%", ">", "<", "!", "?", ":"
    };

    private final String src;
    private int pos;

    Lexer(String src) {
        this.src = src;
    }

    List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        while (true) {
            Token t = next();
            tokens.add(t);
            if (t.type() == Type.EOF) {
                return tokens;
            }
        }
    }

    private Token next() {
        skipWhitespace();
        if (pos >= src.length()) {
            return new Token(Type.EOF, "", null, pos);
        }
        int start = pos;
        char c = src.charAt(pos);

        if (c == '{') {
            return readField(start);
        }
        if (c == '@') {
            return readParam(start);
        }
        if (c == '\'' || c == '"') {
            return readString(start, c);
        }
        if (Character.isDigit(c) || (c == '.' && pos + 1 < src.length() && Character.isDigit(src.charAt(pos + 1)))) {
            return readNumber(start);
        }
        if (isIdentStart(c)) {
            return readIdent(start);
        }
        if (c == '(') {
            pos++;
            return new Token(Type.LPAREN, "(", null, start);
        }
        if (c == ')') {
            pos++;
            return new Token(Type.RPAREN, ")", null, start);
        }
        if (c == ',') {
            pos++;
            return new Token(Type.COMMA, ",", null, start);
        }
        for (String op : OPERATORS) {
            if (src.startsWith(op, pos)) {
                pos += op.length();
                return new Token(Type.OP, op, null, start);
            }
        }
        throw new ExpressionException("해석할 수 없는 문자 '" + c + "' (" + start + "번째)", src);
    }

    private void skipWhitespace() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
            pos++;
        }
    }

    /** {@code {컬럼명}} — 중괄호 안은 공백과 한글을 포함한 임의 이름을 허용한다 */
    private Token readField(int start) {
        int close = src.indexOf('}', pos + 1);
        if (close < 0) {
            throw new ExpressionException("필드 참조의 '}' 가 없습니다 (" + start + "번째)", src);
        }
        String name = src.substring(pos + 1, close).trim();
        if (name.isEmpty()) {
            throw new ExpressionException("빈 필드 참조 {} 입니다 (" + start + "번째)", src);
        }
        pos = close + 1;
        return new Token(Type.FIELD, name, null, start);
    }

    private Token readParam(int start) {
        pos++;
        int s = pos;
        while (pos < src.length() && isIdentPart(src.charAt(pos))) {
            pos++;
        }
        if (s == pos) {
            throw new ExpressionException("'@' 뒤에 파라미터명이 없습니다 (" + start + "번째)", src);
        }
        return new Token(Type.PARAM, src.substring(s, pos), null, start);
    }

    /** 따옴표 문자열. 같은 따옴표를 두 번 쓰면 이스케이프된다 ('It''s') */
    private Token readString(int start, char quote) {
        pos++;
        StringBuilder sb = new StringBuilder();
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == quote) {
                if (pos + 1 < src.length() && src.charAt(pos + 1) == quote) {
                    sb.append(quote);
                    pos += 2;
                    continue;
                }
                pos++;
                return new Token(Type.STRING, sb.toString(), sb.toString(), start);
            }
            sb.append(c);
            pos++;
        }
        throw new ExpressionException("문자열이 닫히지 않았습니다 (" + start + "번째)", src);
    }

    private Token readNumber(int start) {
        while (pos < src.length() && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.')) {
            pos++;
        }
        String text = src.substring(start, pos);
        try {
            return new Token(Type.NUMBER, text, new BigDecimal(text), start);
        } catch (NumberFormatException e) {
            throw new ExpressionException("잘못된 숫자 '" + text + "'", src, e);
        }
    }

    private Token readIdent(int start) {
        while (pos < src.length() && isIdentPart(src.charAt(pos))) {
            pos++;
        }
        return new Token(Type.IDENT, src.substring(start, pos), null, start);
    }

    private static boolean isIdentStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isIdentPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '.';
    }
}
