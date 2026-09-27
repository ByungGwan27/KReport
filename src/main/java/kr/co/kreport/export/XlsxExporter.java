package kr.co.kreport.export;

import kr.co.kreport.engine.layout.RenderedElement;
import kr.co.kreport.engine.layout.RenderedReport;
import kr.co.kreport.template.HorizontalAlign;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 엑셀 파일을 만든다.
 *
 * <p>좌표를 셀에 그대로 옮기지 않고 {@link TabularExtractor} 로 복원한 표를 쓴다.
 * 엑셀로 받는 쪽은 종이 모양이 아니라 정렬하고 합계 낼 수 있는 데이터를 원하기 때문이다.
 * 같은 이유로 숫자는 서식 문자열이 아니라 수치 셀로 넣는다.</p>
 */
@Component
public class XlsxExporter implements ReportExporter {

    /** pt 를 엑셀 컬럼 폭(문자 수 기준) 으로 환산하는 계수 */
    private static final double PT_TO_CHAR_WIDTH = 0.17;

    /** 메모리에 유지할 행 수. 넘으면 임시 파일로 흘려보낸다 */
    private static final int ROW_ACCESS_WINDOW = 500;

    @Override
    public ExportFormat format() {
        return ExportFormat.XLSX;
    }

    @Override
    public void export(RenderedReport report, OutputStream out) throws IOException {
        TabularExtractor.Table table = TabularExtractor.extract(report);

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(ROW_ACCESS_WINDOW)) {
            Sheet sheet = workbook.createSheet(sheetName(report));
            Styles styles = new Styles(workbook);

            int rowIndex = 0;
            rowIndex = writeTitle(sheet, styles, report, table, rowIndex);
            rowIndex = writeParameters(sheet, styles, report, table, rowIndex);
            rowIndex = writeHeader(sheet, styles, table, rowIndex);
            writeBody(sheet, styles, table, rowIndex);

            applyColumnWidths(sheet, table);
            if (!table.isEmpty()) {
                sheet.createFreezePane(0, rowIndex);
            }

            workbook.write(out);
            workbook.dispose();
        }
    }

    private String sheetName(RenderedReport report) {
        String name = report.getTemplate().getName();
        if (name == null || name.isBlank()) {
            return "report";
        }
        // 엑셀 시트명 제약: 31자 이내, : \ / ? * [ ] 사용 불가
        String cleaned = name.replaceAll("[:\\\\/?*\\[\\]]", " ").trim();
        return cleaned.length() > 31 ? cleaned.substring(0, 31) : cleaned;
    }

    private int writeTitle(Sheet sheet, Styles styles, RenderedReport report,
                           TabularExtractor.Table table, int rowIndex) {
        Row row = sheet.createRow(rowIndex);
        row.setHeightInPoints(24);
        Cell cell = row.createCell(0);
        cell.setCellValue(report.getTemplate().getName());
        cell.setCellStyle(styles.title);

        int lastColumn = Math.max(0, table.columns().size() - 1);
        if (lastColumn > 0) {
            sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 0, lastColumn));
        }
        return rowIndex + 1;
    }

    /** 조회 조건을 함께 남긴다. 나중에 파일만 봐도 어떤 조건의 결과인지 알 수 있어야 한다. */
    private int writeParameters(Sheet sheet, Styles styles, RenderedReport report,
                                TabularExtractor.Table table, int rowIndex) {
        Map<String, Object> parameters = report.getParameters();
        if (parameters == null || parameters.isEmpty()) {
            return rowIndex;
        }
        StringBuilder sb = new StringBuilder("조회조건: ");
        boolean first = true;
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            String label = report.getTemplate().parameter(entry.getKey())
                    .map(p -> p.getLabel() == null || p.getLabel().isBlank() ? p.getName() : p.getLabel())
                    .orElse(entry.getKey());
            if (!first) {
                sb.append(" | ");
            }
            sb.append(label).append('=').append(entry.getValue() == null ? "" : entry.getValue());
            first = false;
        }

        Row row = sheet.createRow(rowIndex);
        Cell cell = row.createCell(0);
        cell.setCellValue(sb.toString());
        cell.setCellStyle(styles.note);

        int lastColumn = Math.max(0, table.columns().size() - 1);
        if (lastColumn > 0) {
            sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 0, lastColumn));
        }
        return rowIndex + 2;
    }

    private int writeHeader(Sheet sheet, Styles styles, TabularExtractor.Table table, int rowIndex) {
        if (table.isEmpty()) {
            return rowIndex;
        }
        Row row = sheet.createRow(rowIndex);
        row.setHeightInPoints(20);
        List<TabularExtractor.Column> columns = table.columns();
        for (int i = 0; i < columns.size(); i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(columns.get(i).title());
            cell.setCellStyle(styles.header);
        }
        return rowIndex + 1;
    }

    private void writeBody(Sheet sheet, Styles styles, TabularExtractor.Table table, int startRow) {
        List<TabularExtractor.Column> columns = table.columns();
        int rowIndex = startRow;

        for (List<RenderedElement> dataRow : table.rows()) {
            Row row = sheet.createRow(rowIndex++);
            for (int i = 0; i < columns.size(); i++) {
                RenderedElement element = i < dataRow.size() ? dataRow.get(i) : null;
                Cell cell = row.createCell(i);
                writeValue(cell, styles, element, columns.get(i).align());
            }
        }
    }

    private void writeValue(Cell cell, Styles styles, RenderedElement element, HorizontalAlign align) {
        if (element == null) {
            cell.setCellStyle(styles.forAlign(align));
            return;
        }
        Object raw = element.getRawValue();

        switch (raw) {
            case BigDecimal d -> {
                cell.setCellValue(d.doubleValue());
                cell.setCellStyle(styles.numeric(element.getFormat()));
            }
            case Number n -> {
                cell.setCellValue(n.doubleValue());
                cell.setCellStyle(styles.numeric(element.getFormat()));
            }
            case LocalDate d -> {
                cell.setCellValue(d);
                cell.setCellStyle(styles.date);
            }
            case LocalDateTime d -> {
                cell.setCellValue(d);
                cell.setCellStyle(styles.dateTime);
            }
            case null, default -> {
                cell.setCellValue(element.getText() == null ? "" : element.getText());
                cell.setCellStyle(styles.forAlign(align));
            }
        }
    }

    private void applyColumnWidths(Sheet sheet, TabularExtractor.Table table) {
        List<TabularExtractor.Column> columns = table.columns();
        for (int i = 0; i < columns.size(); i++) {
            int chars = (int) Math.round(columns.get(i).width() * PT_TO_CHAR_WIDTH);
            sheet.setColumnWidth(i, Math.max(6, Math.min(60, chars)) * 256);
        }
    }

    // ---------------------------------------------------------------- 셀 서식

    private static final class Styles {

        /** POI 의 셀 서식 개수에는 상한이 있어 패턴별로 한 번만 만들어 돌려 쓴다 */
        private final Map<String, CellStyle> numericCache = new HashMap<>();
        private final SXSSFWorkbook workbook;

        final CellStyle title;
        final CellStyle note;
        final CellStyle header;
        final CellStyle number;
        final CellStyle date;
        final CellStyle dateTime;
        final CellStyle left;
        final CellStyle center;
        final CellStyle right;

        Styles(SXSSFWorkbook workbook) {
            this.workbook = workbook;
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);

            title = workbook.createCellStyle();
            title.setFont(titleFont);
            title.setAlignment(HorizontalAlignment.CENTER);
            title.setVerticalAlignment(VerticalAlignment.CENTER);

            note = workbook.createCellStyle();
            note.setAlignment(HorizontalAlignment.LEFT);

            header = bordered(workbook);
            header.setFont(headerFont);
            header.setAlignment(HorizontalAlignment.CENTER);
            header.setVerticalAlignment(VerticalAlignment.CENTER);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            var formats = workbook.createDataFormat();

            number = bordered(workbook);
            number.setAlignment(HorizontalAlignment.RIGHT);
            number.setDataFormat(formats.getFormat("#,##0.###"));

            date = bordered(workbook);
            date.setAlignment(HorizontalAlignment.CENTER);
            date.setDataFormat(formats.getFormat("yyyy-mm-dd"));

            dateTime = bordered(workbook);
            dateTime.setAlignment(HorizontalAlignment.CENTER);
            dateTime.setDataFormat(formats.getFormat("yyyy-mm-dd hh:mm"));

            left = bordered(workbook);
            left.setAlignment(HorizontalAlignment.LEFT);
            center = bordered(workbook);
            center.setAlignment(HorizontalAlignment.CENTER);
            right = bordered(workbook);
            right.setAlignment(HorizontalAlignment.RIGHT);
        }

        private CellStyle bordered(SXSSFWorkbook workbook) {
            CellStyle style = workbook.createCellStyle();
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            style.setVerticalAlignment(VerticalAlignment.CENTER);
            return style;
        }

        CellStyle forAlign(HorizontalAlign align) {
            return switch (align == null ? HorizontalAlign.LEFT : align) {
                case CENTER -> center;
                case RIGHT -> right;
                case LEFT -> left;
            };
        }

        /**
         * 화면에 적용된 포맷을 엑셀 셀 서식으로 옮긴다.
         *
         * <p>비율을 0.77 로 넣고 서식만 일반 숫자로 두면 받는 쪽에서 "77%" 가 아니라
         * "0.766" 을 보게 된다. 리포트에서 퍼센트로 보이던 칸은 엑셀에서도 퍼센트여야 한다.</p>
         */
        CellStyle numeric(String pattern) {
            String excelPattern = toExcelNumberFormat(pattern);
            if (excelPattern == null) {
                return number;
            }
            return numericCache.computeIfAbsent(excelPattern, p -> {
                CellStyle style = bordered(workbook);
                style.setAlignment(HorizontalAlignment.RIGHT);
                style.setDataFormat(workbook.createDataFormat().getFormat(p));
                return style;
            });
        }

        /** DecimalFormat 패턴은 엑셀 서식과 문법이 거의 같다. 날짜 패턴이면 숫자 서식으로 쓰지 않는다. */
        private String toExcelNumberFormat(String pattern) {
            if (pattern == null || pattern.isBlank()) {
                return null;
            }
            for (int i = 0; i < pattern.length(); i++) {
                char c = pattern.charAt(i);
                if (c == 'y' || c == 'M' || c == 'd' || c == 'H') {
                    return null;
                }
                if (c != '#' && c != '0' && c != ',' && c != '.' && c != '%'
                        && c != '-' && c != '+' && c != ' ' && c != ';') {
                    return null;
                }
            }
            return pattern;
        }
    }
}
