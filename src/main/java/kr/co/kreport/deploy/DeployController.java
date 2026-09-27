package kr.co.kreport.deploy;

import jakarta.servlet.http.HttpServletRequest;
import kr.co.kreport.access.CurrentViewer;
import kr.co.kreport.license.License;
import kr.co.kreport.license.LicenseService;
import kr.co.kreport.web.dto.SaveResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 배포 패키지를 굽고 설치한다. 관리자 전용이다.
 */
@Slf4j
@RestController
@RequestMapping("/api/deploy")
public class DeployController {

    private final DeployService deployService;
    private final ReportPackager packager;
    private final LicenseService licenses;
    private final CurrentViewer currentViewer;

    public DeployController(DeployService deployService,
                            ReportPackager packager,
                            LicenseService licenses,
                            CurrentViewer currentViewer) {
        this.deployService = deployService;
        this.packager = packager;
        this.licenses = licenses;
        this.currentViewer = currentViewer;
    }

    /** 지금 이 서버의 라이선스 상태 */
    @GetMapping("/license")
    public Map<String, Object> license() {
        License current = licenses.current();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("licensee", current.licensee());
        out.put("licenseId", current.licenseId());
        out.put("issuedOn", current.issuedOn());
        out.put("expiresOn", current.expiresOn());
        out.put("trial", licenses.isTrial());
        out.put("maxReports", current.maxReports());
        out.put("features", current.features());
        long left = current.daysLeft(LocalDate.now());
        out.put("daysLeft", left == Long.MAX_VALUE ? null : left);
        // 이 서버에서 패키지를 구울 수 있는지. 고객사 런타임에서는 false 다.
        out.put("canBuild", packager.canBuild());
        return out;
    }

    /** 리포트를 배포용 .krpt 로 내려받는다 */
    @GetMapping("/{reportId}/package")
    public ResponseEntity<Resource> build(@PathVariable String reportId, HttpServletRequest request) {
        DeployService.Built built = deployService.build(reportId, currentViewer.resolve(request));
        byte[] body = built.body().getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + built.fileName() + "\"")
                .contentLength(body.length)
                .body(new ByteArrayResource(body));
    }

    /** 받은 .krpt 를 올려 설치한다 */
    @PostMapping("/install")
    public SaveResult install(@RequestParam("file") MultipartFile file, HttpServletRequest request)
            throws IOException {
        String body = new String(file.getBytes(), StandardCharsets.UTF_8);
        return SaveResult.from(deployService.install(body, currentViewer.resolve(request)));
    }

    /** 파일 대신 본문을 그대로 붙여 넣어 설치할 때 (배포 자동화용) */
    @PostMapping(value = "/install-text", consumes = MediaType.TEXT_PLAIN_VALUE)
    public SaveResult installText(@RequestBody String body, HttpServletRequest request) {
        return SaveResult.from(deployService.install(body, currentViewer.resolve(request)));
    }
}
