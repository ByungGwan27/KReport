package kr.co.kreport.engine.layout;

import kr.co.kreport.engine.data.DataTable;
import kr.co.kreport.engine.expression.EvalContext;
import kr.co.kreport.engine.expression.ExprNode;
import kr.co.kreport.engine.expression.ExpressionEvaluator;
import kr.co.kreport.engine.expression.Values;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 집계값을 데이터 1회 순회로 미리 계산해 두고 조회만 하게 한다.
 *
 * <p>총계를 리포트 헤더에 찍는 식({@code SUM({금액})})은 첫 페이지를 그릴 때
 * 이미 전체 합을 알아야 하므로 레이아웃 도중 누적 방식으로는 풀리지 않는다.
 * 그래서 레이아웃에 들어가기 전에 전 구간을 한 번 훑어 확정한다.</p>
 */
public final class AggregateStore implements EvalContext.AggregateLookup {

    /** key -> (그룹 인스턴스 번호 -> 값). 전체 범위는 인스턴스 번호 0 하나만 쓴다. */
    private final Map<String, Map<Integer, Object>> values = new HashMap<>();
    private final GroupIndex groupIndex;

    private AggregateStore(GroupIndex groupIndex) {
        this.groupIndex = groupIndex;
    }

    public static AggregateStore empty() {
        return new AggregateStore(GroupIndex.empty(0));
    }

    /**
     * @param aggregates 템플릿 전체에서 수집한 집계 노드 (중복 key 허용)
     */
    public static AggregateStore compute(DataTable data,
                                         GroupIndex groupIndex,
                                         Collection<ExprNode.Aggregate> aggregates,
                                         EvalContext context) {
        AggregateStore store = new AggregateStore(groupIndex);
        if (aggregates.isEmpty()) {
            return store;
        }

        // 같은 key 가 여러 셀에서 쓰이므로 하나만 남긴다
        Map<String, ExprNode.Aggregate> distinct = new HashMap<>();
        for (ExprNode.Aggregate a : aggregates) {
            distinct.putIfAbsent(a.key(), a);
        }

        Map<String, Map<Integer, Accumulator>> accumulators = new HashMap<>();
        for (String key : distinct.keySet()) {
            accumulators.put(key, new HashMap<>());
        }

        for (int row = 0; row < data.size(); row++) {
            context.setRow(data.row(row), row);
            for (Map.Entry<String, ExprNode.Aggregate> entry : distinct.entrySet()) {
                ExprNode.Aggregate node = entry.getValue();
                int bucket = bucketOf(node.scope(), groupIndex, row);
                if (bucket < 0) {
                    // 정의되지 않은 그룹명을 참조한 식. 값이 비게 되므로 조용히 건너뛴다.
                    continue;
                }
                Object value = ExpressionEvaluator.evalQuietly(node.inner(), context);
                accumulators.get(entry.getKey())
                        .computeIfAbsent(bucket, b -> new Accumulator(node.func()))
                        .accept(value);
            }
        }

        for (Map.Entry<String, Map<Integer, Accumulator>> entry : accumulators.entrySet()) {
            Map<Integer, Object> byBucket = new HashMap<>();
            entry.getValue().forEach((bucket, acc) -> byBucket.put(bucket, acc.result()));
            store.values.put(entry.getKey(), byBucket);
        }
        return store;
    }

    private static int bucketOf(String scope, GroupIndex groupIndex, int rowIndex) {
        if (scope == null) {
            return 0;
        }
        return groupIndex.instanceIndex(scope, rowIndex);
    }

    @Override
    public Object get(String key, String scope, int rowIndex) {
        Map<Integer, Object> byBucket = values.get(key);
        if (byBucket == null) {
            return null;
        }
        return byBucket.get(bucketOf(scope, groupIndex, rowIndex));
    }

    /** 템플릿 전체를 훑어 집계 노드를 모은다 */
    public static List<ExprNode.Aggregate> collect(Collection<String> expressions,
                                                   Collection<String> interpolatedTexts) {
        List<ExprNode.Aggregate> found = new ArrayList<>();
        for (String expression : expressions) {
            if (expression == null || expression.isBlank()) {
                continue;
            }
            try {
                ExpressionEvaluator.collectAggregates(
                        kr.co.kreport.engine.expression.ExpressionParser.parse(expression), found);
            } catch (RuntimeException ignored) {
                // 식이 깨져 있으면 출력 시점에 빈 값으로 처리된다
            }
        }
        for (String text : interpolatedTexts) {
            ExpressionEvaluator.collectAggregatesFromText(text, found);
        }
        return found;
    }

    // ---------------------------------------------------------------- 누적기

    private static final class Accumulator {

        private final String func;
        private BigDecimal sum = BigDecimal.ZERO;
        private long count;
        private Object min;
        private Object max;
        private Set<Object> distinctValues;

        Accumulator(String func) {
            this.func = func;
            if ("COUNT_DISTINCT".equals(func)) {
                this.distinctValues = new HashSet<>();
            }
        }

        void accept(Object value) {
            if (value == null) {
                return;
            }
            count++;
            switch (func) {
                case "SUM", "AVG" -> sum = sum.add(Values.toDecimalOrZero(value));
                case "MIN" -> {
                    if (min == null || Values.compare(value, min) < 0) {
                        min = value;
                    }
                }
                case "MAX" -> {
                    if (max == null || Values.compare(value, max) > 0) {
                        max = value;
                    }
                }
                case "COUNT_DISTINCT" -> distinctValues.add(Values.toStr(value));
                default -> {
                    // COUNT 는 건수만 센다
                }
            }
        }

        Object result() {
            return switch (func) {
                case "SUM" -> sum;
                case "AVG" -> count == 0
                        ? null
                        : sum.divide(BigDecimal.valueOf(count), Values.DIVISION_SCALE, RoundingMode.HALF_UP)
                        .stripTrailingZeros();
                case "MIN" -> min;
                case "MAX" -> max;
                case "COUNT_DISTINCT" -> BigDecimal.valueOf(distinctValues.size());
                default -> BigDecimal.valueOf(count);
            };
        }
    }
}
