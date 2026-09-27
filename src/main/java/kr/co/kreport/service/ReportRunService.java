package kr.co.kreport.service;

import kr.co.kreport.access.ReportAccessService;
import kr.co.kreport.access.Viewer;
import kr.co.kreport.domain.ReportDefinition;
import kr.co.kreport.engine.ReportEngine;
import kr.co.kreport.engine.layout.RenderedReport;
import kr.co.kreport.export.ExportFormat;
import kr.co.kreport.export.HtmlExporter;
import kr.co.kreport.export.ReportExporter;
import kr.co.kreport.license.LicenseService;
import kr.co.kreport.template.ReportTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 리포트를 실행해 원하는 형식으로 내보내고 이력을 남긴다.
 */
@Slf4j
@Service
public class ReportRunService {

    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final ReportDefinitionService definitionService;
    private final ReportAccessService accessService;
    private final LicenseService licenses;
    private final ReportEngine engine;
    private final ExecutionLogService executionLog;
    private final HtmlExporter htmlExporter;
    private final Map<ExportFormat, ReportExporter> exporters = new EnumMap<>(ExportFormat.class);

    public ReportRunService(ReportDefinitionService definitionService,
                            ReportAccessService accessService,
                            LicenseService licenses,
                            ReportEngine engine,
                            ExecutionLogService executionLog,
                            List<ReportExporter> exporterList,
                            HtmlExporter htmlExporter) {
        this.definitionService = definitionService;
        this.accessService = accessService;
        this.licenses = licenses;
        this.engine = engine;
        this.executionLog = executionLog;
        this.htmlExporter = htmlExporter;
        exporterList.forEach(e -> exporters.put(e.format(), e));
    }

    /** 뷰어 화면에 끼워 넣을 HTML 조각 */
    public ViewerContent renderForViewer(String reportId, Map<String, ?> input, Caller caller) {
        ReportTemplate template = authorize(reportId, caller);
        long startedAt = System.currentTimeMillis();
        try {
            RenderedReport report = engine.run(template, input);
            executionLog.success(reportId, template.getName(), ExportFormat.HTML, input, report, caller);
            return toViewerContent(report);
        } catch (RuntimeException e) {
            executionLog.failure(reportId, template.getName(), ExportFormat.HTML, input, caller, e,
                    System.currentTimeMillis() - startedAt);
            throw e;
        }
    }

    /**
     * 내보낼 준비를 끝낸 결과. 남은 일은 스트림에 쓰는 것뿐이다.
     */
    public record ExportJob(RenderedReport report, ReportExporter exporter, String fileName) {
    }

    /**
     * 조회 조건 검증부터 레이아웃까지 끝내 둔다. 응답에 손대기 전에 부른다.
     *
     * <p>내보내기를 두 단계로 나눈 이유가 있다. 응답 스트림을 한 번 열고 나면 상태 코드도
     * 헤더도 바꿀 수 없어서, 그 뒤에 조회 조건 오류가 나면 예외 처리기가 손쓸 수 없고
     * 받는 쪽은 깨진 파일을 받는다. 실패할 수 있는 일은 전부 여기서 끝낸다.</p>
     */
    public ExportJob prepare(String reportId, Map<String, ?> input, ExportFormat format,
                             Caller caller) {
        ReportTemplate template = authorize(reportId, caller);
        ReportExporter exporter = exporters.get(format);
        if (exporter == null) {
            throw new IllegalArgumentException("지원하지 않는 출력 형식입니다: " + format);
        }

        long startedAt = System.currentTimeMillis();
        try {
            RenderedReport report = engine.run(template, input);
            executionLog.success(reportId, template.getName(), format, input, report, caller);
            return new ExportJob(report, exporter, fileName(template.getName(), format));
        } catch (RuntimeException e) {
            executionLog.failure(reportId, template.getName(), format, input, caller, e,
                    System.currentTimeMillis() - startedAt);
            throw e;
        }
    }

    /**
     * 열람 권한을 확인하고 템플릿을 집어 온다.
     *
     * <p>목록에서 가려 놓는 것만으로는 부족하다. 주소를 직접 치거나 예전에 받아 둔 링크를
     * 다시 누르면 목록을 거치지 않고 여기로 들어오므로, 실제로 자료를 읽기 직전인 이 자리가
     * 마지막 문이다.</p>
     */
    private ReportTemplate authorize(String reportId, Caller caller) {
        // 기동 때 한 번 본 것으로는 모자란다. 서버를 몇 달씩 켜 두는 곳이 많아,
        // 계약이 끝난 뒤에도 재기동 전까지 계속 도는 것을 막아야 한다.
        licenses.requireValid();
        ReportDefinition definition = definitionService.get(reportId);
        accessService.requireView(definition, caller.viewer());
        return definitionService.loadTemplate(reportId);
    }

    /** 준비된 결과를 스트림에 쓴다 */
    public void write(ExportJob job, OutputStream out) throws IOException {
        job.exporter().export(job.report(), out);
    }

    /**
     * 디자이너 미리보기. 저장되지 않은 템플릿을 그대로 실행하므로 이력을 남기지 않는다.
     *
     * <p>저장을 거치지 않고 임의 SQL 이 실행되는 경로라, 데이터소스 권한을 여기서 다시
     * 확인한다. 저장할 때만 막으면 저장하지 않고 미리보기만 돌려 남의 DB 를 읽을 수 있다.</p>
     */
    public ViewerContent preview(ReportTemplate template, Map<String, ?> input, Viewer editor) {
        definitionService.requireDataSourceAccess(template, editor);
        return toViewerContent(engine.run(template, input));
    }

    private ViewerContent toViewerContent(RenderedReport report) {
        return new ViewerContent(
                htmlExporter.renderPages(report),
                htmlExporter.baseCss(report),
                report.getPageCount(),
                report.getRowCount(),
                report.getElapsedMillis());
    }

    /** RFC 5987. 한글 파일명이 브라우저별로 깨지는 것을 막는다. */
    public static String encodeFileName(String fileName) {
        return URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String fileName(String reportName, ExportFormat format) {
        String base = reportName == null || reportName.isBlank() ? "report" : reportName;
        base = base.replaceAll("[\\\\/:*?\"<>|]", "_");
        return base + "_" + LocalDateTime.now().format(FILE_STAMP) + "." + format.getExtension();
    }

    /** 실행 주체. 인증 연동 전까지는 세션 사용자 또는 anonymous 가 들어온다. */
    /**
     * 실행을 요청한 사람.
     *
     * @param viewer   열람 판단과 감사 이력에 함께 쓴다
     * @param clientIp 감사 이력에 남길 접속지
     */
    public record Caller(Viewer viewer, String clientIp) {

        /** 감사 이력에 적히는 이름 */
        public String userId() {
            return viewer.username();
        }
    }

    public record ViewerContent(String html, String css, int pageCount, int rowCount, long elapsedMillis) {
    }
}
