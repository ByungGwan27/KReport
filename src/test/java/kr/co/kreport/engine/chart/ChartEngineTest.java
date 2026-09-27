package kr.co.kreport.engine.chart;

import kr.co.kreport.engine.data.DataTable;
import kr.co.kreport.engine.expression.EvalContext;
import kr.co.kreport.template.ChartAggregation;
import kr.co.kreport.template.ChartSeriesDef;
import kr.co.kreport.template.ChartSortOrder;
import kr.co.kreport.template.ChartSpec;
import kr.co.kreport.template.ChartType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ChartEngineTest {

    // ---------------------------------------------------------------- 픽스처

    private DataTable data(Object[]... rows) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Object[] r : rows) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("DEPT", r[0]);
            row.put("AMT", r[1]);
            list.add(row);
        }
        return new DataTable(List.of("DEPT", "AMT"), list);
    }

    private EvalContext context(DataTable table) {
        EvalContext context = new EvalContext();
        context.setColumnAlias(table.getColumnAlias());
        return context;
    }

    private ChartSpec spec(ChartType type, ChartAggregation aggregation) {
        ChartSpec spec = new ChartSpec();
        spec.setType(type);
        spec.setCategoryExpression("{DEPT}");

        ChartSeriesDef series = new ChartSeriesDef();
        series.setName("금액");
        series.setExpression("{AMT}");
        series.setAggregation(aggregation);
        spec.getSeries().add(series);
        return spec;
    }

    private <T extends ChartShape> List<T> shapesOf(List<ChartShape> shapes, Class<T> type) {
        List<T> found = new ArrayList<>();
        for (ChartShape s : shapes) {
            if (type.isInstance(s)) {
                found.add(type.cast(s));
            }
        }
        return found;
    }

    private List<String> textsOf(List<ChartShape> shapes) {
        List<String> texts = new ArrayList<>();
        for (ChartShape.Text t : shapesOf(shapes, ChartShape.Text.class)) {
            texts.add(t.text());
        }
        return texts;
    }

    // ---------------------------------------------------------------- 집계

    @Test
    @DisplayName("같은 항목의 값을 모아 집계한다")
    void aggregatesByCategory() {
        DataTable table = data(
                new Object[]{"복지정책과", new BigDecimal("100")},
                new Object[]{"환경관리과", new BigDecimal("50")},
                new Object[]{"복지정책과", new BigDecimal("200")});

        ChartDataset dataset = ChartDataBuilder.build(
                spec(ChartType.COLUMN, ChartAggregation.SUM), table, context(table), 0, 2);

        assertThat(dataset.categories()).containsExactly("복지정책과", "환경관리과");
        assertThat(dataset.value(0, 0)).isEqualByComparingTo("300");
        assertThat(dataset.value(0, 1)).isEqualByComparingTo("50");
    }

    @Test
    @DisplayName("집계 방식별 결과")
    void aggregationModes() {
        DataTable table = data(
                new Object[]{"A", new BigDecimal("10")},
                new Object[]{"A", new BigDecimal("30")});
        EvalContext context = context(table);

        assertThat(ChartDataBuilder.build(spec(ChartType.COLUMN, ChartAggregation.AVG),
                table, context, 0, 1).value(0, 0)).isEqualByComparingTo("20");
        assertThat(ChartDataBuilder.build(spec(ChartType.COLUMN, ChartAggregation.MAX),
                table, context, 0, 1).value(0, 0)).isEqualByComparingTo("30");
        assertThat(ChartDataBuilder.build(spec(ChartType.COLUMN, ChartAggregation.COUNT),
                table, context, 0, 1).value(0, 0)).isEqualByComparingTo("2");
    }

    @Test
    @DisplayName("행 구간을 좁히면 그 구간만 집계한다")
    void respectsRowRange() {
        DataTable table = data(
                new Object[]{"A", new BigDecimal("10")},
                new Object[]{"B", new BigDecimal("20")},
                new Object[]{"C", new BigDecimal("30")});

        ChartDataset dataset = ChartDataBuilder.build(
                spec(ChartType.COLUMN, ChartAggregation.SUM), table, context(table), 1, 2);

        assertThat(dataset.categories()).containsExactly("B", "C");
    }

    @Test
    @DisplayName("항목 상한을 넘으면 작은 것들을 기타로 묶는다")
    void foldsOverflowIntoOther() {
        DataTable table = data(
                new Object[]{"A", new BigDecimal("100")},
                new Object[]{"B", new BigDecimal("90")},
                new Object[]{"C", new BigDecimal("5")},
                new Object[]{"D", new BigDecimal("3")},
                new Object[]{"E", new BigDecimal("2")});

        ChartSpec spec = spec(ChartType.PIE, ChartAggregation.SUM);
        spec.setMaxCategories(3);
        spec.setSort(ChartSortOrder.VALUE_DESC);

        ChartDataset dataset = ChartDataBuilder.build(spec, table, context(table), 0, 4);

        assertThat(dataset.categories()).containsExactly("A", "B", "기타");
        // 5 + 3 + 2
        assertThat(dataset.value(0, 2)).isEqualByComparingTo("10");
    }

    @Test
    @DisplayName("정렬 기준을 적용한다")
    void sorting() {
        DataTable table = data(
                new Object[]{"B", new BigDecimal("10")},
                new Object[]{"A", new BigDecimal("30")});

        ChartSpec spec = spec(ChartType.COLUMN, ChartAggregation.SUM);
        spec.setSort(ChartSortOrder.VALUE_DESC);
        assertThat(ChartDataBuilder.build(spec, table, context(table), 0, 1).categories())
                .containsExactly("A", "B");

        spec.setSort(ChartSortOrder.CATEGORY_ASC);
        assertThat(ChartDataBuilder.build(spec, table, context(table), 0, 1).categories())
                .containsExactly("A", "B");
    }

    @Test
    @DisplayName("집계해도 바깥 컨텍스트의 현재 행은 그대로 남는다")
    void restoresContextRow() {
        DataTable table = data(new Object[]{"A", new BigDecimal("10")});
        EvalContext context = context(table);
        context.setRow(table.row(0), 7);

        ChartDataBuilder.build(spec(ChartType.COLUMN, ChartAggregation.SUM), table, context, 0, 0);

        assertThat(context.getRowIndex()).isEqualTo(7);
    }

    // ---------------------------------------------------------------- 축

    @Test
    @DisplayName("축 눈금은 읽기 좋은 값으로 맞춘다")
    void niceAxisScale() {
        AxisScale scale = AxisScale.of(BigDecimal.ZERO, new BigDecimal("87300"), 5, true);

        assertThat(scale.min()).isEqualTo(0);
        assertThat(scale.max()).isGreaterThanOrEqualTo(87300);
        assertThat(scale.step()).isEqualTo(20000);
        assertThat(scale.ticks()).containsExactly(0.0, 20000.0, 40000.0, 60000.0, 80000.0, 100000.0);
    }

    @Test
    @DisplayName("막대 차트의 축은 0을 포함한다")
    void barAxisIncludesZero() {
        AxisScale scale = AxisScale.of(new BigDecimal("500"), new BigDecimal("900"), 5, true);
        assertThat(scale.min()).isEqualTo(0);
    }

    @Test
    @DisplayName("정수 서식이면 눈금도 정수로 놓는다")
    void integerAxisAvoidsFractionalSteps() {
        // 건수 12건을 5칸으로 나누면 2.5 가 나오는데, 정수 서식으로 찍으면
        // 0·3·5·8·10 처럼 간격이 들쭉날쭉해 보인다.
        AxisScale fractional = AxisScale.of(BigDecimal.ZERO, new BigDecimal("12"), 5, true, false);
        assertThat(fractional.step()).isEqualTo(2.5);

        AxisScale integral = AxisScale.of(BigDecimal.ZERO, new BigDecimal("12"), 5, true, true);
        assertThat(integral.step()).isEqualTo(5.0);
        assertThat(integral.ticks()).containsExactly(0.0, 5.0, 10.0, 15.0);
    }

    @Test
    @DisplayName("건수 차트의 축 눈금에는 소수가 섞이지 않는다")
    void countChartHasIntegerTicks() {
        ChartSpec spec = spec(ChartType.COLUMN, ChartAggregation.COUNT);
        spec.setValueFormat("#,##0");

        List<BigDecimal> counts = List.of(
                new BigDecimal("12"), new BigDecimal("11"), new BigDecimal("9"));
        ChartDataset dataset = new ChartDataset(List.of("A", "B", "C"),
                List.of(new ChartDataset.Series("건수", counts, ChartPalette.series(0))));

        List<String> texts = textsOf(ChartLayoutEngine.layout(spec, dataset, 300, 180));
        assertThat(texts).doesNotContain("3", "8", "13");
    }

    @Test
    @DisplayName("값이 모두 같아도 축을 만들 수 있다")
    void flatData() {
        AxisScale scale = AxisScale.of(new BigDecimal("100"), new BigDecimal("100"), 5, true);
        assertThat(scale.span()).isGreaterThan(0);
        assertThat(scale.ticks()).isNotEmpty();
    }

    // ---------------------------------------------------------------- 레이아웃

    private ChartDataset dataset(int categories, int seriesCount) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < categories; i++) {
            names.add("항목" + (i + 1));
        }
        List<ChartDataset.Series> series = new ArrayList<>();
        for (int s = 0; s < seriesCount; s++) {
            List<BigDecimal> values = new ArrayList<>();
            for (int i = 0; i < categories; i++) {
                values.add(BigDecimal.valueOf((i + 1) * 100L * (s + 1)));
            }
            series.add(new ChartDataset.Series("계열" + (s + 1), values, ChartPalette.series(s)));
        }
        return new ChartDataset(names, series);
    }

    @Test
    @DisplayName("막대는 항목 수만큼, 계열 수만큼 그려진다")
    void barShapes() {
        ChartSpec spec = spec(ChartType.COLUMN, ChartAggregation.SUM);
        List<ChartShape> shapes = ChartLayoutEngine.layout(spec, dataset(4, 2), 300, 180);

        // 막대 8개 + 범례 표식 2개
        assertThat(shapesOf(shapes, ChartShape.Rect.class)).hasSize(10);
        assertThat(textsOf(shapes)).contains("계열1", "계열2", "항목1", "항목4");
    }

    @Test
    @DisplayName("계열이 하나면 범례를 만들지 않는다")
    void singleSeriesHasNoLegend() {
        ChartSpec spec = spec(ChartType.COLUMN, ChartAggregation.SUM);
        List<ChartShape> shapes = ChartLayoutEngine.layout(spec, dataset(3, 1), 300, 180);

        assertThat(textsOf(shapes)).doesNotContain("계열1");
        assertThat(shapesOf(shapes, ChartShape.Rect.class)).hasSize(3);
    }

    @Test
    @DisplayName("원 그래프는 계열이 하나여도 항목 범례가 붙는다")
    void pieAlwaysHasLegend() {
        ChartSpec spec = spec(ChartType.PIE, ChartAggregation.SUM);
        List<ChartShape> shapes = ChartLayoutEngine.layout(spec, dataset(3, 1), 240, 200);

        assertThat(shapesOf(shapes, ChartShape.Sector.class)).hasSize(3);
        assertThat(textsOf(shapes)).contains("항목1", "항목2", "항목3");
    }

    @Test
    @DisplayName("도넛은 가운데에 합계를 적는다")
    void donutShowsTotal() {
        ChartSpec spec = spec(ChartType.DONUT, ChartAggregation.SUM);
        List<ChartShape> shapes = ChartLayoutEngine.layout(spec, dataset(3, 1), 260, 220);

        // 100 + 200 + 300
        assertThat(textsOf(shapes)).contains("600");
        assertThat(shapesOf(shapes, ChartShape.Sector.class))
                .allMatch(s -> s.innerR() > 0);
    }

    @Test
    @DisplayName("꺾은선은 선 하나와 점마다 표식을 그린다")
    void lineShapes() {
        ChartSpec spec = spec(ChartType.LINE, ChartAggregation.SUM);
        List<ChartShape> shapes = ChartLayoutEngine.layout(spec, dataset(5, 1), 300, 180);

        assertThat(shapesOf(shapes, ChartShape.Polyline.class)).hasSize(1);
        assertThat(shapesOf(shapes, ChartShape.Circle.class)).hasSize(5);
    }

    @Test
    @DisplayName("값 표시를 켜도 꺾은선은 끝점 하나만 적는다")
    void lineLabelsOnlyEndpoint() {
        ChartSpec spec = spec(ChartType.LINE, ChartAggregation.SUM);
        spec.setShowValues(true);
        List<ChartShape> shapes = ChartLayoutEngine.layout(spec, dataset(5, 1), 300, 180);

        // 축 눈금도 같은 숫자를 쓰므로, 값 라벨(진한 잉크)만 골라 센다
        List<String> valueLabels = new ArrayList<>();
        for (ChartShape.Text t : shapesOf(shapes, ChartShape.Text.class)) {
            if (ChartPalette.TEXT_PRIMARY.equals(t.fill())) {
                valueLabels.add(t.text());
            }
        }
        assertThat(valueLabels).containsExactly("500");
    }

    @Test
    @DisplayName("막대는 값이 끝나는 쪽만 둥글린다")
    void barRoundsOnlyDataEnd() {
        assertThat(ChartLayoutEngine.layout(spec(ChartType.COLUMN, ChartAggregation.SUM),
                dataset(3, 1), 300, 180))
                .filteredOn(ChartShape.Rect.class::isInstance)
                .extracting(s -> ((ChartShape.Rect) s).roundedEnd())
                .containsOnly(ChartShape.RoundedEnd.TOP);

        assertThat(ChartLayoutEngine.layout(spec(ChartType.BAR, ChartAggregation.SUM),
                dataset(3, 1), 300, 180))
                .filteredOn(ChartShape.Rect.class::isInstance)
                .extracting(s -> ((ChartShape.Rect) s).roundedEnd())
                .containsOnly(ChartShape.RoundedEnd.RIGHT);
    }

    // ---------------------------------------------------------------- 누적 막대

    /** 항목마다 계열 값을 직접 지정한 데이터셋 */
    private ChartDataset stackDataset(String[] categories, String[] seriesNames, double[][] values) {
        List<ChartDataset.Series> series = new ArrayList<>();
        for (int s = 0; s < seriesNames.length; s++) {
            List<BigDecimal> column = new ArrayList<>();
            for (int c = 0; c < categories.length; c++) {
                column.add(BigDecimal.valueOf(values[s][c]));
            }
            series.add(new ChartDataset.Series(seriesNames[s], column, ChartPalette.series(s)));
        }
        return new ChartDataset(List.of(categories), series);
    }

    @Test
    @DisplayName("누적 축은 개별 값이 아니라 항목별 합계를 담는다")
    void stackedAxisUsesTotals() {
        ChartDataset dataset = stackDataset(
                new String[]{"A", "B"},
                new String[]{"완료", "진행"},
                new double[][]{{30, 10}, {20, 5}});

        // 개별 최댓값은 30 이지만 A 의 합계는 50 이다
        assertThat(dataset.max()).isEqualByComparingTo("30");
        assertThat(dataset.stackedMax()).isEqualByComparingTo("50");
        assertThat(dataset.positiveTotal(0)).isEqualByComparingTo("50");
        assertThat(dataset.positiveTotal(1)).isEqualByComparingTo("15");
    }

    @Test
    @DisplayName("누적 막대는 계열을 위로 쌓아 한 칸에 하나만 그린다")
    void stackedBarsShareOneSlot() {
        ChartSpec spec = spec(ChartType.STACKED_COLUMN, ChartAggregation.SUM);
        ChartDataset dataset = stackDataset(
                new String[]{"A", "B"},
                new String[]{"완료", "진행"},
                new double[][]{{30, 10}, {20, 5}});

        List<ChartShape.Rect> bars = shapesOf(
                ChartLayoutEngine.layout(spec, dataset, 320, 200), ChartShape.Rect.class);
        // 조각 4개 + 범례 표식 2개
        assertThat(bars).hasSize(6);

        // 같은 항목의 두 조각은 x 가 같고(한 칸을 공유) y 는 다르다(쌓인다)
        ChartShape.Rect first = bars.get(0);
        ChartShape.Rect second = bars.get(1);
        assertThat(second.x()).isEqualTo(first.x());
        assertThat(second.width()).isEqualTo(first.width());
        assertThat(second.y()).isLessThan(first.y());
    }

    @Test
    @DisplayName("쌓인 조각 사이는 지면 폭만큼 떨어져 있다")
    void stackedSegmentsAreSeparatedBySurfaceGap() {
        ChartSpec spec = spec(ChartType.STACKED_COLUMN, ChartAggregation.SUM);
        ChartDataset dataset = stackDataset(
                new String[]{"A"},
                new String[]{"아래", "위"},
                new double[][]{{50}, {50}});

        List<ChartShape.Rect> bars = shapesOf(
                ChartLayoutEngine.layout(spec, dataset, 320, 200), ChartShape.Rect.class);

        ChartShape.Rect lower = bars.get(0);
        ChartShape.Rect upper = bars.get(1);
        double gap = lower.y() - (upper.y() + upper.height());

        assertThat(gap).isGreaterThan(0.5).isLessThan(3.0);
    }

    @Test
    @DisplayName("누적은 바깥쪽 조각만 둥글린다")
    void stackedRoundsOnlyOutermostSegment() {
        ChartSpec spec = spec(ChartType.STACKED_COLUMN, ChartAggregation.SUM);
        ChartDataset dataset = stackDataset(
                new String[]{"A"},
                new String[]{"아래", "가운데", "위"},
                new double[][]{{30}, {30}, {30}});

        List<ChartShape.Rect> bars = shapesOf(
                ChartLayoutEngine.layout(spec, dataset, 320, 200), ChartShape.Rect.class);

        // 앞의 셋이 조각, 나머지는 범례 표식
        assertThat(bars.get(0).roundedEnd()).isEqualTo(ChartShape.RoundedEnd.NONE);
        assertThat(bars.get(1).roundedEnd()).isEqualTo(ChartShape.RoundedEnd.NONE);
        assertThat(bars.get(2).roundedEnd()).isEqualTo(ChartShape.RoundedEnd.TOP);
    }

    @Test
    @DisplayName("음수 계열은 기준선 아래로 따로 쌓인다")
    void negativeSeriesStackDownward() {
        ChartSpec spec = spec(ChartType.STACKED_COLUMN, ChartAggregation.SUM);
        ChartDataset dataset = stackDataset(
                new String[]{"A"},
                new String[]{"수입", "지출"},
                new double[][]{{40}, {-20}});

        assertThat(dataset.stackedMax()).isEqualByComparingTo("40");
        assertThat(dataset.stackedMin()).isEqualByComparingTo("-20");

        List<ChartShape> shapes = ChartLayoutEngine.layout(spec, dataset, 320, 200);
        List<ChartShape.Rect> bars = shapesOf(shapes, ChartShape.Rect.class);

        // 양수 조각은 기준선 위, 음수 조각은 아래
        ChartShape.Rect positive = bars.get(0);
        ChartShape.Rect negative = bars.get(1);
        assertThat(positive.y() + positive.height()).isLessThanOrEqualTo(negative.y() + 0.01);
    }

    @Test
    @DisplayName("누적은 막대 끝에 합계를 적는다")
    void stackedShowsTotal() {
        ChartSpec spec = spec(ChartType.STACKED_BAR, ChartAggregation.SUM);
        spec.setShowValues(true);
        ChartDataset dataset = stackDataset(
                new String[]{"A"},
                new String[]{"완료", "진행"},
                new double[][]{{30}, {20}});

        assertThat(textsOf(ChartLayoutEngine.layout(spec, dataset, 340, 200))).contains("50");
    }

    @Test
    @DisplayName("조각이 글자보다 얇으면 값을 적지 않는다")
    void tinySegmentsDropTheirLabel() {
        ChartSpec spec = spec(ChartType.STACKED_COLUMN, ChartAggregation.SUM);
        spec.setShowValues(true);
        // 두 번째 계열은 전체의 1% 라 조각이 글자보다 얇다
        ChartDataset dataset = stackDataset(
                new String[]{"A"},
                new String[]{"큼", "작음"},
                new double[][]{{990}, {10}});

        List<String> inked = new ArrayList<>();
        for (ChartShape.Text t : shapesOf(
                ChartLayoutEngine.layout(spec, dataset, 320, 200), ChartShape.Text.class)) {
            if (ChartPalette.SURFACE.equals(t.fill()) || ChartPalette.TEXT_PRIMARY.equals(t.fill())) {
                inked.add(t.text());
            }
        }
        // 합계 1,000 과 큰 조각의 990 은 적히지만 얇은 조각의 10 은 빠진다
        assertThat(inked).doesNotContain("10");
    }

    // ---------------------------------------------------------------- 100% 누적

    @Test
    @DisplayName("100% 누적은 총량이 달라도 막대 길이를 맞춘다")
    void percentStackEqualisesBarLength() {
        ChartSpec spec = spec(ChartType.PERCENT_BAR, ChartAggregation.SUM);
        // A 는 100건, B 는 10건이지만 구성비는 둘 다 8:2 다
        ChartDataset dataset = stackDataset(
                new String[]{"A", "B"},
                new String[]{"완료", "진행"},
                new double[][]{{80, 8}, {20, 2}});

        List<ChartShape.Rect> bars = shapesOf(
                ChartLayoutEngine.layout(spec, dataset, 340, 200), ChartShape.Rect.class);

        // 같은 계열의 두 조각은 총량이 10배 달라도 같은 길이로 그려진다
        assertThat(bars.get(0).width()).isCloseTo(bars.get(2).width(), within(0.01));
        assertThat(bars.get(1).width()).isCloseTo(bars.get(3).width(), within(0.01));
    }

    @Test
    @DisplayName("100% 누적의 축 눈금은 0%에서 100%까지다")
    void percentAxisRunsToHundred() {
        ChartSpec spec = spec(ChartType.PERCENT_COLUMN, ChartAggregation.SUM);
        spec.setValueFormat("#,##0");
        ChartDataset dataset = stackDataset(
                new String[]{"A"},
                new String[]{"완료", "진행"},
                new double[][]{{7000}, {3000}});

        List<String> texts = textsOf(ChartLayoutEngine.layout(spec, dataset, 320, 200));
        assertThat(texts).contains("0%", "25%", "50%", "75%", "100%");
        // 값 서식(#,##0)은 축에 쓰이지 않는다
        assertThat(texts).doesNotContain("7,000");
    }

    @Test
    @DisplayName("100% 누적의 조각은 비율을, 막대 끝은 원래 합계를 적는다")
    void percentLabelsShowRatioAndTotal() {
        ChartSpec spec = spec(ChartType.PERCENT_BAR, ChartAggregation.SUM);
        spec.setShowValues(true);
        ChartDataset dataset = stackDataset(
                new String[]{"A"},
                new String[]{"완료", "진행"},
                new double[][]{{75}, {25}});

        List<String> texts = textsOf(ChartLayoutEngine.layout(spec, dataset, 360, 200));
        // 조각은 비율
        assertThat(texts).contains("75%", "25%");
        // 막대 끝에는 길이를 맞추느라 지워진 총량이 남는다
        assertThat(texts).contains("100");
    }

    @Test
    @DisplayName("100% 누적에서 음수는 그리지 않는다")
    void percentStackSkipsNegatives() {
        ChartSpec spec = spec(ChartType.PERCENT_COLUMN, ChartAggregation.SUM);
        ChartDataset dataset = stackDataset(
                new String[]{"A"},
                new String[]{"수입", "지출"},
                new double[][]{{100}, {-40}});

        List<ChartShape.Rect> bars = shapesOf(
                ChartLayoutEngine.layout(spec, dataset, 320, 200), ChartShape.Rect.class);
        // 조각 1개 + 범례 표식 2개. 음수 계열은 조각이 되지 않는다.
        assertThat(bars).hasSize(3);
    }

    @Test
    @DisplayName("합계가 0인 항목은 비율을 만들 수 없어 건너뛴다")
    void percentStackSkipsEmptyCategory() {
        ChartSpec spec = spec(ChartType.PERCENT_COLUMN, ChartAggregation.SUM);
        ChartDataset dataset = stackDataset(
                new String[]{"A", "빈항목"},
                new String[]{"완료", "진행"},
                new double[][]{{60, 0}, {40, 0}});

        List<ChartShape.Rect> bars = shapesOf(
                ChartLayoutEngine.layout(spec, dataset, 320, 200), ChartShape.Rect.class);
        // A 의 조각 2개 + 범례 2개. 빈 항목에서는 아무것도 그리지 않는다.
        assertThat(bars).hasSize(4);
    }

    @Test
    @DisplayName("조각 값 글자는 바탕 밝기에 따라 색이 바뀐다")
    void segmentInkFollowsFillBrightness() {
        // 노랑처럼 밝은 바탕에는 어두운 글자, 보라처럼 어두운 바탕에는 흰 글자
        assertThat(ChartStyle.contrastingInk("#eda100")).isEqualTo(ChartPalette.TEXT_PRIMARY);
        assertThat(ChartStyle.contrastingInk("#4a3aa7")).isEqualTo(ChartPalette.SURFACE);
        assertThat(ChartStyle.contrastingInk("#2a78d6")).isEqualTo(ChartPalette.SURFACE);
    }

    // ---------------------------------------------------------------- 로그 축

    private ChartSpec lineSpec(ChartSpec.ValueScale scale) {
        ChartSpec spec = spec(ChartType.LINE, ChartAggregation.SUM);
        spec.setValueScale(scale);
        spec.setValueFormat("#,##0");
        return spec;
    }

    private ChartDataset lineDataset(double... values) {
        List<String> categories = new ArrayList<>();
        List<BigDecimal> column = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            categories.add("p" + (i + 1));
            column.add(BigDecimal.valueOf(values[i]));
        }
        return new ChartDataset(categories,
                List.of(new ChartDataset.Series("값", column, ChartPalette.series(0))));
    }

    @Test
    @DisplayName("로그 축은 같은 배수를 같은 거리에 놓는다")
    void logAxisSpacesByRatio() {
        LogScale scale = LogScale.of(BigDecimal.ONE, new BigDecimal("1000"));

        // 1→10 과 10→100 은 둘 다 10배라 축 위에서 같은 간격이어야 한다
        double first = scale.ratio(10) - scale.ratio(1);
        double second = scale.ratio(100) - scale.ratio(10);
        double third = scale.ratio(1000) - scale.ratio(100);

        assertThat(first).isCloseTo(second, within(1e-9));
        assertThat(second).isCloseTo(third, within(1e-9));
        assertThat(scale.ratio(1)).isEqualTo(0);
        assertThat(scale.ratio(1000)).isEqualTo(1);
    }

    @Test
    @DisplayName("로그 축 범위는 10의 거듭제곱까지 넓힌다")
    void logAxisSnapsToDecades() {
        LogScale scale = LogScale.of(new BigDecimal("30"), new BigDecimal("8000"));

        assertThat(scale.min()).isEqualTo(10);
        assertThat(scale.max()).isEqualTo(10000);
    }

    @Test
    @DisplayName("자릿수 폭이 좁으면 거듭제곱 사이에 눈금을 더 넣는다")
    void logAxisAddsIntermediateTicks() {
        // 두 자릿수 폭: 1,2,5 배수까지
        assertThat(LogScale.of(new BigDecimal("12"), new BigDecimal("400")).ticks())
                .contains(10.0, 20.0, 50.0, 100.0, 200.0, 500.0);

        // 폭이 넓어지면 눈금을 성기게 둔다
        assertThat(LogScale.of(BigDecimal.ONE, new BigDecimal("900000")).ticks())
                .containsExactly(1.0, 10.0, 100.0, 1000.0, 10000.0, 100000.0, 1000000.0);
    }

    @Test
    @DisplayName("데이터가 정확히 거듭제곱이어도 축 폭이 생긴다")
    void logAxisHandlesExactDecade() {
        LogScale scale = LogScale.of(new BigDecimal("100"), new BigDecimal("100"));

        assertThat(scale.max()).isGreaterThan(scale.min());
        assertThat(scale.ticks()).isNotEmpty();
    }

    @Test
    @DisplayName("0이나 음수가 섞이면 로그 축을 쓸 수 없다")
    void logAxisRejectsNonPositive() {
        assertThat(LogScale.isUsable(lineDataset(10, 100, 1000))).isTrue();
        assertThat(LogScale.isUsable(lineDataset(10, 0, 1000))).isFalse();
        assertThat(LogScale.isUsable(lineDataset(10, -5, 1000))).isFalse();
    }

    @Test
    @DisplayName("로그 축을 켜면 자릿수가 다른 값도 함께 읽힌다")
    void logAxisKeepsSmallValuesVisible() {
        // 3 과 3000 이 섞인 데이터. 선형에서는 작은 값들이 바닥에 깔린다.
        ChartDataset dataset = lineDataset(3, 30, 300, 3000);

        List<ChartShape.Circle> linear = shapesOf(
                ChartLayoutEngine.layout(lineSpec(ChartSpec.ValueScale.LINEAR), dataset, 300, 180),
                ChartShape.Circle.class);
        List<ChartShape.Circle> log = shapesOf(
                ChartLayoutEngine.layout(lineSpec(ChartSpec.ValueScale.LOG), dataset, 300, 180),
                ChartShape.Circle.class);

        // 선형에서는 앞의 세 점이 거의 같은 높이에 몰린다
        double linearSpread = linear.get(0).cy() - linear.get(2).cy();
        // 로그에서는 세 점이 고르게 벌어진다
        double logSpread = log.get(0).cy() - log.get(2).cy();

        assertThat(logSpread).isGreaterThan(linearSpread * 3);
    }

    @Test
    @DisplayName("로그 축의 눈금 숫자는 거듭제곱 값으로 찍힌다")
    void logAxisTickLabels() {
        List<String> texts = textsOf(ChartLayoutEngine.layout(
                lineSpec(ChartSpec.ValueScale.LOG), lineDataset(5, 50, 5000), 320, 200));

        assertThat(texts).contains("1", "10", "100", "1,000", "10,000");
    }

    @Test
    @DisplayName("막대에 로그를 설정해도 선형으로 그린다")
    void logIsIgnoredOnBars() {
        ChartSpec spec = spec(ChartType.COLUMN, ChartAggregation.SUM);
        spec.setValueScale(ChartSpec.ValueScale.LOG);
        spec.setValueFormat("#,##0");

        List<String> texts = textsOf(
                ChartLayoutEngine.layout(spec, lineDataset(3, 30, 300, 3000), 320, 200));

        // 선형 눈금(0 에서 시작)이 나온다. 로그 축이었다면 0 이 있을 수 없다.
        assertThat(texts).contains("0");
    }

    @Test
    @DisplayName("0이 섞인 데이터는 로그를 켜도 선형으로 되돌린다")
    void logFallsBackWhenDataHasZero() {
        List<String> texts = textsOf(ChartLayoutEngine.layout(
                lineSpec(ChartSpec.ValueScale.LOG), lineDataset(0, 50, 500), 320, 200));

        assertThat(texts).contains("0");
    }

    @Test
    @DisplayName("가로 축 눈금 숫자가 서로 겹치지 않는다")
    void horizontalTickLabelsDoNotCollide() {
        // 금액처럼 자릿수가 긴 값을 좁은 폭에 넣으면 눈금 숫자가 서로 밀어낸다
        ChartSpec spec = spec(ChartType.BAR, ChartAggregation.SUM);
        spec.setValueFormat("#,##0");
        ChartDataset dataset = stackDataset(
                new String[]{"A", "B"},
                new String[]{"금액"},
                new double[][]{{4_137_900_000d, 911_900_000d}});

        List<ChartShape> shapes = ChartLayoutEngine.layout(spec, dataset, 250, 150);

        // 축 눈금 라벨만 추려 x 순으로 늘어놓고 이웃 간 간격을 확인한다
        List<ChartShape.Text> labels = new ArrayList<>();
        for (ChartShape.Text t : shapesOf(shapes, ChartShape.Text.class)) {
            if (ChartPalette.TEXT_SECONDARY.equals(t.fill())
                    && t.anchor() == ChartShape.Anchor.MIDDLE
                    && t.text().matches("[0-9,]+")) {
                labels.add(t);
            }
        }
        labels.sort((a, b) -> Double.compare(a.x(), b.x()));

        for (int i = 1; i < labels.size(); i++) {
            ChartShape.Text left = labels.get(i - 1);
            ChartShape.Text right = labels.get(i);
            double halfSpan = (ChartTextMetrics.width(left.text(), left.fontSize())
                    + ChartTextMetrics.width(right.text(), right.fontSize())) / 2;
            assertThat(right.x() - left.x())
                    .as("'%s' 와 '%s' 사이 간격", left.text(), right.text())
                    .isGreaterThanOrEqualTo(halfSpan);
        }
        // 그래도 눈금이 아예 사라지지는 않는다
        assertThat(labels).isNotEmpty();
    }

    @Test
    @DisplayName("항목 라벨이 서로 겹치거나 플롯 밖으로 나가지 않는다")
    void categoryLabelsStayInsideAndApart() {
        // 계약번호처럼 긴 이름이 24개. 전부 찍으면 서로 밀어낸다.
        List<String> categories = new ArrayList<>();
        List<BigDecimal> values = new ArrayList<>();
        for (int i = 1; i <= 24; i++) {
            categories.add(String.format("2026-계약-%05d", i));
            values.add(BigDecimal.valueOf(1000L * i));
        }
        ChartDataset dataset = new ChartDataset(categories,
                List.of(new ChartDataset.Series("금액", values, ChartPalette.series(0))));

        ChartSpec spec = spec(ChartType.LINE, ChartAggregation.SUM);
        spec.setValueFormat("#,##0");
        List<ChartShape> shapes = ChartLayoutEngine.layout(spec, dataset, 275, 150);

        List<ChartShape.Text> labels = new ArrayList<>();
        for (ChartShape.Text t : shapesOf(shapes, ChartShape.Text.class)) {
            if (t.text().startsWith("2026-계약-")) {
                labels.add(t);
            }
        }
        assertThat(labels).as("항목 라벨이 하나도 없으면 축을 읽을 수 없다").isNotEmpty();

        labels.sort((a, b) -> Double.compare(a.x(), b.x()));
        double previousRight = Double.NEGATIVE_INFINITY;
        for (ChartShape.Text t : labels) {
            double width = ChartTextMetrics.width(t.text(), t.fontSize());
            double left = switch (t.anchor()) {
                case START -> t.x();
                case MIDDLE -> t.x() - width / 2;
                case END -> t.x() - width;
            };
            assertThat(left).as("'%s' 가 차트 왼쪽으로 삐져나감", t.text())
                    .isGreaterThanOrEqualTo(-0.01);
            assertThat(left + width).as("'%s' 가 차트 오른쪽으로 삐져나감", t.text())
                    .isLessThanOrEqualTo(275 + 0.01);
            assertThat(left).as("'%s' 가 앞 라벨과 겹침", t.text())
                    .isGreaterThanOrEqualTo(previousRight);
            previousRight = left + width;
        }
    }

    @Test
    @DisplayName("모든 도형이 차트 상자 안에 들어간다")
    void shapesStayInsideBox() {
        double width = 300;
        double height = 180;

        for (ChartType type : ChartType.values()) {
            ChartSpec spec = spec(type, ChartAggregation.SUM);
            spec.setShowValues(true);
            spec.setTitle("제목");
            int seriesCount = type.isCircular() ? 1 : (type.isStacked() ? 3 : 2);

            List<ChartShape> shapes =
                    ChartLayoutEngine.layout(spec, dataset(4, seriesCount), width, height);

            for (ChartShape.Rect r : shapesOf(shapes, ChartShape.Rect.class)) {
                assertThat(r.x()).as("%s 막대 왼쪽", type).isGreaterThanOrEqualTo(-0.01);
                assertThat(r.x() + r.width()).as("%s 막대 오른쪽", type)
                        .isLessThanOrEqualTo(width + 0.01);
                assertThat(r.y() + r.height()).as("%s 막대 아래", type)
                        .isLessThanOrEqualTo(height + 0.01);
            }
            for (ChartShape.Sector s : shapesOf(shapes, ChartShape.Sector.class)) {
                assertThat(s.cx() - s.outerR()).as("%s 원 왼쪽", type).isGreaterThanOrEqualTo(-0.01);
                assertThat(s.cx() + s.outerR()).as("%s 원 오른쪽", type)
                        .isLessThanOrEqualTo(width + 0.01);
            }
        }
    }

    @Test
    @DisplayName("데이터가 없으면 안내 문구만 남긴다")
    void emptyDataset() {
        List<ChartShape> shapes = ChartLayoutEngine.layout(
                spec(ChartType.COLUMN, ChartAggregation.SUM), ChartDataset.empty(), 200, 120);

        assertThat(textsOf(shapes)).containsExactly("표시할 자료가 없습니다.");
    }

    @Test
    @DisplayName("팔레트는 슬롯을 돌려 쓰지 않는다")
    void paletteDoesNotCycle() {
        assertThat(ChartPalette.series(0)).isEqualTo("#2a78d6");
        // 8색을 넘어서면 새 색을 만들지 않고 마지막 색을 유지한다
        assertThat(ChartPalette.series(8)).isEqualTo(ChartPalette.series(7));
        assertThat(ChartPalette.series(20)).isEqualTo(ChartPalette.series(7));
    }
}
