package kr.co.kreport.export;

import kr.co.kreport.engine.layout.RenderedElement;
import kr.co.kreport.engine.layout.RenderedReport;
import kr.co.kreport.engine.expression.Values;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * CSV 를 만든다.
 *
 * <p>UTF-8 BOM 을 앞에 붙인다. BOM 이 없으면 엑셀이 시스템 기본 인코딩으로 열어서
 * 한글이 깨지는데, 현업에서 CSV 는 대부분 엑셀로 열린다.</p>
 */
@Component
public class CsvExporter implements ReportExporter {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    @Override
    public ExportFormat format() {
        return ExportFormat.CSV;
    }

    @Override
    public void export(RenderedReport report, OutputStream out) throws IOException {
        out.write(UTF8_BOM);
        Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8);

        TabularExtractor.Table table = TabularExtractor.extract(report);

        List<TabularExtractor.Column> columns = table.columns();
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                w.write(',');
            }
            w.write(quote(columns.get(i).title()));
        }
        w.write("\r\n");

        for (List<RenderedElement> row : table.rows()) {
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) {
                    w.write(',');
                }
                w.write(quote(cellValue(i < row.size() ? row.get(i) : null)));
            }
            w.write("\r\n");
        }
        w.flush();
    }

    /**
     * 수치는 천단위 구분기호를 뺀 원본을 쓴다. 서식이 들어간 문자열은 받는 쪽에서
     * 다시 숫자로 되돌려야 하는 번거로움이 생긴다.
     */
    private String cellValue(RenderedElement element) {
        if (element == null) {
            return "";
        }
        Object raw = element.getRawValue();
        if (raw instanceof Number || raw instanceof java.math.BigDecimal) {
            return Values.toStr(raw);
        }
        return element.getText() == null ? "" : element.getText();
    }

    private String quote(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        boolean needsQuote = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0
                || value.startsWith(" ")
                || value.endsWith(" ");
        if (!needsQuote) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
