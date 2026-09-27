package kr.co.kreport.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.co.kreport.access.CurrentViewer;
import kr.co.kreport.access.ReportAccessService;
import kr.co.kreport.access.Viewer;
import kr.co.kreport.domain.ReportDefinition;
import kr.co.kreport.engine.TemplateValidator;
import kr.co.kreport.engine.data.DataTable;
import kr.co.kreport.engine.data.DataSourceRegistry;
import kr.co.kreport.engine.data.JdbcDataSetProvider;
import kr.co.kreport.engine.data.SqlGuard;
import kr.co.kreport.engine.expression.FunctionRegistry;
import kr.co.kreport.engine.expression.ReportFunction;
import kr.co.kreport.export.ExportFormat;
import kr.co.kreport.service.ReportDefinitionService;
import kr.co.kreport.service.ReportRunService;
import kr.co.kreport.template.DataSetDef;
import kr.co.kreport.template.ReportTemplate;
import kr.co.kreport.web.dto.DeleteResult;
import kr.co.kreport.web.dto.FieldList;
import kr.co.kreport.web.dto.FunctionCatalog;
import kr.co.kreport.web.dto.ReportMeta;
import kr.co.kreport.web.dto.ReportSummary;
import kr.co.kreport.web.dto.SaveResult;
import kr.co.kreport.web.dto.ValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 리포트 정의 관리와 실행 API.
 *
 * <p>응답은 모두 {@code web.dto} 의 레코드로 내보낸다. {@code Map} 으로 조립하면 응답의
 * 모양이 코드 어디에도 적혀 있지 않게 되어, 키 이름을 고쳤을 때 무엇이 깨지는지 컴파일러가
 * 알려 주지 못한다. 연계 기관에 넘기는 규격서를 자동으로 뽑을 수도 없다.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/reports")
public class ReportApiController {

    private static final List<String> AGGREGATE_NAMES =
            List.of("SUM", "AVG", "MIN", "MAX", "COUNT", "COUNT_DISTINCT");

    private static final List<String> VARIABLE_NAMES =
            List.of("PAGE_NO", "PAGE_COUNT", "ROW_NUM", "GROUP_ROW_NUM",
                    "TOTAL_ROWS", "REPORT_NAME", "PRINT_DATE", "PRINT_TIME");

    private final ReportDefinitionService definitionService;
    private final ReportRunService runService;
    private final ReportAccessService accessService;
    private final CurrentViewer currentViewer;
    private final JdbcDataSetProvider jdbcDataSetProvider;
    private final DataSourceRegistry dataSources;
    private final ObjectMapper mapper;

    public ReportApiController(ReportDefinitionService definitionService,
                               ReportRunService runService,
                               ReportAccessService accessService,
                               CurrentViewer currentViewer,
                               JdbcDataSetProvider jdbcDataSetProvider,
                               DataSourceRegistry dataSources,
                               ObjectMapper templateObjectMapper) {
        this.definitionService = definitionService;
        this.runService = runService;
        this.accessService = accessService;
        this.currentViewer = currentViewer;
        this.jdbcDataSetProvider = jdbcDataSetProvider;
        this.dataSources = dataSources;
        this.mapper = templateObjectMapper;
    }

    // ---------------------------------------------------------------- 정의 관리

    @GetMapping
    public List<ReportSummary> list(@RequestParam(required = false) String keyword,
                                    HttpServletRequest request) {
        List<ReportDefinition> definitions = keyword == null || keyword.isBlank()
                ? definitionService.findAll()
                : definitionService.search(keyword);

        List<ReportSummary> result = new ArrayList<>();
        for (ReportDefinition definition
                : accessService.filterViewable(definitions, currentViewer.resolve(request))) {
            result.add(ReportSummary.from(definition));
        }
        return result;
    }

    @GetMapping("/categories")
    public List<String> categories() {
        return definitionService.categories();
    }

    /**
     * 디자이너가 불러 쓰는 템플릿 원본.
     *
     * <p>여기에도 열람 검사를 거는 이유는 정의 안에 조회 SQL 이 들어 있기 때문이다.
     * 리포트를 못 보는 사람이 그 리포트가 어떤 표를 어떤 조건으로 읽는지는 알 수 있다면
     * 통제에 구멍이 남는다.</p>
     */
    @GetMapping("/{reportId}")
    public ReportTemplate template(@PathVariable String reportId, HttpServletRequest request) {
        requireView(reportId, request);
        return definitionService.loadEditableTemplate(reportId);
    }

    /** 조회 조건 입력 폼을 그리기 위한 메타 정보 */
    @GetMapping("/{reportId}/meta")
    public ReportMeta meta(@PathVariable String reportId, HttpServletRequest request) {
        requireView(reportId, request);
        return ReportMeta.from(definitionService.loadTemplate(reportId));
    }

    @PostMapping("/{reportId}")
    public SaveResult save(@PathVariable String reportId,
                           @RequestParam(required = false) String category,
                           @RequestBody ReportTemplate template,
                           HttpServletRequest request) {
        return SaveResult.from(definitionService.save(
                reportId, template, category, currentViewer.resolve(request)));
    }

    @DeleteMapping("/{reportId}")
    public DeleteResult delete(@PathVariable String reportId) {
        definitionService.delete(reportId);
        return DeleteResult.of(reportId);
    }

    /** 저장 전 정합성 검사. 디자이너가 저장 버튼을 누르기 전에 호출한다. */
    @PostMapping("/validate")
    public ValidationResult validate(@RequestBody ReportTemplate template) {
        return ValidationResult.of(TemplateValidator.collectErrors(template));
    }

    // ---------------------------------------------------------------- 실행

    /** 뷰어용 HTML 조각 */
    @PostMapping(value = "/{reportId}/render", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ReportRunService.ViewerContent render(@PathVariable String reportId,
                                                 @RequestBody(required = false) Map<String, Object> parameters,
                                                 HttpServletRequest request) {
        return runService.renderForViewer(reportId, parameters == null ? Map.of() : parameters, caller(request));
    }

    /**
     * 저장하지 않은 템플릿을 그대로 실행한다. 디자이너 미리보기 전용.
     *
     * <p>요청 본문은 {@code {"template": {...}, "parameters": {...}}} 형태다.</p>
     */
    @PostMapping(value = "/preview", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ReportRunService.ViewerContent preview(@RequestBody JsonNode body,
                                                  HttpServletRequest request) {
        ReportTemplate template = mapper.convertValue(body.path("template"), ReportTemplate.class);
        return runService.preview(template, readParameters(body.path("parameters")),
                currentViewer.resolve(request));
    }

    /** 파일 내려받기 */
    @GetMapping("/{reportId}/export")
    public void export(@PathVariable String reportId,
                       @RequestParam(defaultValue = "PDF") String format,
                       @RequestParam Map<String, String> query,
                       HttpServletRequest request,
                       HttpServletResponse response) throws IOException {
        ExportFormat exportFormat = ExportFormat.of(format);
        Map<String, String> parameters = new LinkedHashMap<>(query);
        parameters.remove("format");

        // 실패할 수 있는 일(조건 검증, 조회, 레이아웃)을 먼저 끝낸다.
        // 응답을 건드리기 전이라 여기서 터지면 예외 처리기가 제대로 된 오류 응답을 만든다.
        ReportRunService.ExportJob job =
                runService.prepare(reportId, parameters, exportFormat, caller(request));

        String fileName = job.fileName();
        response.setContentType(exportFormat.getContentType());
        response.setHeader("Content-Disposition",
                (exportFormat.isInline() ? "inline" : "attachment")
                        + "; filename=\"" + fileName.replaceAll("[^\\x20-\\x7e]", "_") + "\""
                        + "; filename*=UTF-8''" + ReportRunService.encodeFileName(fileName));

        OutputStream out = response.getOutputStream();
        runService.write(job, out);
        out.flush();
    }

    // ---------------------------------------------------------------- 디자이너 보조

    /**
     * SQL 의 결과 컬럼 목록을 돌려준다. 디자이너에서 필드 칩을 만들 때 쓴다.
     *
     * <p>{@code where 1 = 0} 을 덧대 한 행도 읽지 않는다. 컬럼 이름만 필요한데
     * 편집 중에 수십만 건을 긁어 오면 운영 DB 에 부담만 준다.</p>
     */
    @PostMapping(value = "/fields", consumes = MediaType.APPLICATION_JSON_VALUE)
    public FieldList fields(@RequestBody JsonNode body, HttpServletRequest request) {
        String sql = body.path("sql").asText("");
        SqlGuard.verifySelect(sql);

        // 저장을 거치지 않고 SQL 이 실행되는 자리라 여기서도 데이터소스 권한을 본다
        String target = body.path("dataSource").asText(DataSourceRegistry.MAIN);
        dataSources.requireWrite(target, currentViewer.resolve(request));

        DataSetDef probe = new DataSetDef();
        probe.setSourceType(DataSetDef.SourceType.SQL);
        probe.setDataSource(target);
        probe.setSql("select * from (" + stripTrailingSemicolon(sql) + ") kreport_probe where 1 = 0");
        probe.setMaxRows(1);

        DataTable table = jdbcDataSetProvider.fetch(probe, readParameters(body.path("parameters")));
        return new FieldList(table.getColumns());
    }

    /** 이 사람이 조회를 작성할 수 있는 데이터소스. 디자이너의 선택 목록에 쓴다. */
    @GetMapping("/datasources")
    public List<String> datasources(HttpServletRequest request) {
        return dataSources.writableBy(currentViewer.resolve(request));
    }

    /** 디자이너 함수 목록 */
    @GetMapping("/functions")
    public FunctionCatalog functions() {
        List<FunctionCatalog.FunctionInfo> infos = new ArrayList<>();
        for (ReportFunction function : FunctionRegistry.descriptors()) {
            infos.add(FunctionCatalog.FunctionInfo.from(function));
        }
        infos.sort(Comparator.comparing(FunctionCatalog.FunctionInfo::name));
        return new FunctionCatalog(infos, AGGREGATE_NAMES, VARIABLE_NAMES);
    }

    // ---------------------------------------------------------------- 공통

    /** 요청 본문에 실려 온 조회 조건 부분을 문자열 키 맵으로 읽는다. */
    private Map<String, Object> readParameters(JsonNode node) {
        Map<String, Object> parameters = mapper.convertValue(node, mapper.getTypeFactory()
                .constructMapType(HashMap.class, String.class, Object.class));
        return parameters == null ? Map.of() : parameters;
    }

    private String stripTrailingSemicolon(String sql) {
        String trimmed = sql.strip();
        return trimmed.endsWith(";") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private ReportRunService.Caller caller(HttpServletRequest request) {
        return new ReportRunService.Caller(currentViewer.resolve(request), clientIp(request));
    }

    private void requireView(String reportId, HttpServletRequest request) {
        accessService.requireView(definitionService.get(reportId), currentViewer.resolve(request));
    }

    private String currentUser(HttpServletRequest request) {
        return currentViewer.username(request);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // 프록시를 여러 단 거치면 콤마로 이어진다. 최초 클라이언트가 맨 앞.
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

}
