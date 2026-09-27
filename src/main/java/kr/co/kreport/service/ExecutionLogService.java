package kr.co.kreport.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.kreport.domain.ReportExecutionLog;
import kr.co.kreport.engine.layout.RenderedReport;
import kr.co.kreport.export.ExportFormat;
import kr.co.kreport.repository.ReportExecutionLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 실행 이력 기록 전담.
 *
 * <p>실행 서비스에서 떼어 낸 이유는 트랜잭션 때문이다. 이력은 본 작업이 실패해 롤백되더라도
 * 남아야 하므로 {@code REQUIRES_NEW} 가 필요한데, 같은 빈 안에서 호출하면 프록시를 타지 않아
 * 전파 설정이 그대로 무시된다. 별도 빈으로 두어야 의도한 대로 동작한다.</p>
 */
@Slf4j
@Service
public class ExecutionLogService {

    private final ReportExecutionLogRepository repository;
    private final ObjectMapper mapper;

    public ExecutionLogService(ReportExecutionLogRepository repository, ObjectMapper templateObjectMapper) {
        this.repository = repository;
        this.mapper = templateObjectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void success(String reportId, String reportName, ExportFormat format,
                        Map<String, ?> input, RenderedReport report, ReportRunService.Caller caller) {
        ReportExecutionLog entry = base(reportId, reportName, format, input, caller);
        entry.setRowCount(report.getRowCount());
        entry.setPageCount(report.getPageCount());
        entry.setElapsedMs(report.getElapsedMillis());
        entry.setStatus(ReportExecutionLog.Status.SUCCESS);
        repository.save(entry);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failure(String reportId, String reportName, ExportFormat format,
                        Map<String, ?> input, ReportRunService.Caller caller,
                        Exception cause, long elapsedMillis) {
        ReportExecutionLog entry = base(reportId, reportName, format, input, caller);
        entry.setStatus(ReportExecutionLog.Status.FAILURE);
        entry.setElapsedMs(elapsedMillis);
        entry.setErrorMessage(truncate(cause.getMessage(), 1000));
        repository.save(entry);
        log.warn("리포트 실행 실패: {} - {}", reportId, cause.getMessage());
    }

    @Transactional(readOnly = true)
    public Page<ReportExecutionLog> recent(Pageable pageable) {
        return repository.findAllByOrderByExecutedAtDesc(pageable);
    }

    @Transactional(readOnly = true)
    public Page<ReportExecutionLog> byReport(String reportId, Pageable pageable) {
        return repository.findByReportIdOrderByExecutedAtDesc(reportId, pageable);
    }

    private ReportExecutionLog base(String reportId, String reportName, ExportFormat format,
                                    Map<String, ?> input, ReportRunService.Caller caller) {
        ReportExecutionLog entry = new ReportExecutionLog();
        entry.setReportId(reportId);
        entry.setReportName(reportName);
        entry.setFormat(format);
        entry.setParameters(truncate(toJson(input), 2000));
        entry.setExecutedBy(caller == null ? "anonymous" : caller.userId());
        entry.setClientIp(caller == null ? null : caller.clientIp());
        entry.setExecutedAt(LocalDateTime.now());
        return entry;
    }

    private String toJson(Map<String, ?> input) {
        try {
            return mapper.writeValueAsString(input);
        } catch (Exception e) {
            return String.valueOf(input);
        }
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
