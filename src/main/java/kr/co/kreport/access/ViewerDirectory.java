package kr.co.kreport.access;

import java.util.Set;

/**
 * 로그인한 사람의 소속 부서를 알려 준다.
 *
 * <p>이 자리를 인터페이스로 뺀 이유는 <b>부서를 어디서 가져오는지가 기관마다 다르기</b>
 * 때문이다. 어떤 곳은 인사 시스템 연계로, 어떤 곳은 SSO 토큰의 클레임으로, 어떤 곳은
 * LDAP 속성으로 온다. 리포트 엔진이 그중 하나를 알게 되면 다음 기관에서 그 코드를
 * 들어내야 한다.</p>
 *
 * <p>구현을 스프링 빈으로 올리면 기본 구현({@code PropertiesViewerDirectory}) 대신 쓰인다.</p>
 */
public interface ViewerDirectory {

    /**
     * @param username 로그인 아이디
     * @param roles    인증에서 이미 확인된 권한. 디렉터리가 다시 조회할 필요가 없게 넘긴다.
     * @return 판단에 쓸 사람 정보. 부서를 못 찾으면 부서 없는 {@link Viewer} 를 돌려준다.
     */
    Viewer lookup(String username, Set<String> roles);
}
