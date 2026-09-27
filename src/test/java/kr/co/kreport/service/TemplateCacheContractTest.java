package kr.co.kreport.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.kreport.access.Viewer;
import kr.co.kreport.config.ReportRole;
import kr.co.kreport.export.ExportFormat;
import kr.co.kreport.template.ReportTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayOutputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 캐시에 올라간 템플릿이 실행 때문에 변하지 않는다는 규약을 지킨다.
 *
 * <p>{@code loadTemplate()} 은 속도 때문에 캐시 인스턴스를 그대로 내준다. 그 대신
 * 실행 경로가 템플릿을 절대 고치지 않아야 하는데, 이건 컴파일러가 잡아 주지 못하는
 * 약속이라 여기서 못을 박는다. 누군가 레이아웃 코드에서 밴드 목록에 무언가를 끼워
 * 넣으면 이 테스트가 먼저 깨진다. 그러지 않으면 한 사람이 리포트를 뽑은 뒤부터
 * 나머지 사람의 출력물이 조용히 달라진다.</p>
 */
@SpringBootTest
class TemplateCacheContractTest {

    @Autowired
    private ReportDefinitionService definitionService;

    @Autowired
    private ReportRunService runService;

    @Autowired
    private ObjectMapper templateObjectMapper;

    private static final String REPORT_ID = "BUDGET_EXEC";

    private static final ReportRunService.Caller CALLER = new ReportRunService.Caller(
            Viewer.of("tester", java.util.Set.of(ReportRole.VIEWER)), "127.0.0.1");

    @Test
    @DisplayName("리포트를 실행해도 캐시된 템플릿은 그대로다")
    void executionDoesNotMutateCachedTemplate() throws Exception {
        String before = snapshot();

        runService.renderForViewer(REPORT_ID, Map.of(), CALLER);

        ReportRunService.ExportJob job =
                runService.prepare(REPORT_ID, Map.of(), ExportFormat.PDF, CALLER);
        runService.write(job, new ByteArrayOutputStream());

        assertThat(snapshot())
                .as("실행 경로가 캐시된 템플릿을 고쳤습니다. 템플릿은 읽기만 해야 합니다.")
                .isEqualTo(before);
    }

    @Test
    @DisplayName("같은 리포트를 두 번 읽으면 같은 인스턴스가 온다 - 캐시가 실제로 걸린다")
    void loadTemplateReusesCachedInstance() {
        assertThat(definitionService.loadTemplate(REPORT_ID))
                .isSameAs(definitionService.loadTemplate(REPORT_ID));
    }

    @Test
    @DisplayName("편집용 사본을 고쳐도 캐시는 흔들리지 않는다")
    void editableCopyIsDetached() {
        ReportTemplate copy = definitionService.loadEditableTemplate(REPORT_ID);
        assertThat(copy).isNotSameAs(definitionService.loadTemplate(REPORT_ID));

        String before = snapshot();
        copy.setName("엉뚱한 이름");
        copy.getBands().clear();

        assertThat(snapshot()).isEqualTo(before);
        assertThat(definitionService.loadTemplate(REPORT_ID).getBands()).isNotEmpty();
    }

    /** 캐시에 올라 있는 템플릿의 현재 모습 */
    private String snapshot() {
        try {
            return templateObjectMapper.writeValueAsString(definitionService.loadTemplate(REPORT_ID));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
