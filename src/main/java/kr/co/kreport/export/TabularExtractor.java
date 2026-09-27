package kr.co.kreport.export;

import kr.co.kreport.engine.layout.RenderedElement;
import kr.co.kreport.engine.layout.RenderedPage;
import kr.co.kreport.engine.layout.RenderedReport;
import kr.co.kreport.template.Band;
import kr.co.kreport.template.BandType;
import kr.co.kreport.template.ElementType;
import kr.co.kreport.template.HorizontalAlign;
import kr.co.kreport.template.ReportElement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 좌표로 배치된 출력 결과에서 표 형태를 되뽑아 낸다.
 *
 * <p>엑셀과 CSV 는 "종이 이미지"가 아니라 "표"를 원한다. 좌표를 셀에 1:1로 옮기면
 * 병합 셀 범벅이 되어 받는 쪽에서 정렬도 합계도 못 낸다. 그래서 본문 밴드의 요소 배치를
 * 컬럼 정의로 보고, 같은 x 구간에 걸친 머리말 라벨을 컬럼 제목으로 짝지어 표를 복원한다.</p>
 *
 * <p>행을 고를 때는 좌표가 아니라 요소의 출처 밴드를 본다. 좌표로 추리면 컬럼과 같은 자리에
 * 놓인 머리말 라벨이나 소계 칸이 데이터 행으로 딸려 들어간다.</p>
 */
public final class TabularExtractor {

    /** 컬럼 제목과 x 구간 */
    public record Column(String title, String elementId, double x, double width, HorizontalAlign align) {

        public double right() {
            return x + width;
        }
    }

    public record Table(List<Column> columns, List<List<RenderedElement>> rows) {

        public boolean isEmpty() {
            return columns.isEmpty();
        }
    }

    private TabularExtractor() {
    }

    public static Table extract(RenderedReport report) {
        Band detail = report.getTemplate().band(BandType.DETAIL).orElse(null);
        if (detail == null) {
            return new Table(List.of(), List.of());
        }

        List<Column> columns = buildColumns(report, detail);
        if (columns.isEmpty()) {
            return new Table(List.of(), List.of());
        }
        return new Table(columns, collectRows(report, detail, columns));
    }

    // ---------------------------------------------------------------- 컬럼

    private static List<Column> buildColumns(RenderedReport report, Band detail) {
        double marginLeft = report.getTemplate().getPage().getMarginLeft();

        List<ReportElement> cells = detail.getElements().stream()
                .filter(e -> e.getType() == ElementType.TEXT || e.getType() == ElementType.LABEL)
                .sorted(Comparator.comparingDouble(ReportElement::getX))
                .toList();

        List<ReportElement> candidates = titleCandidates(report);

        // 같은 자리에 출력조건으로 갈라 놓은 요소들(정상/지연 표시 등)은 한 컬럼으로 합친다
        Map<String, Column> merged = new LinkedHashMap<>();
        for (ReportElement cell : cells) {
            double absoluteX = marginLeft + cell.getX();
            String slot = Math.round(absoluteX) + ":" + Math.round(cell.getWidth());
            if (merged.containsKey(slot)) {
                continue;
            }
            String title = findTitle(candidates, marginLeft, absoluteX, cell.getWidth());
            merged.put(slot, new Column(
                    title == null || title.isBlank() ? fallbackTitle(cell, merged.size()) : title,
                    cell.getId(),
                    absoluteX,
                    cell.getWidth(),
                    cell.getStyle().getAlign()));
        }
        return new ArrayList<>(merged.values());
    }

    /**
     * 컬럼 제목 후보가 되는 라벨을 추린다.
     *
     * <p>머리말 밴드의 라벨을 전부 후보로 두면 "○○년도 집행 현황" 같은 제목 줄이
     * 모든 컬럼의 제목으로 붙는다. 컬럼 제목은 관례적으로 머리말 밴드의 맨 아랫줄에 오므로
     * 밴드마다 가장 아래 줄만 후보로 남긴다.</p>
     */
    private static List<ReportElement> titleCandidates(RenderedReport report) {
        List<ReportElement> candidates = new ArrayList<>();

        for (Band band : report.getTemplate().getBands()) {
            if (band.getType() != BandType.PAGE_HEADER
                    && band.getType() != BandType.GROUP_HEADER
                    && band.getType() != BandType.REPORT_HEADER) {
                continue;
            }
            List<ReportElement> labels = band.getElements().stream()
                    .filter(e -> e.getType() == ElementType.LABEL)
                    .filter(e -> e.getText() != null && !e.getText().isBlank())
                    .toList();
            if (labels.isEmpty()) {
                continue;
            }
            double bottomRow = labels.stream().mapToDouble(ReportElement::getY).max().orElse(0);
            for (ReportElement e : labels) {
                // 같은 줄로 볼 허용 오차. 라벨 높이가 조금씩 달라도 한 줄로 묶이게 한다.
                if (Math.abs(e.getY() - bottomRow) <= 2.0) {
                    candidates.add(e);
                }
            }
        }
        return candidates;
    }

    /**
     * 컬럼 구간을 덮는 제목 후보 중 가장 잘 맞는 것을 고른다.
     * 여러 컬럼을 가로지르는 넓은 라벨은 제목으로 보지 않는다.
     */
    private static String findTitle(List<ReportElement> candidates, double marginLeft,
                                    double x, double width) {
        String best = null;
        double bestOverlap = 0;

        for (ReportElement e : candidates) {
            double ex = marginLeft + e.getX();
            double overlap = Math.min(x + width, ex + e.getWidth()) - Math.max(x, ex);
            if (overlap <= bestOverlap || overlap < width * 0.5) {
                continue;
            }
            // 라벨이 컬럼보다 크게 넓으면 여러 칸을 묶는 제목이므로 제외한다
            if (e.getWidth() > width * 1.8) {
                continue;
            }
            bestOverlap = overlap;
            best = e.getText();
        }
        return best;
    }

    private static String fallbackTitle(ReportElement cell, int index) {
        if (cell.getId() != null && !cell.getId().isBlank()) {
            return cell.getId();
        }
        return "컬럼" + (index + 1);
    }

    // ---------------------------------------------------------------- 행

    /**
     * 본문 밴드에서 나온 요소만 모아 y 좌표로 행을 묶는다.
     */
    private static List<List<RenderedElement>> collectRows(RenderedReport report, Band detail,
                                                           List<Column> columns) {
        double rowGap = Math.max(1.0, detail.getHeight() * 0.5);
        Map<String, Integer> slotIndex = new HashMap<>();
        for (int i = 0; i < columns.size(); i++) {
            slotIndex.put(slotKey(columns.get(i).x(), columns.get(i).width()), i);
        }

        List<List<RenderedElement>> rows = new ArrayList<>();

        for (RenderedPage page : report.getPages()) {
            List<RenderedElement> cells = page.getElements().stream()
                    .filter(e -> e.getBandType() == BandType.DETAIL)
                    .filter(RenderedElement::isTextual)
                    .filter(e -> slotIndex.containsKey(slotKey(e.getX(), e.getWidth())))
                    .sorted(Comparator.comparingDouble(RenderedElement::getY)
                            .thenComparingDouble(RenderedElement::getX))
                    .toList();

            List<RenderedElement> current = null;
            double currentTop = Double.NaN;

            for (RenderedElement e : cells) {
                if (current == null || Math.abs(e.getY() - currentTop) > rowGap) {
                    if (current != null) {
                        rows.add(current);
                    }
                    current = newRow(columns.size());
                    currentTop = e.getY();
                }
                Integer index = slotIndex.get(slotKey(e.getX(), e.getWidth()));
                // 출력조건으로 갈라진 요소 중 실제로 찍힌 쪽만 값이 있다
                if (index != null && current.get(index) == null) {
                    current.set(index, e);
                }
            }
            if (current != null) {
                rows.add(current);
            }
        }
        return rows;
    }

    private static List<RenderedElement> newRow(int size) {
        List<RenderedElement> row = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            row.add(null);
        }
        return row;
    }

    /** 좌표를 정수로 반올림해 슬롯 키로 쓴다. 부동소수 오차로 매칭이 어긋나지 않도록. */
    private static String slotKey(double x, double width) {
        return Math.round(x) + ":" + Math.round(width);
    }
}
