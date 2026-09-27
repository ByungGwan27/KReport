package kr.co.kreport.access;

import kr.co.kreport.config.ReportRole;
import kr.co.kreport.domain.ReportDefinition;
import kr.co.kreport.repository.ReportAccessRuleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 리포트별 열람 범위 판단.
 *
 * <p>이 묶음이 지키는 것은 하나다 — <b>볼 권한이 없는 사람에게는 자료도, 자료가 있다는
 * 사실도 넘어가지 않는다.</b> 목록에서 가리는 것과 실행을 막는 것은 별개라, 둘 다 확인한다.</p>
 */
@SpringBootTest
@Transactional
class ReportAccessServiceTest {

    @Autowired
    private ReportAccessService accessService;

    @Autowired
    private ReportAccessRuleRepository rules;

    private static final String REPORT_ID = "ACL_TEST";

    private ReportDefinition restricted;
    private ReportDefinition open;

    /** 복지정책과 직원 */
    private final Viewer welfare = new Viewer("kim", Set.of("2100"), Set.of(ReportRole.VIEWER));
    /** 교통행정과 직원 */
    private final Viewer traffic = new Viewer("lee", Set.of("3200"), Set.of(ReportRole.VIEWER));
    /** 부서를 모르는 사람 */
    private final Viewer unknown = Viewer.of("park", Set.of(ReportRole.VIEWER));

    @BeforeEach
    void setUp() {
        rules.deleteAll();
        restricted = definition(REPORT_ID, AccessMode.RESTRICTED);
        open = definition("OPEN_TEST", AccessMode.PUBLIC);
    }

    private ReportDefinition definition(String reportId, AccessMode mode) {
        ReportDefinition d = new ReportDefinition();
        d.setReportId(reportId);
        d.setName(reportId);
        d.setTemplateJson("{}");
        d.setAccessMode(mode);
        return d;
    }

    @Test
    @DisplayName("통제 중인데 규칙이 없으면 아무도 못 본다 - 실수로 규칙을 지워도 열리지 않는다")
    void restrictedWithNoRuleIsClosed() {
        assertThat(accessService.canView(restricted, welfare)).isFalse();
        assertThat(accessService.canView(restricted, unknown)).isFalse();
    }

    @Test
    @DisplayName("부서 규칙에 걸린 사람만 본다")
    void departmentRuleOpensForThatDepartmentOnly() {
        accessService.grant(REPORT_ID, GrantType.DEPARTMENT, "2100", "복지정책과 담당", "admin");

        assertThat(accessService.canView(restricted, welfare)).isTrue();
        assertThat(accessService.canView(restricted, traffic)).isFalse();
        assertThat(accessService.canView(restricted, unknown)).isFalse();
    }

    @Test
    @DisplayName("부서로 묶이지 않는 예외는 사용자 규칙으로 한 명씩 연다")
    void userRuleOpensForOnePerson() {
        accessService.grant(REPORT_ID, GrantType.USER, "park", "감사 담당", "admin");

        assertThat(accessService.canView(restricted, unknown)).isTrue();
        assertThat(accessService.canView(restricted, welfare)).isFalse();
    }

    @Test
    @DisplayName("겸직이면 어느 한쪽 부서만 걸려도 열린다")
    void anyOfSeveralDepartmentsIsEnough() {
        accessService.grant(REPORT_ID, GrantType.DEPARTMENT, "3200", null, "admin");
        Viewer bothDesks = new Viewer("choi", Set.of("2100", "3200"), Set.of(ReportRole.VIEWER));

        assertThat(accessService.canView(restricted, bothDesks)).isTrue();
    }

    @Test
    @DisplayName("공개 리포트는 규칙을 보지 않는다")
    void publicReportIgnoresRules() {
        assertThat(accessService.canView(open, traffic)).isTrue();
    }

    @Test
    @DisplayName("관리자와 편집 권한자는 통제를 받지 않는다")
    void adminAndDesignerAreExempt() {
        Viewer admin = Viewer.of("admin", Set.of(ReportRole.VIEWER, ReportRole.ADMIN));
        Viewer designer = Viewer.of("designer", Set.of(ReportRole.VIEWER, ReportRole.DESIGNER));

        // 편집 권한자는 같은 표를 읽는 리포트를 새로 만들 수 있어 막아도 우회된다.
        // 막는 시늉을 하지 않는 편이 통제 범위를 정확히 보여 준다.
        assertThat(accessService.canView(restricted, admin)).isTrue();
        assertThat(accessService.canView(restricted, designer)).isTrue();
    }

    @Test
    @DisplayName("목록에서는 볼 수 있는 것만 남는다")
    void listIsFiltered() {
        accessService.grant(REPORT_ID, GrantType.DEPARTMENT, "2100", null, "admin");
        List<ReportDefinition> all = List.of(restricted, open);

        assertThat(accessService.filterViewable(all, welfare))
                .extracting(ReportDefinition::getReportId)
                .containsExactly(REPORT_ID, "OPEN_TEST");
        assertThat(accessService.filterViewable(all, traffic))
                .extracting(ReportDefinition::getReportId)
                .containsExactly("OPEN_TEST");
    }

    @Test
    @DisplayName("막힌 사람이 실행을 시도하면 403 으로 끊는다")
    void requireViewThrowsForOutsider() {
        accessService.grant(REPORT_ID, GrantType.DEPARTMENT, "2100", null, "admin");

        assertThatThrownBy(() -> accessService.requireView(restricted, traffic))
                .hasMessageContaining(REPORT_ID);

        // 걸린 사람은 조용히 통과한다
        accessService.requireView(restricted, welfare);
    }

    @Test
    @DisplayName("부서 코드는 대소문자를 가리지 않는다")
    void departmentCodeIsCaseInsensitive() {
        accessService.grant(REPORT_ID, GrantType.DEPARTMENT, "dept-a", null, "admin");
        Viewer member = new Viewer("hong", Set.of("DEPT-A"), Set.of(ReportRole.VIEWER));

        assertThat(accessService.canView(restricted, member)).isTrue();
    }

    @Test
    @DisplayName("같은 규칙을 두 번 넣으면 거부한다")
    void duplicateGrantIsRejected() {
        accessService.grant(REPORT_ID, GrantType.DEPARTMENT, "2100", null, "admin");

        assertThatThrownBy(() -> accessService.grant(REPORT_ID, GrantType.DEPARTMENT, "2100", null, "admin"))
                .hasMessageContaining("이미 등록된");
    }

    @Test
    @DisplayName("규칙을 거두면 다시 막힌다")
    void revokeClosesAgain() {
        Long id = accessService.grant(REPORT_ID, GrantType.DEPARTMENT, "2100", null, "admin").getId();
        assertThat(accessService.canView(restricted, welfare)).isTrue();

        accessService.revoke(id, "admin");

        assertThat(accessService.canView(restricted, welfare)).isFalse();
    }
}
