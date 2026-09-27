package kr.co.kreport.web;

import kr.co.kreport.access.AccessMode;
import kr.co.kreport.access.GrantType;
import kr.co.kreport.access.ReportAccessService;
import kr.co.kreport.repository.ReportAccessRuleRepository;
import kr.co.kreport.service.ReportDefinitionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 열람 통제가 HTTP 경계에서 실제로 막는지.
 *
 * <p>판단 로직이 맞아도 그 판단을 부르지 않는 경로가 하나 남아 있으면 통제는 없는 것과
 * 같다. 목록·메타·정의·실행·내려받기를 모두 두드려 본다. 특히 <b>목록에서 가려지는 것과
 * 주소를 직접 치는 것은 다른 문제</b>라 따로 확인한다.</p>
 *
 * <p>소속 부서는 이 시험 안에서만 쓰는 설정으로 준다. 복지정책과(2100) 직원 {@code kim},
 * 교통행정과(3200) 직원 {@code lee} 다.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "kreport.security.departments.kim=2100",
        "kreport.security.departments.lee=3200"
})
class ReportAccessWebTest {

    private static final String REPORT_ID = "CIVIL_STATUS";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ReportAccessService accessService;

    @Autowired
    private ReportDefinitionService definitionService;

    @Autowired
    private ReportAccessRuleRepository rules;

    @BeforeEach
    void restrict() {
        rules.deleteAll();
        definitionService.changeAccessMode(REPORT_ID, AccessMode.RESTRICTED, "test");
        accessService.grant(REPORT_ID, GrantType.DEPARTMENT, "2100", "복지정책과", "test");
    }

    @AfterEach
    void release() {
        rules.deleteAll();
        definitionService.changeAccessMode(REPORT_ID, AccessMode.PUBLIC, "test");
    }

    // ---------------------------------------------------------------- 막혀야 하는 쪽

    @Test
    @WithMockUser(username = "lee", roles = "VIEWER")
    @DisplayName("다른 부서 직원에게는 목록에서 보이지 않는다")
    void hiddenFromListForOtherDepartment() throws Exception {
        mvc.perform(get("/api/reports"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(REPORT_ID))));
    }

    @Test
    @WithMockUser(username = "lee", roles = "VIEWER")
    @DisplayName("목록에 없어도 주소를 직접 치면 실행될 수 있다 - 그 경로도 막는다")
    void directRunIsBlocked() throws Exception {
        mvc.perform(post("/api/reports/" + REPORT_ID + "/render")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "lee", roles = "VIEWER")
    @DisplayName("파일 내려받기도 막는다")
    void exportIsBlocked() throws Exception {
        mvc.perform(get("/api/reports/" + REPORT_ID + "/export").param("format", "PDF"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "lee", roles = "VIEWER")
    @DisplayName("정의 원본도 막는다 - 안에 조회 SQL 이 들어 있다")
    void templateIsBlocked() throws Exception {
        mvc.perform(get("/api/reports/" + REPORT_ID))
                .andExpect(status().isForbidden())
                .andExpect(content().string(not(containsString("select"))));
    }

    @Test
    @WithMockUser(username = "lee", roles = "VIEWER")
    @DisplayName("조회 조건 메타도 막는다")
    void metaIsBlocked() throws Exception {
        mvc.perform(get("/api/reports/" + REPORT_ID + "/meta"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "lee", roles = "VIEWER")
    @DisplayName("뷰어 화면은 JSON 이 아니라 오류 페이지로 끊는다")
    void viewerPageIsForbidden() throws Exception {
        mvc.perform(get("/viewer/" + REPORT_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "lee", roles = "VIEWER")
    @DisplayName("조회 권한자는 열람 규칙을 고칠 수 없다")
    void viewerCannotGrantToSelf() throws Exception {
        mvc.perform(post("/api/reports/" + REPORT_ID + "/access")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"grantType\":\"USER\",\"grantValue\":\"lee\"}"))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- 열려야 하는 쪽

    @Test
    @WithMockUser(username = "kim", roles = "VIEWER")
    @DisplayName("담당 부서 직원에게는 목록에 보인다")
    void visibleToGrantedDepartment() throws Exception {
        mvc.perform(get("/api/reports"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(REPORT_ID)));
    }

    @Test
    @WithMockUser(username = "kim", roles = "VIEWER")
    @DisplayName("담당 부서 직원은 실행할 수 있다")
    void grantedDepartmentCanRun() throws Exception {
        mvc.perform(post("/api/reports/" + REPORT_ID + "/render")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"VIEWER", "DESIGNER", "ADMIN"})
    @DisplayName("관리자는 통제와 무관하게 보고, 규칙도 고친다")
    void adminSeesEverythingAndManagesRules() throws Exception {
        mvc.perform(get("/api/reports"))
                .andExpect(content().string(containsString(REPORT_ID)));

        mvc.perform(get("/api/reports/" + REPORT_ID + "/access"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("2100")));
    }

    @Test
    @WithMockUser(username = "kim", roles = "VIEWER")
    @DisplayName("공개 리포트는 부서와 무관하게 보인다")
    void publicReportStaysOpen() throws Exception {
        mvc.perform(get("/api/reports"))
                .andExpect(content().string(containsString("BUDGET_EXEC")));
    }
}
