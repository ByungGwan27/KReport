package kr.co.kreport.repository;

import kr.co.kreport.domain.ReportDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ReportDefinitionRepository extends JpaRepository<ReportDefinition, Long> {

    Optional<ReportDefinition> findByReportId(String reportId);

    boolean existsByReportId(String reportId);

    List<ReportDefinition> findAllByOrderByCategoryAscNameAsc();

    @Query("""
            select r from ReportDefinition r
            where r.useYn = 'Y'
              and (:keyword is null
                   or lower(r.name) like lower(concat('%', :keyword, '%'))
                   or lower(r.reportId) like lower(concat('%', :keyword, '%')))
            order by r.category, r.name
            """)
    List<ReportDefinition> search(String keyword);

    @Query("select distinct r.category from ReportDefinition r where r.category is not null order by r.category")
    List<String> findCategories();
}
