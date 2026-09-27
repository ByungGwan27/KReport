package kr.co.kreport.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 접근 통제 검증.
 *
 * <p>리포트 정의를 저장하는 경로가 이 묶음의 핵심이다. 그 경로가 열려 있으면 조회 SQL 을
 * 마음대로 쓸 수 있고, 그것은 업무 DB 를 통째로 읽을 수 있다는 뜻이다. 권한 규칙이
 * 바뀌었을 때 그 사실이 조용히 넘어가지 않도록 못을 박아 둔다.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityAccessTest {

    /** 남의 테이블을 읽는 정의. 편집 권한이 곧 DB 열람 권한임을 보여 준다. */
    private static final String HOSTILE_TEMPLATE = """
            {
              "reportId":"SEC_TEST","name":"probe",
              "page":{"paperSize":"A4","orientation":"PORTRAIT",
                      "marginTop":36,"marginRight":28,"marginBottom":36,"marginLeft":28},
              "dataSet":{"sourceType":"SQL",
                         "sql":"select applicant_name from civil_complaint"},
              "parameters":[],"groups":[],
              "bands":[{"type":"DETAIL","height":16,"elements":[
                {"id":"a","type":"TEXT","expression":"{APPLICANT_NAME}",
                 "x":0,"y":0,"width":120,"height":14}]}]
            }
            """;

    @Autowired
    private MockMvc mvc;

    // ---------------------------------------------------------------- 익명 차단

    @Test
    @WithAnonymousUser
    @DisplayName("로그인하지 않으면 화면에 들어갈 수 없다")
    void anonymousIsRedirectedToLogin() throws Exception {
        mvc.perform(get("/")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/viewer/BUDGET_EXEC")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/designer")).andExpect(status().is3xxRedirection());
    }

    @Test
    @WithAnonymousUser
    @DisplayName("로그인하지 않으면 데이터를 내려받을 수 없다")
    void anonymousCannotExport() throws Exception {
        mvc.perform(get("/api/reports/BUDGET_EXEC/export")
                        .param("format", "CSV")
                        .param("fiscalYear", "2026")
                        .param("deptCode", "%"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithAnonymousUser
    @DisplayName("로그인하지 않으면 리포트 정의를 등록할 수 없다")
    void anonymousCannotSaveDefinition() throws Exception {
        mvc.perform(post("/api/reports/SEC_TEST").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(HOSTILE_TEMPLATE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CSRF 토큰이 없는 쓰기 요청은 거부된다")
    @WithMockUser(username = "d", roles = {"VIEWER", "DESIGNER"})
    void writeWithoutCsrfIsRejected() throws Exception {
        mvc.perform(post("/api/reports/SEC_TEST")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(HOSTILE_TEMPLATE))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- 권한 분리

    @Test
    @WithMockUser(username = "v", roles = "VIEWER")
    @DisplayName("조회 권한만으로는 리포트 정의를 고칠 수 없다")
    void viewerCannotEditDefinition() throws Exception {
        mvc.perform(post("/api/reports/SEC_TEST").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(HOSTILE_TEMPLATE))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "v", roles = "VIEWER")
    @DisplayName("조회 권한만으로는 임의 SQL 의 컬럼을 들여다볼 수 없다")
    void viewerCannotProbeSchema() throws Exception {
        mvc.perform(post("/api/reports/fields").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sql\":\"select * from civil_complaint\",\"parameters\":{}}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "v", roles = "VIEWER")
    @DisplayName("조회 권한만으로는 미저장 템플릿을 실행할 수 없다")
    void viewerCannotPreviewArbitraryTemplate() throws Exception {
        mvc.perform(post("/api/reports/preview").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"template\":" + HOSTILE_TEMPLATE + ",\"parameters\":{}}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "v", roles = "VIEWER")
    @DisplayName("조회 권한만으로는 디자이너 화면에 들어갈 수 없다")
    void viewerCannotOpenDesigner() throws Exception {
        mvc.perform(get("/designer")).andExpect(status().isForbidden());
        mvc.perform(get("/designer/BUDGET_EXEC")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "d", roles = {"VIEWER", "DESIGNER"})
    @DisplayName("편집 권한만으로는 정의를 지우거나 이력을 볼 수 없다")
    void designerCannotDeleteOrAudit() throws Exception {
        mvc.perform(delete("/api/reports/BUDGET_EXEC").with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/logs")).andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- 허용 경로

    @Test
    @WithMockUser(username = "v", roles = "VIEWER")
    @DisplayName("조회 권한이면 리포트를 보고 내려받을 수 있다")
    void viewerCanReadAndExport() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/viewer/BUDGET_EXEC")).andExpect(status().isOk());
        mvc.perform(get("/api/reports/BUDGET_EXEC/export")
                        .param("format", "CSV")
                        .param("fiscalYear", "2026")
                        .param("deptCode", "%"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "a", roles = {"VIEWER", "DESIGNER", "ADMIN"})
    @DisplayName("관리자는 이력을 볼 수 있다")
    void adminCanAudit() throws Exception {
        mvc.perform(get("/logs")).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- 정보 노출

    @Test
    @WithMockUser(username = "v", roles = "VIEWER")
    @DisplayName("조회 실패 메시지에 테이블 이름이 섞여 나오지 않는다")
    void sqlErrorsDoNotLeakSchema() throws Exception {
        // 없는 파라미터를 넣어 조회를 실패시킨다
        mvc.perform(get("/api/reports/BUDGET_EXEC/export")
                        .param("format", "CSV")
                        .param("fiscalYear", "숫자아님")
                        .param("deptCode", "%"))
                .andExpect(content().string(not(containsString("budget_execution"))));
    }
}
