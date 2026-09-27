package kr.co.kreport.engine.expression;

import java.util.List;

/**
 * 표현식 구문 트리.
 *
 * <p>노드 종류를 sealed 로 닫아 두었기 때문에 평가기에서 switch 패턴 매칭이
 * 누락 없이 컴파일 타임에 검증된다.</p>
 */
public sealed interface ExprNode {

    /** 숫자/문자열/불리언/null 리터럴 */
    record Literal(Object value) implements ExprNode {
    }

    /** {@code {컬럼명}} — 현재 데이터 행의 값 */
    record FieldRef(String name) implements ExprNode {
    }

    /** {@code @파라미터명} — 실행 파라미터 */
    record ParamRef(String name) implements ExprNode {
    }

    /** {@code PAGE_NO} 같은 식별자 — 내장 변수 */
    record VarRef(String name) implements ExprNode {
    }

    /** {@code !x}, {@code -x} */
    record Unary(String op, ExprNode operand) implements ExprNode {
    }

    /** 이항 연산 */
    record Binary(String op, ExprNode left, ExprNode right) implements ExprNode {
    }

    /** {@code cond ? a : b} */
    record Ternary(ExprNode condition, ExprNode whenTrue, ExprNode whenFalse) implements ExprNode {
    }

    /** 일반 함수 호출 */
    record FunctionCall(String name, List<ExprNode> args) implements ExprNode {
    }

    /**
     * 집계 함수 호출. 일반 함수와 달리 행 단위로 평가되지 않고
     * 레이아웃 전에 계산된 집계표에서 값을 꺼낸다.
     *
     * @param scope 집계 범위. null 이면 리포트 전체, 아니면 그룹명
     */
    record Aggregate(String func, ExprNode inner, String scope, String key) implements ExprNode {
    }
}
