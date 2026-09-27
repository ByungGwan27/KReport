package kr.co.kreport.engine.layout;

import kr.co.kreport.engine.data.DataTable;
import kr.co.kreport.template.ReportTemplate;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 레이아웃이 끝난 리포트 전체 */
@Getter
@Setter
public class RenderedReport {

    private ReportTemplate template;

    private final List<RenderedPage> pages = new ArrayList<>();

    /** 실행에 쓰인 조회 조건 */
    private Map<String, Object> parameters = new LinkedHashMap<>();

    /** 원본 조회 결과. XLSX/CSV 처럼 좌표가 아니라 표 형태로 내보낼 때 쓴다. */
    private DataTable data;

    private long elapsedMillis;

    public int getPageCount() {
        return pages.size();
    }

    public int getRowCount() {
        return data == null ? 0 : data.size();
    }

    public void add(RenderedPage page) {
        pages.add(page);
    }
}
