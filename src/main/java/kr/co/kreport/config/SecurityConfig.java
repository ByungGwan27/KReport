package kr.co.kreport.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;
import org.springframework.security.web.servlet.util.matcher.MvcRequestMatcher;

import java.util.List;

/**
 * 접근 통제.
 *
 * <p>권한을 화면 단위가 아니라 <b>할 수 있는 일</b> 단위로 나눈다. 특히 리포트 정의를
 * 저장하는 경로는 조회 SQL 을 직접 쓰는 자리라, 업무 DB 를 읽는 권한과 동급으로 막는다.
 * 목록 화면을 볼 수 있다고 정의를 고칠 수 있어서는 안 된다.</p>
 *
 * <p>기관의 인증 체계(SSO, GPKI 등)를 붙일 때는 {@link #userDetailsService} 를 그쪽
 * 구현으로 갈아 끼우면 된다. 경로별 권한 규칙은 그대로 쓸 수 있다.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String API = "/api/reports";

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, HandlerMappingIntrospector introspector)
            throws Exception {
        MvcRequestMatcher.Builder mvc = new MvcRequestMatcher.Builder(introspector);

        // 토큰을 자바스크립트가 읽어 헤더로 되돌려 보낼 수 있어야 하므로 HttpOnly 를 끈다.
        // 값 자체는 공격자가 교차 출처에서 읽을 수 없으므로 CSRF 방어는 유지된다.
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null);

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(csrfHandler))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(mvc.pattern("/login"), mvc.pattern("/css/**"),
                                mvc.pattern("/js/**"), mvc.pattern("/favicon.ico")).permitAll()

                        // --- 배포 패키지를 굽고 설치하는 자리. 설치는 리포트 정의를
                        //     새로 들이는 일이라 편집과 같은 무게이고, 굽기는 납품 산출물을
                        //     만드는 일이라 둘 다 관리자에게만 연다.
                        .requestMatchers("/api/deploy/**").hasRole(ReportRole.ADMIN)
                        .requestMatchers(mvc.pattern("/deploy"), mvc.pattern("/deploy/**"))
                        .hasRole(ReportRole.ADMIN)

                        // --- 열람 권한 관리. 리포트를 만드는 사람과 그 자료를 누구에게
                        //     보일지 정하는 사람이 같으면 승인 절차가 형식이 되므로 관리자에게만 연다.
                        //     아래 편집 규칙보다 먼저 두어야 POST 가 DESIGNER 로 새지 않는다.
                        .requestMatchers(API + "/*/access", API + "/*/access/**")
                        .hasRole(ReportRole.ADMIN)

                        // --- 정의 편집. 조회 SQL 을 쓰는 자리라 DB 열람 권한과 같은 무게로 막는다
                        .requestMatchers(org.springframework.http.HttpMethod.POST,
                                API + "/*", API + "/validate", API + "/preview", API + "/fields")
                        .hasRole(ReportRole.DESIGNER)
                        .requestMatchers(mvc.pattern("/designer"), mvc.pattern("/designer/**"))
                        .hasRole(ReportRole.DESIGNER)

                        // --- 삭제와 이력 열람
                        .requestMatchers(org.springframework.http.HttpMethod.DELETE, API + "/**")
                        .hasRole(ReportRole.ADMIN)
                        .requestMatchers(mvc.pattern("/logs"), mvc.pattern("/h2-console/**"),
                                mvc.pattern("/access/**"))
                        .hasRole(ReportRole.ADMIN)

                        // --- 조회와 내려받기
                        .anyRequest().hasRole(ReportRole.VIEWER))

                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .permitAll())
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                        .permitAll())

                .exceptionHandling(ex -> ex
                        // API 호출이 만료된 세션으로 들어오면 로그인 화면 HTML 대신 401 을 준다.
                        // 그래야 호출한 쪽이 로그인 페이지를 데이터로 착각하지 않는다.
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                new AntPathRequestMatcher("/api/**"))
                        // 화면 요청은 로그인 페이지로 보낸다. 이 항목을 함께 등록하지 않으면
                        // 위의 401 이 전체 기본값이 되어 화면까지 401 로 끊긴다.
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/login"),
                                AnyRequestMatcher.INSTANCE))

                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.newSession()))

                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                // 리포트 본문은 서버가 만든 인라인 스타일과 SVG 로 이뤄져 있다.
                                // 스크립트는 같은 출처에서만 받는다.
                                "default-src 'self'; img-src 'self' data:; "
                                        + "style-src 'self' 'unsafe-inline'; script-src 'self'; "
                                        + "frame-ancestors 'none'; base-uri 'self'"))
                        .frameOptions(frame -> frame.sameOrigin())
                        .referrerPolicy(referrer -> referrer.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter
                                        .ReferrerPolicy.SAME_ORIGIN)));

        return http.build();
    }

    /**
     * 개발용 계정. 운영에서는 기관 인증 체계에 연결한 구현으로 대체한다.
     *
     * <p>비밀번호를 설정 파일에서 받도록 두었으므로, 값을 비워 두면 기동하지 못한다.
     * 소스에 박힌 기본 비밀번호가 그대로 운영에 따라가는 일을 막기 위함이다.</p>
     */
    @Bean
    @ConditionalOnProperty(name = "kreport.security.in-memory-users", havingValue = "true")
    public UserDetailsService userDetailsService(PasswordEncoder encoder,
                                                 KReportProperties properties) {
        KReportProperties.Security security = properties.getSecurity();
        List<UserDetails> users = List.of(
                User.withUsername(security.getViewerId())
                        .password(encoder.encode(security.getViewerPassword()))
                        .roles(ReportRole.VIEWER).build(),
                User.withUsername(security.getDesignerId())
                        .password(encoder.encode(security.getDesignerPassword()))
                        .roles(ReportRole.VIEWER, ReportRole.DESIGNER).build(),
                User.withUsername(security.getAdminId())
                        .password(encoder.encode(security.getAdminPassword()))
                        .roles(ReportRole.VIEWER, ReportRole.DESIGNER, ReportRole.ADMIN).build());

        return new InMemoryUserDetailsManager(users);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
