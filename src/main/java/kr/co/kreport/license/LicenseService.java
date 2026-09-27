package kr.co.kreport.license;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import kr.co.kreport.config.KReportProperties;
import kr.co.kreport.support.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

/**
 * 라이선스를 읽고, 지금 이 서버에서 써도 되는지 판단한다.
 *
 * <h2>언제 확인하는가</h2>
 * <p>기동할 때 한 번 읽고, 그 뒤로는 <b>리포트를 실행할 때마다</b> 만료만 다시 본다.
 * 서버를 한번 띄우면 몇 달씩 돌아가는 곳이 많아서, 기동 시점에만 보면 계약이 끝난 뒤로도
 * 재기동 전까지 계속 동작한다. 반대로 매번 서명까지 다시 검증하면 렌더링마다 비용이 붙으므로,
 * 무거운 검증은 기동 때 한 번만 한다.</p>
 *
 * <h2>없으면 어떻게 되는가</h2>
 * <p>라이선스 파일이 없으면 <b>기동은 하되 평가판으로 돕니다.</b> 출력물에 워터마크가 찍히고
 * 기동 로그에 안내가 남는다. 기동 자체를 막지 않는 이유는, 설치 직후 담당자가 화면을 한 번
 * 열어 보고 나서 정식 키를 요청하는 순서가 현장에서 보통이기 때문이다. 운영에서 평가판을
 * 막으려면 {@code kreport.license.require=true} 로 둔다.</p>
 */
@Slf4j
@Service
public class LicenseService {

    private final KReportProperties.LicenseConfig config;
    private final ObjectMapper mapper;

    private License license;
    private boolean trial;

    public LicenseService(KReportProperties properties, ObjectMapper templateObjectMapper) {
        this.config = properties.getLicense();
        this.mapper = templateObjectMapper;
    }

    @PostConstruct
    void load() {
        String raw = read();
        if (raw == null) {
            if (config.isRequire()) {
                throw new LicenseException(ErrorCode.LICENSE_MISSING,
                        "라이선스 파일이 없습니다. kreport.license.path 를 확인하세요.");
            }
            this.license = trialLicense();
            this.trial = true;
            log.warn("라이선스가 없어 평가판으로 실행합니다. 출력물에 워터마크가 찍힙니다. "
                    + "정식 키는 공급사에 요청하세요.");
            return;
        }

        this.license = verify(raw);
        this.trial = license.isTrial();

        String host = hostName();
        if (!license.allowsHost(host)) {
            throw new LicenseException(ErrorCode.LICENSE_HOST_MISMATCH,
                    "이 서버에서 쓸 수 없는 라이선스입니다. 현재 호스트: " + host
                            + ", 허용: " + license.hosts());
        }
        if (license.isExpired(LocalDate.now())) {
            throw new LicenseException(ErrorCode.LICENSE_EXPIRED,
                    "라이선스가 만료되었습니다: " + license.expiresOn());
        }

        long left = license.daysLeft(LocalDate.now());
        log.info("라이선스 확인: {} ({}), 만료 {}", license.licensee(), license.licenseId(),
                license.expiresOn() == null ? "없음" : license.expiresOn());
        if (left <= 30) {
            // 만료 한 달 전부터 매 기동 시 알린다. 갱신 협의에 시간이 걸린다.
            log.warn("라이선스 만료가 {}일 남았습니다. 갱신을 요청하세요.", left);
        }
    }

    /** 서명을 확인하고 내용을 꺼낸다 */
    private License verify(String raw) {
        byte[] payload = SignedEnvelope.open(raw, SignedEnvelope.publicKey(config.getPublicKey()));
        try {
            return mapper.readValue(payload, License.class);
        } catch (IOException e) {
            throw new LicenseException("라이선스 내용을 해석하지 못했습니다.");
        }
    }

    private String read() {
        if (config.getKey() != null && !config.getKey().isBlank()) {
            return config.getKey();
        }
        if (config.getPath() == null || config.getPath().isBlank()) {
            return null;
        }
        Path path = Path.of(config.getPath());
        if (!Files.isReadable(path)) {
            return null;
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new LicenseException("라이선스 파일을 읽지 못했습니다: " + path);
        }
    }

    // ---------------------------------------------------------------- 실행 시점 확인

    /**
     * 리포트를 실행해도 되는지. 실행 경로에서 매번 부른다.
     *
     * <p>기동 때 이미 서명을 확인했으므로 여기서는 날짜만 본다. 며칠씩 켜 둔 서버가
     * 계약 종료 뒤에도 계속 도는 것을 막는 것이 목적이다.</p>
     */
    public void requireValid() {
        if (license == null) {
            throw new LicenseException(ErrorCode.LICENSE_MISSING, "라이선스가 확인되지 않았습니다.");
        }
        if (license.isExpired(LocalDate.now())) {
            throw new LicenseException(ErrorCode.LICENSE_EXPIRED,
                    "라이선스가 만료되었습니다 (" + license.expiresOn() + "). 공급사에 갱신을 요청하세요.");
        }
    }

    /** 배포 가능한 리포트 수 제한 */
    public void requireCapacity(int currentCount) {
        if (license.maxReports() > 0 && currentCount >= license.maxReports()) {
            throw new LicenseException(ErrorCode.LICENSE_LIMIT,
                    "라이선스로 배포할 수 있는 리포트 수를 넘었습니다 ("
                            + license.maxReports() + "건). 상위 라이선스가 필요합니다.");
        }
    }

    public boolean hasFeature(String feature) {
        return license != null && license.features().contains(feature);
    }

    public License current() {
        return license;
    }

    public boolean isTrial() {
        return trial;
    }

    /** 평가판 출력물에 찍을 문구. 정식이면 {@code null}. */
    public String watermark() {
        return trial ? "평가판 - KReport" : null;
    }

    private License trialLicense() {
        return new License("(평가판)", "TRIAL", LocalDate.now(), null,
                java.util.List.of(), 0, java.util.Set.of(License.TRIAL));
    }

    private String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }
}
