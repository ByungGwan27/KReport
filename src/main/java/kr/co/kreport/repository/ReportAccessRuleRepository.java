package kr.co.kreport.repository;

import kr.co.kreport.access.GrantType;
import kr.co.kreport.domain.ReportAccessRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ReportAccessRuleRepository extends JpaRepository<ReportAccessRule, Long> {

    List<ReportAccessRule> findByReportIdOrderByGrantTypeAscGrantValueAsc(String reportId);

    /** 목록 화면에서 리포트 수십 건의 규칙을 한 번에 가져온다. 건마다 물으면 N+1 이 된다. */
    List<ReportAccessRule> findByReportIdIn(Collection<String> reportIds);

    void deleteByReportId(String reportId);

    boolean existsByReportIdAndGrantTypeAndGrantValue(String reportId, GrantType grantType, String grantValue);

    /** 조직 개편 때 부서 코드를 한꺼번에 찾기 위한 것 */
    List<ReportAccessRule> findByGrantTypeAndGrantValue(GrantType grantType, String grantValue);
}
