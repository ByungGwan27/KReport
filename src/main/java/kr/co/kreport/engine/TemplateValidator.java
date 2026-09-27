package kr.co.kreport.engine;

import kr.co.kreport.engine.data.SqlGuard;
import kr.co.kreport.engine.expression.ExpressionParser;
import kr.co.kreport.template.Band;
import kr.co.kreport.template.ChartSeriesDef;
import kr.co.kreport.template.ChartSpec;
import kr.co.kreport.template.ChartType;
import kr.co.kreport.template.DataSetDef;
import kr.co.kreport.template.GroupDef;
import kr.co.kreport.template.ReportElement;
import kr.co.kreport.template.ReportTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 저장/실행 전에 템플릿의 정합성을 검사한다.
 *
 * <p>실행 도중에 터지는 오류는 사용자가 원인을 짚기 어렵다. 그룹명 오타나 깨진 표현식처럼
 * 정적으로 잡을 수 있는 것은 저장 시점에 위치와 함께 알려 주는 편이 훨씬 싸게 먹힌다.</p>
 */
public final class TemplateValidator {

    private TemplateValidator() {
    }

    public static void validate(ReportTemplate template) {
        List<String> errors = collectErrors(template);
        if (!errors.isEmpty()) {
            throw new TemplateValidationException(errors);
        }
    }

    public static List<String> collectErrors(ReportTemplate template) {
        List<String> errors = new ArrayList<>();

        if (template.getReportId() == null || template.getReportId().isBlank()) {
            errors.add("리포트 ID 가 없습니다.");
        }
        if (template.getPage() == null) {
            errors.add("페이지 설정이 없습니다.");
            return errors;
        }

        validateDataSet(template.getDataSet(), errors);
        Set<String> groupNames = validateGroups(template.getGroups(), errors);
        validateBands(template, groupNames, errors);

        return errors;
    }

    private static void validateDataSet(DataSetDef dataSet, List<String> errors) {
        if (dataSet == null) {
            errors.add("데이터셋 정의가 없습니다.");
            return;
        }
        if (dataSet.getSourceType() == DataSetDef.SourceType.SQL) {
            try {
                SqlGuard.verifySelect(dataSet.getSql());
            } catch (IllegalArgumentException e) {
                errors.add("데이터셋: " + e.getMessage());
            }
        }
    }

    private static Set<String> validateGroups(List<GroupDef> groups, List<String> errors) {
        Set<String> names = new HashSet<>();
        for (GroupDef group : groups) {
            if (group.getName() == null || group.getName().isBlank()) {
                errors.add("그룹명이 비어 있습니다.");
                continue;
            }
            if (!names.add(group.getName())) {
                errors.add("그룹명이 중복되었습니다: " + group.getName());
            }
            checkExpression(group.getExpression(), "그룹 '" + group.getName() + "' 의 기준식", errors);
        }
        return names;
    }

    private static void validateBands(ReportTemplate template, Set<String> groupNames, List<String> errors) {
        Set<String> seen = new HashSet<>();

        for (Band band : template.getBands()) {
            String label = bandLabel(band);

            String key = band.getType() + "/" + (band.getGroupName() == null ? "" : band.getGroupName());
            if (!seen.add(key)) {
                errors.add(label + " 밴드가 중복 정의되었습니다.");
            }
            if (band.getHeight() <= 0) {
                errors.add(label + " 밴드의 높이는 0보다 커야 합니다.");
            }
            if (band.getType().isGroupScoped()) {
                if (band.getGroupName() == null || band.getGroupName().isBlank()) {
                    errors.add(label + " 밴드에 대상 그룹명이 없습니다.");
                } else if (!groupNames.contains(band.getGroupName())) {
                    errors.add(label + " 밴드가 정의되지 않은 그룹 '" + band.getGroupName() + "' 을(를) 가리킵니다.");
                }
            }

            checkExpression(band.getPrintWhen(), label + " 밴드의 출력조건", errors);
            validateElements(band, label, errors);
        }

        double contentWidth = template.getPage().getContentWidth();
        for (Band band : template.getBands()) {
            for (ReportElement e : band.getElements()) {
                if (e.getRight() > contentWidth + 0.5) {
                    errors.add(bandLabel(band) + " 밴드의 요소가 인쇄 영역을 넘습니다 (오른쪽 "
                            + Math.round(e.getRight()) + "pt > " + Math.round(contentWidth) + "pt).");
                }
            }
        }
    }

    private static void validateElements(Band band, String bandLabel, List<String> errors) {
        for (ReportElement e : band.getElements()) {
            String where = bandLabel + " 밴드의 요소" + (e.getId() == null ? "" : " '" + e.getId() + "'");

            if (e.getWidth() <= 0 || e.getHeight() <= 0) {
                errors.add(where + " 의 크기가 0 이하입니다.");
            }
            if (e.getBottom() > band.getHeight() + 0.5) {
                errors.add(where + " 가 밴드 높이를 벗어납니다.");
            }
            switch (e.getType()) {
                case TEXT, BARCODE, QRCODE -> {
                    if (e.getExpression() == null || e.getExpression().isBlank()) {
                        errors.add(where + " 에 값 표현식이 없습니다.");
                    } else {
                        checkExpression(e.getExpression(), where + " 의 값 표현식", errors);
                    }
                }
                case IMAGE -> {
                    if (e.getSource() == null || e.getSource().isBlank()) {
                        errors.add(where + " 에 이미지 경로가 없습니다.");
                    }
                }
                case CHART -> validateChart(e.getChart(), where, errors);
                default -> {
                }
            }
            checkExpression(e.getPrintWhen(), where + " 의 출력조건", errors);
        }
    }

    /**
     * 차트 정의 검사.
     *
     * <p>계열 수 상한을 강제하는 이유는 색 때문이다. 사람이 안정적으로 구별할 수 있는 색의
     * 수가 한정되어 있어서, 계열을 늘리려고 색을 더 만들면 인접한 두 계열을 분간할 수 없게 된다.
     * 상한을 넘기면 차트를 나누거나 항목을 묶어야 한다.</p>
     */
    private static void validateChart(ChartSpec chart, String where, List<String> errors) {
        if (chart == null) {
            errors.add(where + " 에 차트 설정이 없습니다.");
            return;
        }
        if (chart.getCategoryExpression() == null || chart.getCategoryExpression().isBlank()) {
            errors.add(where + " 에 항목(축) 표현식이 없습니다.");
        } else {
            checkExpression(chart.getCategoryExpression(), where + " 의 항목 표현식", errors);
        }

        if (chart.getSeries().isEmpty()) {
            errors.add(where + " 에 계열이 하나도 없습니다.");
            return;
        }

        int maxSeries = chart.getType().getMaxSeries();
        if (chart.getSeries().size() > maxSeries) {
            errors.add(where + " 의 계열이 " + chart.getSeries().size() + "개입니다. "
                    + chart.getType() + " 는 " + maxSeries
                    + "개까지 구분할 수 있습니다. 차트를 나누거나 계열을 줄이세요.");
        }
        if (chart.getType().isCircular() && chart.getSeries().size() > 1) {
            errors.add(where + " 는 원 그래프라 계열을 하나만 그립니다. "
                    + "여러 값을 비교하려면 막대 차트를 쓰세요.");
        }
        if (chart.getType().isStacked() && chart.getSeries().size() < 2) {
            errors.add(where + " 는 누적 막대인데 계열이 하나뿐입니다. "
                    + "쌓을 것이 없으므로 COLUMN 또는 BAR 을 쓰세요.");
        }
        // 로그 축은 위치로 값을 읽는 꺾은선에서만 뜻이 통한다.
        // 막대는 길이가 곧 크기라서 로그를 얹으면 길이 비율이 값 비율과 달라지고,
        // 원 그래프는 조각 각도가, 영역은 채움 면적이 같은 이유로 왜곡된다.
        if (chart.getValueScale() == ChartSpec.ValueScale.LOG
                && chart.getType() != ChartType.LINE) {
            errors.add(where + " 에 로그 축을 쓸 수 없습니다. "
                    + chart.getType() + " 는 길이나 넓이로 크기를 나타내므로 "
                    + "로그를 적용하면 그림이 값을 잘못 말하게 됩니다. LINE 에서만 쓸 수 있습니다.");
        }

        for (int i = 0; i < chart.getSeries().size(); i++) {
            ChartSeriesDef series = chart.getSeries().get(i);
            String label = where + " 의 계열 " + (i + 1);
            if (series.getExpression() == null || series.getExpression().isBlank()) {
                errors.add(label + " 에 값 표현식이 없습니다.");
            } else {
                checkExpression(series.getExpression(), label + " 의 값 표현식", errors);
            }
            if (series.getColor() != null && !series.getColor().isBlank()
                    && !series.getColor().matches("#[0-9a-fA-F]{3,6}")) {
                errors.add(label + " 의 색상 형식이 올바르지 않습니다: " + series.getColor());
            }
        }
    }

    private static void checkExpression(String expression, String where, List<String> errors) {
        if (expression == null || expression.isBlank()) {
            return;
        }
        try {
            ExpressionParser.parse(expression);
        } catch (RuntimeException ex) {
            errors.add(where + " 이(가) 올바르지 않습니다: " + ex.getMessage());
        }
    }

    private static String bandLabel(Band band) {
        String name = switch (band.getType()) {
            case REPORT_HEADER -> "리포트 머리말";
            case PAGE_HEADER -> "페이지 머리말";
            case GROUP_HEADER -> "그룹 머리말";
            case DETAIL -> "본문";
            case GROUP_FOOTER -> "그룹 꼬리말";
            case PAGE_FOOTER -> "페이지 꼬리말";
            case REPORT_FOOTER -> "리포트 꼬리말";
        };
        return band.getType().isGroupScoped() && band.getGroupName() != null
                ? name + "(" + band.getGroupName() + ")"
                : name;
    }

}
