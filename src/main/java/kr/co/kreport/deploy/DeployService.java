package kr.co.kreport.deploy;

import kr.co.kreport.access.Viewer;
import kr.co.kreport.domain.ReportDefinition;
import kr.co.kreport.license.LicenseService;
import kr.co.kreport.service.ReportDefinitionService;
import kr.co.kreport.template.ReportTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 리포트를 배포용으로 굽고, 받은 패키지를 런타임에 올린다.
 *
 * <h2>납품 흐름</h2>
 * <ol>
 *   <li>공급사에서 디자이너로 리포트를 만든다</li>
 *   <li><b>배포용 내보내기</b> 로 {@code .krpt} 를 굽는다 (개인키가 있는 환경에서만)</li>
 *   <li>고객사 서버에 그 파일을 올린다</li>
 *   <li>런타임이 서명과 라이선스를 확인한 뒤 등록한다</li>
 * </ol>
 *
 * <p>고객사 서버에는 개인키가 없으므로 패키지를 새로 굽거나 고칠 수 없다. 고친 파일을
 * 올리면 서명이 깨져 3번에서 막힌다.</p>
 */
@Slf4j
@Service
public class DeployService {

    private final ReportDefinitionService definitionService;
    private final ReportPackager packager;
    private final LicenseService licenses;

    public DeployService(ReportDefinitionService definitionService,
                         ReportPackager packager,
                         LicenseService licenses) {
        this.definitionService = definitionService;
        this.packager = packager;
        this.licenses = licenses;
    }

    /** 등록된 리포트를 배포용 패키지로 굽는다 */
    @Transactional(readOnly = true)
    public Built build(String reportId, Viewer builder) {
        ReportDefinition definition = definitionService.get(reportId);
        ReportPackage content = new ReportPackage(
                ReportPackage.FORMAT_VERSION,
                definition.getReportId(),
                definition.getName(),
                definition.getTemplateJson(),
                LocalDateTime.now(),
                builder.username(),
                licenses.current() == null ? "UNLICENSED" : licenses.current().licenseId());

        return new Built(
                definition.getReportId() + ReportPackage.EXTENSION,
                packager.build(content));
    }

    /**
     * 받은 패키지를 런타임에 등록한다.
     *
     * <p>서명을 먼저 확인하고, 라이선스 한도를 본 다음 등록한다. 순서가 중요하다 —
     * 변조된 정의를 먼저 풀어 놓고 나중에 검사하면 그사이에 일이 벌어질 수 있다.</p>
     */
    @Transactional
    public ReportDefinition install(String packaged, Viewer installer) {
        ReportPackage content = packager.open(packaged);

        licenses.requireValid();
        if (!definitionService.exists(content.reportId())) {
            // 갱신 설치는 수를 늘리지 않으므로 새로 들어올 때만 한도를 본다
            licenses.requireCapacity(definitionService.count());
        }

        ReportTemplate template = definitionService.parse(content.templateJson());
        log.info("리포트 패키지 설치: {} (구운이 {} / {}, 설치 {})",
                content.reportId(), content.builtBy(), content.builtAt(), installer.username());

        return definitionService.installPackaged(content.reportId(), template, installer.username());
    }

    /** 굽기 결과 */
    public record Built(String fileName, String body) {
    }
}
