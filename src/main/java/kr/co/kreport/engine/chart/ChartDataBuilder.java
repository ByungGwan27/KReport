package kr.co.kreport.engine.chart;

import kr.co.kreport.engine.data.DataTable;
import kr.co.kreport.engine.expression.EvalContext;
import kr.co.kreport.engine.expression.ExpressionEvaluator;
import kr.co.kreport.engine.expression.Values;
import kr.co.kreport.template.ChartAggregation;
import kr.co.kreport.template.ChartSeriesDef;
import kr.co.kreport.template.ChartSortOrder;
import kr.co.kreport.template.ChartSpec;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 데이터 행 구간을 항목별로 집계해 차트 데이터로 만든다.
 *
 * <p>항목 수가 차트가 감당할 수 있는 선을 넘으면 큰 값부터 남기고 나머지를 "기타"로 묶는다.
 * 색을 늘려서 해결하지 않는 이유는, 구분 가능한 색의 개수 자체가 한정되어 있어서
 * 색을 더 만들면 인접한 두 항목을 구별할 수 없게 되기 때문이다.</p>
 */
public final class ChartDataBuilder {

    private ChartDataBuilder() {
    }

    /**
     * @param fromRow 집계 시작 행(포함)
     * @param toRow   집계 끝 행(포함)
     */
    public static ChartDataset build(ChartSpec spec, DataTable data, EvalContext context,
                                     int fromRow, int toRow) {
        if (spec == null || spec.getSeries().isEmpty() || data.isEmpty()) {
            return ChartDataset.empty();
        }
        int start = Math.max(0, fromRow);
        int end = Math.min(data.size() - 1, toRow);
        if (start > end) {
            return ChartDataset.empty();
        }

        // 항목 등장 순서를 유지한 채 계열별 누적기를 모은다
        Map<String, Accumulator[]> byCategory = new LinkedHashMap<>();
        List<ChartSeriesDef> seriesDefs = spec.getSeries();

        int savedRowIndex = context.getRowIndex();
        Map<String, Object> savedRow = context.getRow();

        try {
            for (int row = start; row <= end; row++) {
                context.setRow(data.row(row), row);

                Object rawCategory = ExpressionEvaluator.evalQuietly(spec.getCategoryExpression(), context);
                String category = Values.isBlank(rawCategory) ? "(미지정)" : Values.toStr(rawCategory);

                Accumulator[] accumulators = byCategory.computeIfAbsent(category, key -> {
                    Accumulator[] created = new Accumulator[seriesDefs.size()];
                    for (int i = 0; i < created.length; i++) {
                        created[i] = new Accumulator(seriesDefs.get(i).getAggregation());
                    }
                    return created;
                });

                for (int i = 0; i < seriesDefs.size(); i++) {
                    accumulators[i].accept(
                            ExpressionEvaluator.evalQuietly(seriesDefs.get(i).getExpression(), context));
                }
            }
        } finally {
            context.setRow(savedRow, savedRowIndex);
        }

        List<Entry> collected = new ArrayList<>(byCategory.size());
        byCategory.forEach((category, accumulators) -> {
            BigDecimal[] values = new BigDecimal[accumulators.length];
            for (int i = 0; i < accumulators.length; i++) {
                values[i] = accumulators[i].result();
            }
            collected.add(new Entry(category, values));
        });

        sort(collected, spec.getSort());
        return toDataset(foldOverflow(collected, spec), spec);
    }

    private static void sort(List<Entry> entries, ChartSortOrder order) {
        Comparator<Entry> comparator = switch (order) {
            case NONE -> null;
            case VALUE_DESC -> Comparator.comparing(Entry::primary).reversed();
            case VALUE_ASC -> Comparator.comparing(Entry::primary);
            case CATEGORY_ASC -> Comparator.comparing(Entry::category);
            case CATEGORY_DESC -> Comparator.comparing(Entry::category).reversed();
        };
        if (comparator != null) {
            entries.sort(comparator);
        }
    }

    /**
     * 상한을 넘는 항목을 "기타" 하나로 묶는다.
     *
     * <p>정렬 기준과 무관하게 <b>값이 큰 것부터</b> 남긴다. 남길 항목을 고르는 일과
     * 남은 것을 늘어놓는 순서는 별개이기 때문이다. 묶고 난 뒤 원래 정렬을 다시 적용한다.</p>
     */
    private static List<Entry> foldOverflow(List<Entry> entries, ChartSpec spec) {
        int limit = spec.effectiveMaxCategories();
        if (entries.size() <= limit || limit <= 1) {
            return entries;
        }

        List<Entry> byValue = new ArrayList<>(entries);
        byValue.sort(Comparator.comparing(Entry::primary).reversed());

        List<Entry> kept = new ArrayList<>(byValue.subList(0, limit - 1));
        List<Entry> folded = byValue.subList(limit - 1, byValue.size());

        int seriesCount = entries.get(0).values().length;
        BigDecimal[] otherValues = new BigDecimal[seriesCount];
        for (int i = 0; i < seriesCount; i++) {
            BigDecimal sum = BigDecimal.ZERO;
            for (Entry e : folded) {
                if (e.values()[i] != null) {
                    sum = sum.add(e.values()[i]);
                }
            }
            otherValues[i] = sum;
        }

        // 남긴 항목만 원래 순서대로 되돌린 뒤 "기타"를 맨 끝에 붙인다
        List<Entry> result = new ArrayList<>();
        for (Entry e : entries) {
            if (kept.contains(e)) {
                result.add(e);
            }
        }
        result.add(new Entry(spec.getOtherLabel(), otherValues));
        return result;
    }

    private static ChartDataset toDataset(List<Entry> entries, ChartSpec spec) {
        List<String> categories = new ArrayList<>(entries.size());
        for (Entry e : entries) {
            categories.add(e.category());
        }

        List<ChartSeriesDef> defs = spec.getSeries();
        List<ChartDataset.Series> series = new ArrayList<>(defs.size());

        for (int i = 0; i < defs.size(); i++) {
            List<BigDecimal> values = new ArrayList<>(entries.size());
            for (Entry e : entries) {
                values.add(e.values()[i]);
            }
            ChartSeriesDef def = defs.get(i);
            String color = def.getColor() != null && !def.getColor().isBlank()
                    ? def.getColor()
                    : ChartPalette.series(spec.getPalette(), i);
            String name = def.getName() == null || def.getName().isBlank()
                    ? "계열 " + (i + 1)
                    : def.getName();
            series.add(new ChartDataset.Series(name, values, color));
        }
        return new ChartDataset(categories, series);
    }

    /** 집계 중간 결과 */
    private record Entry(String category, BigDecimal[] values) {

        /** 정렬과 상한 판단에 쓰는 대표값. 첫 계열의 값이다. */
        BigDecimal primary() {
            return values.length > 0 && values[0] != null ? values[0] : BigDecimal.ZERO;
        }
    }

    private static final class Accumulator {

        private final ChartAggregation aggregation;
        private BigDecimal sum = BigDecimal.ZERO;
        private long count;
        private BigDecimal min;
        private BigDecimal max;

        Accumulator(ChartAggregation aggregation) {
            this.aggregation = aggregation == null ? ChartAggregation.SUM : aggregation;
        }

        void accept(Object value) {
            if (value == null) {
                return;
            }
            count++;
            if (aggregation == ChartAggregation.COUNT) {
                return;
            }
            BigDecimal d = Values.toDecimal(value);
            if (d == null) {
                return;
            }
            sum = sum.add(d);
            if (min == null || d.compareTo(min) < 0) {
                min = d;
            }
            if (max == null || d.compareTo(max) > 0) {
                max = d;
            }
        }

        BigDecimal result() {
            return switch (aggregation) {
                case COUNT -> BigDecimal.valueOf(count);
                case AVG -> count == 0
                        ? null
                        : sum.divide(BigDecimal.valueOf(count), 6, RoundingMode.HALF_UP)
                        .stripTrailingZeros();
                case MIN -> min;
                case MAX -> max;
                case SUM -> sum;
            };
        }
    }
}
