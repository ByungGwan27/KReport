package kr.co.kreport.access;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 지금 요청을 보낸 사람을 열람 판단에 쓸 {@link Viewer} 로 바꾼다.
 *
 * <p>권한 이름에서 {@code ROLE_} 접두어를 떼는 일을 여기 한 곳에서 한다. 스프링은 안에서
 * 접두어를 붙여 두는데, 그걸 그대로 들고 다니면 열람 규칙에 적힌 {@code ADMIN} 과
 * {@code ROLE_ADMIN} 이 서로 안 맞는 일이 규칙을 비교하는 자리마다 생긴다.</p>
 */
@Component
public class CurrentViewer {

    private static final String ROLE_PREFIX = "ROLE_";

    private final ViewerDirectory directory;

    public CurrentViewer(ViewerDirectory directory) {
        this.directory = directory;
    }

    public Viewer resolve(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = username(authentication, request);
        return directory.lookup(username, roles(authentication));
    }

    /**
     * 실행 주체. 감사 이력에 남는 이름이다.
     *
     * <p>인증을 거치지 않은 요청은 접근 통제에서 이미 막히므로 여기까지 오지 않는다.
     * 그래도 값이 비어 있으면 이력에 빈 이름을 남기는 대신 알 수 없음으로 적는다.
     * 누가 받아 갔는지 모르는 이력은 없는 것과 같아서, 조용히 넘기지 않고 기록해 둔다.</p>
     */
    public String username(HttpServletRequest request) {
        return username(SecurityContextHolder.getContext().getAuthentication(), request);
    }

    private String username(Authentication authentication, HttpServletRequest request) {
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return authentication.getName();
        }
        return request != null && request.getUserPrincipal() != null
                ? request.getUserPrincipal().getName()
                : "unknown";
    }

    private Set<String> roles(Authentication authentication) {
        if (authentication == null) {
            return Set.of();
        }
        Set<String> roles = new LinkedHashSet<>();
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String name = authority.getAuthority();
            roles.add(name.startsWith(ROLE_PREFIX) ? name.substring(ROLE_PREFIX.length()) : name);
        }
        return roles;
    }
}
