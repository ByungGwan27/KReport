package kr.co.kreport.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code kreport.*} 설정.
 */
@Data
@Component
@ConfigurationProperties(prefix = "kreport")
public class KReportProperties {

    /** 데이터셋 조회 제한시간(초) */
    private int queryTimeoutSeconds = 30;

    /** 기동 시 classpath:reports/*.json 을 등록할지 여부 */
    private boolean loadSampleReports = true;

    private Security security = new Security();

    private Image image = new Image();

    private Font font = new Font();

    /** 고객사 화면에 끼워 넣기 */
    private Embed embed = new Embed();

    /**
     * 임베드 설정.
     *
     * <p>고객사 포털은 대개 우리 서버와 다른 도메인이라, 브라우저가 교차 출처 요청을
     * 막는다. 그렇다고 아무 도메인에나 열면 남의 사이트가 로그인된 브라우저를 빌려
     * 리포트를 읽어 갈 수 있다. 그래서 <b>허용할 주소를 하나씩 적게</b> 한다.</p>
     */
    @Data
    public static class Embed {

        /**
         * 끼워 넣기를 허용할 출처. {@code https://portal.city.go.kr} 처럼 스킴까지 적는다.
         *
         * <p>비워 두면 교차 출처 임베드가 꺼진다. 같은 도메인에서 쓰는 경우에는 설정이
         * 필요 없다. 와일드카드({@code *})는 받지 않는다 — 로그인 쿠키를 함께 보내는
         * 요청이라 브라우저도 허용하지 않고, 허용한다면 전체 공개와 다름없다.</p>
         */
        private List<String> allowedOrigins = new ArrayList<>();

        /**
         * iframe 으로 감싸는 것을 허용할 상위 페이지.
         *
         * <p>{@code frame-ancestors} 에 그대로 들어간다. 비어 있으면 어떤 페이지도
         * 우리 화면을 감쌀 수 없다(클릭재킹 방지 기본값).</p>
         */
        private List<String> allowedFrameAncestors = new ArrayList<>();

        /**
         * 교차 출처로 세션 쿠키를 보낼지.
         *
         * <p>켜면 쿠키가 {@code SameSite=None; Secure} 로 나간다. <b>HTTPS 가 아니면
         * 브라우저가 그 쿠키를 아예 저장하지 않으므로</b>, 평문 HTTP 운영에서는 켜도
         * 소용이 없다. 사내망이라 HTTPS 를 안 쓰는 곳이면 iframe 이나 같은 도메인
         * 리버스 프록시로 붙이는 편이 낫다.</p>
         */
        private boolean crossSiteCookie = false;
    }

    /** 라이선스와 배포 패키지 서명 */
    private LicenseConfig license = new LicenseConfig();

    /**
     * 라이선스 설정.
     *
     * <p>공개키는 고객사 서버에 들어가고, 개인키는 <b>공급사 빌드 환경에만</b> 둔다.
     * 개인키가 고객사에 있으면 그쪽에서 라이선스를 스스로 발급하고 패키지를 새로 구울 수
     * 있어, 서명을 붙인 의미가 사라진다.</p>
     */
    @Data
    public static class LicenseConfig {

        /** 검증용 공개키 (Base64, X.509). 고객사 배포본에 들어간다. */
        private String publicKey = "";

        /** 서명용 개인키 (Base64, PKCS#8). 공급사 빌드 환경에만 둔다. */
        private String privateKey = "";

        /** 라이선스 파일 경로 */
        private String path = "license.key";

        /** 파일 대신 값을 직접 넣을 때. 컨테이너 환경에서 환경변수로 주입하기 좋다. */
        private String key = "";

        /**
         * 라이선스가 없으면 기동을 멈출지.
         *
         * <p>기본은 {@code false} 라 평가판으로 뜬다. 설치 직후 화면을 한 번 열어 보고
         * 정식 키를 요청하는 순서가 현장에서 보통이기 때문이다. 운영 배포에서는 켠다.</p>
         */
        private boolean require = false;
    }

    /**
     * 이름별 추가 데이터소스. 거래처나 계열 기관의 DB 를 붙일 때 쓴다.
     *
     * <p>{@code main} 은 스프링이 만든 기본 연결이라 여기 적지 않는다. 여기 적은 이름은
     * 리포트 정의의 {@code dataSet.dataSource} 에서 가리킨다.</p>
     */
    private Map<String, Datasource> datasources = new LinkedHashMap<>();

    /**
     * 외부 데이터소스 하나.
     *
     * <p>{@code writers} 를 적지 않으면 <b>아무도 이 DB 에 쿼리를 쓰지 못한다.</b> 새로
     * 붙이는 연결을 기본으로 열어 두면, 추가한 사실을 모르는 사이에 편집 권한자 전원이
     * 그 DB 를 읽게 된다. 의도적으로 열어야 열리게 두는 편이 안전하다.</p>
     */
    @Data
    public static class Datasource {

        private String url;
        private String username;
        private String password;
        private String driverClassName;

        /**
         * 연결을 읽기 전용으로 연다.
         *
         * <p>기본이 {@code true} 다. 리포트 도구가 남의 DB 에 쓰기를 할 일이 없고,
         * 드라이버가 막아 주면 조회만 허용한다는 약속이 코드 밖에서도 지켜진다.
         * 다만 이것은 거들 뿐이고, 진짜 방어선은 조회 전용 DB 계정을 받는 것이다.</p>
         */
        private boolean readOnly = true;

        /** 거래처 DB 는 우리 쪽 조회가 전부가 아니므로 풀을 작게 잡는다 */
        private int maxPoolSize = 5;

        private long connectionTimeoutMillis = 10_000;

        /** 이 DB 를 향한 쿼리를 쓸 수 있는 사람 */
        private Writers writers = new Writers();

        @Data
        public static class Writers {
            private List<String> users = new ArrayList<>();
            private List<String> departments = new ArrayList<>();
            private List<String> roles = new ArrayList<>();

            public boolean isEmpty() {
                return users.isEmpty() && departments.isEmpty() && roles.isEmpty();
            }
        }
    }

    @Data
    public static class Security {

        /**
         * 개발용 내장 계정 사용 여부.
         * 운영에서는 꺼 두고 기관 인증 체계에 연결한 UserDetailsService 를 등록한다.
         */
        private boolean inMemoryUsers = true;

        private String viewerId = "viewer";
        private String viewerPassword;

        private String designerId = "designer";
        private String designerPassword;

        private String adminId = "admin";
        private String adminPassword;

        /**
         * 아이디별 소속 부서 코드. 겸직은 쉼표로 잇는다 (예: {@code kim: "1100,2100"}).
         *
         * <p>리포트별 열람 권한을 부서 단위로 줄 때 쓴다. 사람이 늘면 설정 파일로는
         * 감당할 수 없으므로, 그때는 인사 연계를 읽는 {@code ViewerDirectory} 구현을
         * 빈으로 올린다. 여기 없는 아이디는 부서가 없는 사람으로 보아 부서 규칙에
         * 걸리지 않는다.</p>
         */
        private Map<String, String> departments = new LinkedHashMap<>();

        /**
         * 기본 DB(main)에 쿼리를 쓸 수 있는 사람.
         *
         * <p>비워 두면 편집 권한자 모두가 쓸 수 있다. 지금까지의 동작이고, 내부 직원만
         * 편집하는 동안은 이대로도 된다. <b>거래처처럼 밖의 사람에게 편집 권한을 주는
         * 순간 반드시 채워야 한다.</b> 비어 있으면 그 사람도 우리 업무 DB 에 임의 조회를
         * 쓸 수 있고, 그것은 DB 를 통째로 내주는 것과 같다.</p>
         */
        private Datasource.Writers mainWriters = new Datasource.Writers();

        /**
         * 내장 계정을 쓰기로 해 놓고 비밀번호를 비워 두면 기동을 멈춘다.
         *
         * <p>기본 비밀번호를 코드에 박아 두면 그 값이 그대로 운영까지 따라간다.
         * 설정하지 않았다는 사실을 배포 시점에 알아채게 하는 편이 안전하다.</p>
         */
        @PostConstruct
        void verify() {
            if (!inMemoryUsers) {
                return;
            }
            List<String> missing = new ArrayList<>();
            if (isBlank(viewerPassword)) {
                missing.add("kreport.security.viewer-password");
            }
            if (isBlank(designerPassword)) {
                missing.add("kreport.security.designer-password");
            }
            if (isBlank(adminPassword)) {
                missing.add("kreport.security.admin-password");
            }
            if (!missing.isEmpty()) {
                throw new IllegalStateException(
                        "내장 계정 비밀번호가 설정되지 않았습니다: " + String.join(", ", missing)
                                + " (운영에서는 kreport.security.in-memory-users=false 로 두고 "
                                + "기관 인증 체계를 연결하세요)");
            }
        }

        private boolean isBlank(String value) {
            return value == null || value.isBlank();
        }
    }

    @Data
    public static class Font {

        /**
         * PDF 출력에 쓸 TTF 경로. 비우면 classpath:fonts 와 OS 기본 경로를 차례로 찾는다.
         *
         * <p>표준 14 폰트에는 한글 글리프가 없어서, 리눅스 서버처럼 한글 폰트가 없는 곳에서는
         * 이 값을 지정하지 않으면 출력물의 한글이 전부 깨진다.</p>
         */
        private String regular = "";

        private String bold = "";
    }

    @Data
    public static class Image {

        /**
         * 리포트 IMAGE 요소가 읽을 수 있는 위치.
         *
         * <p>기본은 classpath 안쪽뿐이다. 임의 URL 을 허용하면 리포트 정의를 쓸 수 있는 사람이
         * 서버를 통해 내부망에 요청을 보낼 수 있고(SSRF), 파일 경로를 허용하면 서버의 파일을
         * 이미지로 읽어 낼 수 있다.</p>
         */
        private boolean allowClasspath = true;

        /** 허용할 외부 이미지 주소 접두어. 비어 있으면 외부 주소를 쓰지 않는다. */
        private List<String> allowedUrlPrefixes = new ArrayList<>();

        /** 허용할 파일 경로 접두어. 비어 있으면 파일 경로를 쓰지 않는다. */
        private List<String> allowedFilePrefixes = new ArrayList<>();
    }
}
