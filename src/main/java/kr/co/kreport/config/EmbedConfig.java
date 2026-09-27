package kr.co.kreport.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.server.CookieSameSiteSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * 고객사 화면에 리포트를 끼워 넣기 위한 교차 출처 설정.
 *
 * <h2>왜 설정으로만 열리나</h2>
 * <p>교차 출처 요청에 로그인 쿠키가 함께 실린다. 아무 도메인에나 열어 두면 남의 사이트가
 * 방문자의 로그인 상태를 빌려 리포트를 읽어 갈 수 있다. 그래서 허용할 주소를 하나씩
 * 적게 하고, 적지 않으면 꺼 둔다.</p>
 *
 * <h2>HTTPS 가 아니면 교차 출처 임베드는 동작하지 않는다</h2>
 * <p>{@code SameSite=None} 쿠키는 {@code Secure} 없이는 브라우저가 저장하지 않고,
 * {@code Secure} 는 HTTPS 에서만 붙는다. 평문 HTTP 로 운영하는 사내망이라면 이 방식
 * 대신 같은 도메인 리버스 프록시로 붙이는 편이 확실하다. 설정만 켜 두고 안 되는 이유를
 * 못 찾는 일이 없도록 기동할 때 알려 준다.</p>
 */
@Slf4j
@Configuration
public class EmbedConfig implements WebMvcConfigurer {

    private final KReportProperties.Embed embed;

    public EmbedConfig(KReportProperties properties) {
        this.embed = properties.getEmbed();
        warnIfMisconfigured();
    }

    private void warnIfMisconfigured() {
        if (embed.getAllowedOrigins().isEmpty()) {
            return;
        }
        log.info("임베드 허용 출처: {}", embed.getAllowedOrigins());
        if (!embed.isCrossSiteCookie()) {
            log.warn("임베드 출처를 열었지만 cross-site-cookie 가 꺼져 있습니다. "
                    + "고객사 화면에서 로그인 세션이 실리지 않아 401 이 납니다.");
        }
        boolean plainHttp = embed.getAllowedOrigins().stream()
                .anyMatch(o -> o.startsWith("http://"));
        if (plainHttp && embed.isCrossSiteCookie()) {
            log.warn("평문 http 출처가 있습니다. SameSite=None 쿠키는 HTTPS 에서만 유지되므로 "
                    + "그 출처에서는 임베드가 동작하지 않습니다.");
        }
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = embed.getAllowedOrigins();
        if (origins.isEmpty()) {
            return;
        }
        registry.addMapping("/embed/**")
                // 와일드카드를 쓰지 않는다. 쿠키를 싣는 요청이라 브라우저도 거부하고,
                // 허용한다면 리포트를 전체 공개하는 것과 같다.
                .allowedOrigins(origins.toArray(String[]::new))
                .allowedMethods("GET")
                .allowCredentials(true)
                .maxAge(1800);
    }

    /**
     * 교차 출처로 세션을 유지하려면 쿠키에 {@code SameSite=None} 이 필요하다.
     *
     * <p>기본값인 {@code Lax} 는 남의 사이트에서 온 요청에 쿠키를 싣지 않는다. 그 기본값이
     * CSRF 를 한 겹 막아 주므로, 임베드를 쓰지 않는 설치본에서는 그대로 두는 것이 낫다.</p>
     */
    @Bean
    CookieSameSiteSupplier embedCookieSameSite() {
        return embed.isCrossSiteCookie()
                ? CookieSameSiteSupplier.ofNone()
                : CookieSameSiteSupplier.ofLax();
    }
}
