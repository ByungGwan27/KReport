package kr.co.kreport.engine.expression;

/** 표현식 파싱/평가 실패 */
public class ExpressionException extends RuntimeException {

    private final String expression;

    public ExpressionException(String message, String expression) {
        super(message + " (식: " + expression + ")");
        this.expression = expression;
    }

    public ExpressionException(String message, String expression, Throwable cause) {
        super(message + " (식: " + expression + ")", cause);
        this.expression = expression;
    }

    public String getExpression() {
        return expression;
    }
}
