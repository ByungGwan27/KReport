package kr.co.kreport.access;

import kr.co.kreport.config.ReportRole;
import kr.co.kreport.domain.ReportAccessRule;
import kr.co.kreport.domain.ReportDefinition;
import kr.co.kreport.repository.ReportAccessRuleRepository;
import kr.co.kreport.support.ErrorCode;
import kr.co.kreport.support.KReportException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 리포트를 누가 볼 수 있는지 판단하고, 그 규칙을 관리한다.
 *
 * <h2>편집 권한자는 왜 통제 대상이 아닌가</h2>
 * <p>{@code DESIGNER} 는 리포트 정의에 조회 SQL 을 직접 쓸 수 있다. 어떤 리포트를 못 보게
 * 막아 봐야 같은 표를 읽는 리포트를 새로 만들면 그만이므로, 열람 규칙으로 막는 시늉을 하는
 * 것은 통제가 있다는 착각만 준다. 편집 권한 자체를 DB 열람 권한과 같은 무게로 주는 것이
 * 실제 방어선이고, 이 설계는 그 사실을 감추지 않는다.</p>
 *
 * <p>그래서 이 기능이 실제로 나누는 것은 <b>조회 권한자들 사이의 범위</b>다. 인사·감사
 * 자료를 전 직원이 아니라 해당 부서만 보게 하는 것이 목적이다.</p>
 */
@Slf4j
@Service
public class ReportAccessService {

    private final ReportAccessRuleRepository rules;

    public ReportAccessService(ReportAccessRuleRepository rules) {
        this.rules = rules;
    }

    // ---------------------------------------------------------------- 판단

    /**
     * 이 사람이 이 리포트를 볼 수 있는가.
     *
     * <p>공개 리포트, 관리자, 편집 권한자는 규칙을 보지 않는다. 그 밖에는 규칙 한 줄이라도
     * 걸려야 열린다. {@code RESTRICTED} 인데 규칙이 비어 있으면 아무도 보지 못하는데,
     * 이는 실수로 규칙을 지웠을 때 자료가 열리는 쪽보다 닫히는 쪽으로 틀리게 한 것이다.</p>
     */
    @Transactional(readOnly = true)
    public boolean canView(ReportDefinition definition, Viewer viewer) {
        if (!definition.getAccessMode().isRestricted() || isExempt(viewer)) {
            return true;
        }
        return matches(rules.findByReportIdOrderByGrantTypeAscGrantValueAsc(definition.getReportId()), viewer);
    }

    /** 볼 수 없으면 예외. 목록을 거치지 않고 주소를 직접 친 경우가 여기로 온다. */
    @Transactional(readOnly = true)
    public void requireView(ReportDefinition definition, Viewer viewer) {
        if (canView(definition, viewer)) {
            return;
        }
        // 누가 무엇을 보려다 막혔는지는 남긴다. 접근 통제는 막는 것만큼 기록이 중요하다.
        log.warn("열람 거부: user={} report={} departments={}",
                viewer.username(), definition.getReportId(), viewer.departments());
        throw new KReportException(ErrorCode.REPORT_FORBIDDEN,
                "이 리포트를 열람할 권한이 없습니다: " + definition.getReportId());
    }

    /**
     * 목록에서 볼 수 있는 것만 남긴다.
     *
     * <p>리포트마다 규칙을 따로 묻지 않고 한 번에 읽어 온다. 목록에 수십 건이 놓이는
     * 화면이라 건별 조회로 두면 질의가 그만큼 늘어난다.</p>
     */
    @Transactional(readOnly = true)
    public List<ReportDefinition> filterViewable(List<ReportDefinition> definitions, Viewer viewer) {
        if (isExempt(viewer)) {
            return definitions;
        }
        List<String> restricted = definitions.stream()
                .filter(d -> d.getAccessMode().isRestricted())
                .map(ReportDefinition::getReportId)
                .toList();
        if (restricted.isEmpty()) {
            return definitions;
        }

        Map<String, List<ReportAccessRule>> byReport = new HashMap<>();
        for (ReportAccessRule rule : rules.findByReportIdIn(restricted)) {
            byReport.computeIfAbsent(rule.getReportId(), k -> new ArrayList<>()).add(rule);
        }

        List<ReportDefinition> visible = new ArrayList<>(definitions.size());
        for (ReportDefinition definition : definitions) {
            if (!definition.getAccessMode().isRestricted()
                    || matches(byReport.getOrDefault(definition.getReportId(), List.of()), viewer)) {
                visible.add(definition);
            }
        }
        return visible;
    }

    /**
     * 규칙을 보지 않고 통과하는 사람.
     *
     * <p>관리자는 통제를 운영하는 주체라 전체를 봐야 하고, 편집 권한자는 위 설명대로
     * 막아도 우회할 수 있어 막는 시늉을 하지 않는다.</p>
     */
    private boolean isExempt(Viewer viewer) {
        return viewer.hasRole(ReportRole.ADMIN) || viewer.hasRole(ReportRole.DESIGNER);
    }

    private boolean matches(Collection<ReportAccessRule> reportRules, Viewer viewer) {
        for (ReportAccessRule rule : reportRules) {
            boolean hit = switch (rule.getGrantType()) {
                case USER -> rule.getGrantValue().equalsIgnoreCase(viewer.username());
                case DEPARTMENT -> viewer.departments().stream()
                        .anyMatch(d -> d.equalsIgnoreCase(rule.getGrantValue()));
                case ROLE -> viewer.roles().stream()
                        .anyMatch(r -> r.equalsIgnoreCase(rule.getGrantValue()));
            };
            if (hit) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- 관리

    @Transactional(readOnly = true)
    public List<ReportAccessRule> rulesOf(String reportId) {
        return rules.findByReportIdOrderByGrantTypeAscGrantValueAsc(reportId);
    }

    @Transactional
    public ReportAccessRule grant(String reportId, GrantType type, String value, String note, String actor) {
        String normalized = normalize(type, value);
        if (rules.existsByReportIdAndGrantTypeAndGrantValue(reportId, type, normalized)) {
            throw new KReportException(ErrorCode.ACCESS_RULE_DUPLICATE,
                    "이미 등록된 열람 규칙입니다: " + type + " " + normalized);
        }
        ReportAccessRule rule = new ReportAccessRule(reportId, type, normalized, actor);
        rule.setNote(note);
        log.info("열람 규칙 추가: report={} {}={} ({})", reportId, type, normalized, actor);
        return rules.save(rule);
    }

    @Transactional
    public void revoke(Long ruleId, String actor) {
        ReportAccessRule rule = rules.findById(ruleId)
                .orElseThrow(() -> new KReportException(ErrorCode.ACCESS_RULE_NOT_FOUND,
                        "열람 규칙을 찾을 수 없습니다: " + ruleId));
        rules.delete(rule);
        log.info("열람 규칙 삭제: report={} {}={} ({})",
                rule.getReportId(), rule.getGrantType(), rule.getGrantValue(), actor);
    }

    /** 리포트를 지울 때 함께 지운다. 남아 있으면 같은 아이디로 다시 만든 리포트에 옛 규칙이 붙는다. */
    @Transactional
    public void revokeAll(String reportId) {
        rules.deleteByReportId(reportId);
    }

    /** 부서 코드는 대소문자를 가리지 않는다. 아이디는 쓰인 그대로 둔다. */
    private String normalize(GrantType type, String value) {
        if (value == null || value.isBlank()) {
            throw new KReportException(ErrorCode.ACCESS_RULE_INVALID, "열람 대상이 비어 있습니다.");
        }
        String trimmed = value.trim();
        return type == GrantType.USER ? trimmed : trimmed.toUpperCase(java.util.Locale.ROOT);
    }
}
