package kr.co.kreport.web;

import jakarta.servlet.http.HttpServletRequest;
import kr.co.kreport.access.CurrentViewer;
import kr.co.kreport.access.ReportAccessService;
import kr.co.kreport.domain.ReportAccessRule;
import kr.co.kreport.service.ReportDefinitionService;
import kr.co.kreport.web.dto.AccessModeRequest;
import kr.co.kreport.web.dto.AccessRuleView;
import kr.co.kreport.web.dto.AccessSettings;
import kr.co.kreport.web.dto.DeleteResult;
import kr.co.kreport.web.dto.GrantRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 리포트별 열람 권한 관리. 관리자 전용이다.
 *
 * <p>열람 범위를 정하는 일을 편집 권한자가 아니라 관리자에게 둔 이유는, 리포트를 만드는
 * 사람과 그 자료를 누구에게까지 보일지 정하는 사람이 같으면 통제가 성립하지 않기
 * 때문이다. 만든 사람이 스스로 열람 범위를 넓힐 수 있으면 승인 절차가 형식이 된다.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/reports/{reportId}/access")
public class ReportAccessController {

    private final ReportAccessService accessService;
    private final ReportDefinitionService definitionService;
    private final CurrentViewer currentViewer;

    public ReportAccessController(ReportAccessService accessService,
                                  ReportDefinitionService definitionService,
                                  CurrentViewer currentViewer) {
        this.accessService = accessService;
        this.definitionService = definitionService;
        this.currentViewer = currentViewer;
    }

    @GetMapping
    public AccessSettings settings(@PathVariable String reportId) {
        return new AccessSettings(
                reportId,
                definitionService.get(reportId).getAccessMode(),
                toViews(accessService.rulesOf(reportId)));
    }

    /** 통제 방식 전환. 규칙은 그대로 두므로 잠시 공개로 돌렸다가 되돌릴 수 있다. */
    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public AccessSettings changeMode(@PathVariable String reportId,
                                     @RequestBody AccessModeRequest request,
                                     HttpServletRequest httpRequest) {
        definitionService.changeAccessMode(reportId, request.mode(), currentViewer.username(httpRequest));
        return settings(reportId);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public AccessRuleView grant(@PathVariable String reportId,
                                @RequestBody GrantRequest request,
                                HttpServletRequest httpRequest) {
        // 없는 리포트에 규칙만 쌓이지 않도록 먼저 확인한다
        definitionService.get(reportId);
        return AccessRuleView.from(accessService.grant(
                reportId, request.grantType(), request.grantValue(), request.note(),
                currentViewer.username(httpRequest)));
    }

    @DeleteMapping("/{ruleId}")
    public DeleteResult revoke(@PathVariable String reportId,
                               @PathVariable Long ruleId,
                               HttpServletRequest httpRequest) {
        accessService.revoke(ruleId, currentViewer.username(httpRequest));
        return new DeleteResult(reportId, true);
    }

    private List<AccessRuleView> toViews(List<ReportAccessRule> rules) {
        List<AccessRuleView> views = new ArrayList<>(rules.size());
        for (ReportAccessRule rule : rules) {
            views.add(AccessRuleView.from(rule));
        }
        return views;
    }
}
