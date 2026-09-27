package kr.co.kreport.access;

import java.util.Set;

/**
 * 열람 판단에 쓰는 사람 정보.
 *
 * <p>스프링 시큐리티의 인증 객체를 그대로 들고 다니지 않는 이유는, 기관마다 인증 체계가
 * 달라도({@code SSO}, GPKI, LDAP) 판단 규칙은 같아야 하기 때문이다. 어디서 왔든 이
 * 세 가지만 채워 주면 열람 판단이 돈다.</p>
 *
 * @param username    사용자 아이디
 * @param departments 소속 부서 코드. 겸직이 있어 하나로 두지 않았다.
 * @param roles       ROLE_ 접두어를 뗀 권한 이름
 */
public record Viewer(String username, Set<String> departments, Set<String> roles) {

    public Viewer {
        departments = departments == null ? Set.of() : Set.copyOf(departments);
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    /** 부서를 알 수 없는 사람. 부서 규칙에는 걸리지 않고 사용자·권한 규칙으로만 열린다. */
    public static Viewer of(String username, Set<String> roles) {
        return new Viewer(username, Set.of(), roles);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}
