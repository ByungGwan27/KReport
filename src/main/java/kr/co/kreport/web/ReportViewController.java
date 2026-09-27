package kr.co.kreport.web;

import jakarta.servlet.http.HttpServletRequest;
import kr.co.kreport.access.CurrentViewer;
import kr.co.kreport.access.ReportAccessService;
import kr.co.kreport.access.Viewer;
import kr.co.kreport.service.ExecutionLogService;
import kr.co.kreport.service.ReportDefinitionService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 화면 라우팅. 데이터는 화면에서 API 를 호출해 채운다.
 */
@Controller
public class ReportViewController {

    private final ReportDefinitionService definitionService;
    private final ExecutionLogService executionLogService;
    private final ReportAccessService accessService;
    private final CurrentViewer currentViewer;

    public ReportViewController(ReportDefinitionService definitionService,
                                ExecutionLogService executionLogService,
                                ReportAccessService accessService,
                                CurrentViewer currentViewer) {
        this.definitionService = definitionService;
        this.executionLogService = executionLogService;
        this.accessService = accessService;
        this.currentViewer = currentViewer;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/")
    public String index(@RequestParam(required = false) String keyword, Model model,
                        HttpServletRequest request) {
        model.addAttribute("reports", accessService.filterViewable(
                keyword == null || keyword.isBlank()
                        ? definitionService.findAll()
                        : definitionService.search(keyword),
                currentViewer.resolve(request)));
        model.addAttribute("keyword", keyword);
        return "report/list";
    }

    @GetMapping("/viewer/{reportId}")
    public String viewer(@PathVariable String reportId, Model model, HttpServletRequest request) {
        Viewer viewer = currentViewer.resolve(request);
        accessService.requireView(definitionService.get(reportId), viewer);
        model.addAttribute("definition", definitionService.get(reportId));
        model.addAttribute("template", definitionService.loadEditableTemplate(reportId));
        return "report/viewer";
    }

    @GetMapping("/designer")
    public String newDesigner(Model model) {
        model.addAttribute("reportId", "");
        return "report/designer";
    }

    @GetMapping("/designer/{reportId}")
    public String designer(@PathVariable String reportId, Model model) {
        model.addAttribute("reportId", definitionService.get(reportId).getReportId());
        return "report/designer";
    }

    /** 열람 권한 관리 화면. 접근 자체는 SecurityConfig 가 관리자에게만 연다. */
    @GetMapping("/access/{reportId}")
    public String access(@PathVariable String reportId, Model model) {
        model.addAttribute("definition", definitionService.get(reportId));
        return "report/access";
    }

    @GetMapping("/logs")
    public String logs(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("logs", executionLogService.recent(PageRequest.of(page, 50)));
        model.addAttribute("page", page);
        return "report/logs";
    }
}
