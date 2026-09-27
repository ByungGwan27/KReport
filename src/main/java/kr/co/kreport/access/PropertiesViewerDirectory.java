package kr.co.kreport.access;

import kr.co.kreport.config.KReportProperties;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 설정 파일에 적어 둔 소속을 읽는 기본 디렉터리.
 *
 * <pre>
 * kreport.security.departments:
 *   viewer: "1100"
 *   kim: "1100,2100"   # 겸직은 쉼표로
 * </pre>
 *
 * <p>사람이 수십 명을 넘어가면 설정 파일로 감당할 수 없다. 그때는 인사 시스템이나 SSO 를
 * 읽는 {@link ViewerDirectory} 를 빈으로 올리면 이 구현은 물러난다. 운영에서 이 구현이
 * 쓰이고 있으면 기동할 때 한 줄 남긴다 — 부서 규칙이 설정 파일에 묶여 있다는 사실이
 * 아무도 모르게 넘어가지 않도록.</p>
 */
@Slf4j
public class PropertiesViewerDirectory implements ViewerDirectory {

    private final Map<String, String> departments;

    public PropertiesViewerDirectory(KReportProperties properties) {
        this.departments = properties.getSecurity().getDepartments();
        log.info("소속 부서를 설정 파일에서 읽습니다 ({}명). 기관 인사 연계를 붙이려면 "
                + "ViewerDirectory 구현을 빈으로 등록하세요.", departments.size());
    }

    @Override
    public Viewer lookup(String username, Set<String> roles) {
        String configured = departments.get(username);
        if (configured == null || configured.isBlank()) {
            return Viewer.of(username, roles);
        }
        Set<String> codes = new LinkedHashSet<>();
        for (String code : configured.split(",")) {
            String trimmed = code.trim();
            if (!trimmed.isEmpty()) {
                codes.add(trimmed.toUpperCase(Locale.ROOT));
            }
        }
        return new Viewer(username, codes, roles);
    }

    /** 설정에 적힌 부서 코드 전체. 관리 화면에서 고를 수 있게 내보낸다. */
    public Set<String> knownDepartments() {
        Set<String> all = new LinkedHashSet<>();
        for (String value : departments.values()) {
            Arrays.stream(value.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> s.toUpperCase(Locale.ROOT))
                    .forEach(all::add);
        }
        return all;
    }
}
