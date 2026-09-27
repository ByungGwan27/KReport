package kr.co.kreport.repository;

import kr.co.kreport.domain.ReportExecutionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportExecutionLogRepository extends JpaRepository<ReportExecutionLog, Long> {

    Page<ReportExecutionLog> findAllByOrderByExecutedAtDesc(Pageable pageable);

    Page<ReportExecutionLog> findByReportIdOrderByExecutedAtDesc(String reportId, Pageable pageable);
}
