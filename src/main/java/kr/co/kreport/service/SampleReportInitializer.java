package kr.co.kreport.service;

import kr.co.kreport.repository.ReportDefinitionRepository;
import kr.co.kreport.template.ReportTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 기동 시 {@code classpath:reports/*.json} 을 리포트 정의로 등록한다.
 *
 * <p>이미 같은 ID 로 등록된 정의가 있으면 건드리지 않는다. 운영 중에 화면에서 수정한 내용을
 * 재기동이 되돌려 버리면 곤란하기 때문이다.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "kreport.load-sample-reports", havingValue = "true", matchIfMissing = true)
public class SampleReportInitializer implements ApplicationRunner {

    /** 기동 시 샘플을 넣는 주체. 어느 데이터소스든 쓸 수 있어야 하므로 관리자로 둔다. */
    private static final kr.co.kreport.access.Viewer SYSTEM =
            kr.co.kreport.access.Viewer.of("system",
                    java.util.Set.of(kr.co.kreport.config.ReportRole.ADMIN));

    /** 파일명 앞부분으로 분류를 붙인다 */
    private static final Map<String, String> CATEGORY = Map.of(
            "BUDGET_EXEC", "재정",
            "CIVIL_STATUS", "민원",
            "CONTRACT_STATUS", "계약"
    );

    private final ReportDefinitionService definitionService;
    private final ReportDefinitionRepository repository;

    public SampleReportInitializer(ReportDefinitionService definitionService,
                                   ReportDefinitionRepository repository) {
        this.definitionService = definitionService;
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:reports/*.json");

            for (Resource resource : resources) {
                register(resource);
            }
        } catch (Exception e) {
            log.warn("샘플 리포트 등록 중 오류: {}", e.getMessage());
        }
    }

    private void register(Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            ReportTemplate template = definitionService.parse(json);

            String reportId = template.getReportId();
            if (reportId == null || reportId.isBlank()) {
                log.warn("reportId 가 없어 건너뜁니다: {}", resource.getFilename());
                return;
            }
            if (repository.existsByReportId(reportId)) {
                log.debug("이미 등록된 리포트입니다: {}", reportId);
                return;
            }
            definitionService.save(reportId, template, CATEGORY.getOrDefault(reportId, "기본"), SYSTEM);
            log.info("샘플 리포트 등록: {} ({})", reportId, template.getName());

        } catch (Exception e) {
            log.warn("샘플 리포트 등록 실패 ({}): {}", resource.getFilename(), e.getMessage());
        }
    }
}
