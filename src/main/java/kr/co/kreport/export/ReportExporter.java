package kr.co.kreport.export;

import kr.co.kreport.engine.layout.RenderedReport;

import java.io.IOException;
import java.io.OutputStream;

/**
 * 레이아웃 결과를 특정 형식으로 써낸다.
 */
public interface ReportExporter {

    ExportFormat format();

    void export(RenderedReport report, OutputStream out) throws IOException;
}
