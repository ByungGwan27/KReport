package kr.co.kreport.license;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * 발급된 라이선스의 내용.
 *
 * <p>여기 담긴 값은 서명 대상이라 한 글자도 고칠 수 없다. 고치면 검증이 깨진다.</p>
 *
 * @param licensee    고객사 이름. 화면과 로그에 그대로 나간다.
 * @param licenseId   발급 번호. 문의가 왔을 때 어느 계약인지 찾는 열쇠다.
 * @param issuedOn    발급일
 * @param expiresOn   만료일. {@code null} 이면 무기한.
 * @param hosts       실행을 허용할 호스트명. 비어 있으면 어디서나 (개발·평가용).
 * @param maxReports  배포 가능한 리포트 수. 0 이하면 제한 없음.
 * @param features    켜 줄 기능 이름
 */
public record License(
        String licensee,
        String licenseId,
        LocalDate issuedOn,
        LocalDate expiresOn,
        List<String> hosts,
        int maxReports,
        Set<String> features) {

    /** 평가판임을 나타내는 기능 표시. 출력물에 워터마크가 찍힌다. */
    public static final String TRIAL = "TRIAL";

    public License {
        hosts = hosts == null ? List.of() : List.copyOf(hosts);
        features = features == null ? Set.of() : Set.copyOf(features);
    }

    public boolean isTrial() {
        return features.contains(TRIAL);
    }

    public boolean isExpired(LocalDate today) {
        return expiresOn != null && today.isAfter(expiresOn);
    }

    /**
     * 만료까지 남은 날. 무기한이면 {@code Long.MAX_VALUE}.
     *
     * <p>만료 당일까지는 쓸 수 있다. 계약 종료일 자정에 리포트가 멈추면
     * 마감일에 결재를 올리던 사람이 곤란해진다.</p>
     */
    public long daysLeft(LocalDate today) {
        return expiresOn == null
                ? Long.MAX_VALUE
                : java.time.temporal.ChronoUnit.DAYS.between(today, expiresOn);
    }

    public boolean allowsHost(String host) {
        return hosts.isEmpty() || hosts.stream().anyMatch(h -> h.equalsIgnoreCase(host));
    }
}
