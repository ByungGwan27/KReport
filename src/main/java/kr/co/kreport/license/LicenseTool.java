package kr.co.kreport.license;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 키를 만들고 라이선스를 발급하는 도구. <b>공급사에서만 쓴다.</b>
 *
 * <p>스프링 빈이 아니라 {@code main} 하나로 둔 이유는, 이 코드가 고객사 서버에서 <b>실행될
 * 일이 없어야</b> 하기 때문이다. 웹 경로로 열어 두면 그 경로를 여는 순간 누구나 라이선스를
 * 발급할 수 있게 된다. 개인키도 설정 파일이 아니라 명령줄로 받는다.</p>
 *
 * <pre>
 * # 1. 키 한 쌍 만들기 (한 번만)
 * java -cp target/classes kr.co.kreport.license.LicenseTool keygen
 *
 * # 2. 라이선스 발급
 * java -cp target/classes kr.co.kreport.license.LicenseTool issue \
 *      --private-key &lt;개인키&gt; --licensee "○○시청" --id KR-2026-001 \
 *      --expires 2027-12-31 --hosts report.city.go.kr --max-reports 50 \
 *      --out license.key
 * </pre>
 */
public final class LicenseTool {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private LicenseTool() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usage();
            return;
        }
        switch (args[0]) {
            case "keygen" -> keygen();
            case "issue" -> issue(args);
            case "show" -> show(args);
            default -> usage();
        }
    }

    /** 키 한 쌍을 만든다. 개인키는 공급사 금고에, 공개키는 배포본 설정에 넣는다. */
    private static void keygen() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(SignedEnvelope.ALGORITHM);
        KeyPair pair = generator.generateKeyPair();

        System.out.println("# 고객사 배포본 설정에 넣습니다 (application.yml)");
        System.out.println("kreport.license.public-key: "
                + SignedEnvelope.encodeKey(pair.getPublic().getEncoded()));
        System.out.println();
        System.out.println("# 공급사 빌드 환경에만 둡니다. 절대 납품물에 넣지 마세요.");
        System.out.println("kreport.license.private-key: "
                + SignedEnvelope.encodeKey(pair.getPrivate().getEncoded()));
    }

    private static void issue(String[] args) throws Exception {
        Args a = Args.of(args);
        String privateKey = a.require("--private-key");

        License license = new License(
                a.require("--licensee"),
                a.get("--id", "KR-" + LocalDate.now().getYear() + "-000"),
                LocalDate.now(),
                a.get("--expires") == null ? null : LocalDate.parse(a.get("--expires")),
                split(a.get("--hosts")),
                Integer.parseInt(a.get("--max-reports", "0")),
                new LinkedHashSet<>(split(a.get("--features"))));

        String sealed = SignedEnvelope.seal(
                MAPPER.writeValueAsBytes(license), SignedEnvelope.privateKey(privateKey));

        String out = a.get("--out");
        if (out == null) {
            System.out.println(sealed);
        } else {
            Files.writeString(Path.of(out), sealed, StandardCharsets.UTF_8);
            System.out.println("발급 완료: " + out);
            System.out.println("  고객사   " + license.licensee());
            System.out.println("  번호     " + license.licenseId());
            System.out.println("  만료     " + (license.expiresOn() == null ? "무기한" : license.expiresOn()));
            System.out.println("  호스트   " + (license.hosts().isEmpty() ? "제한 없음" : license.hosts()));
            System.out.println("  리포트   " + (license.maxReports() <= 0 ? "제한 없음" : license.maxReports() + "건"));
        }
    }

    /** 발급된 라이선스를 공개키로 열어 확인한다. 문의가 왔을 때 내용을 보는 용도. */
    private static void show(String[] args) throws Exception {
        Args a = Args.of(args);
        String body = a.get("--file") != null
                ? Files.readString(Path.of(a.require("--file")), StandardCharsets.UTF_8)
                : a.require("--key");

        byte[] payload = SignedEnvelope.open(body, SignedEnvelope.publicKey(a.require("--public-key")));
        System.out.println(MAPPER.writerWithDefaultPrettyPrinter()
                .writeValueAsString(MAPPER.readValue(payload, License.class)));
    }

    private static List<String> split(String value) {
        return value == null || value.isBlank()
                ? List.of()
                : Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static void usage() {
        System.out.println("""
                KReport 라이선스 도구 (공급사 전용)

                  keygen
                      Ed25519 키 한 쌍을 만듭니다. 공개키는 배포본에, 개인키는 금고에.

                  issue --private-key <키> --licensee <고객사> [옵션]
                      --id           발급 번호 (예: KR-2026-001)
                      --expires      만료일 yyyy-MM-dd. 없으면 무기한
                      --hosts        허용 호스트명, 쉼표로 여럿. 없으면 어디서나
                      --max-reports  배포 가능 리포트 수. 0 이면 제한 없음
                      --features     켤 기능, 쉼표로 여럿 (TRIAL 을 넣으면 평가판)
                      --out          저장할 파일. 없으면 화면에 출력

                  show --public-key <키> (--file <경로> | --key <값>)
                      발급된 라이선스의 내용을 확인합니다.
                """);
    }

    /** 아주 작은 명령줄 파서. 이 도구 하나 때문에 라이브러리를 더하지 않는다. */
    private record Args(java.util.Map<String, String> values) {

        static Args of(String[] args) {
            java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
            for (int i = 1; i < args.length - 1; i++) {
                if (args[i].startsWith("--")) {
                    map.put(args[i], args[i + 1]);
                }
            }
            return new Args(map);
        }

        String get(String name) {
            return values.get(name);
        }

        String get(String name, String fallback) {
            return values.getOrDefault(name, fallback);
        }

        String require(String name) {
            String value = values.get(name);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " 이(가) 필요합니다.");
            }
            return value;
        }
    }

    static Set<String> noFeatures() {
        return Set.of();
    }
}
