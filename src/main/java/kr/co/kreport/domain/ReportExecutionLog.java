package kr.co.kreport.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import kr.co.kreport.export.ExportFormat;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 리포트 실행 이력.
 *
 * <p>리포트는 업무 데이터를 대량으로 꺼내 파일로 내보내는 통로다. 누가 언제 어떤 조건으로
 * 무엇을 몇 건 내려받았는지 남기지 않으면 개인정보 처리 현황 점검이나 유출 사고 조사에서
 * 답할 수 있는 게 없다. 성공/실패를 모두 남긴다.</p>
 */
@Entity
@Table(name = "report_execution_log", indexes = {
        @Index(name = "ix_exec_report", columnList = "report_id, executed_at"),
        @Index(name = "ix_exec_user", columnList = "executed_by, executed_at")
})
@Getter
@Setter
@NoArgsConstructor
public class ReportExecutionLog {

    public enum Status {
        SUCCESS, FAILURE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_id", nullable = false, length = 100)
    private String reportId;

    @Column(name = "report_name", length = 200)
    private String reportName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ExportFormat format;

    /** 조회 조건. 원문 그대로 남겨야 재현이 가능하다. */
    @Column(name = "parameters", length = 2000)
    private String parameters;

    @Column(name = "row_count")
    private Integer rowCount;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "elapsed_ms")
    private Long elapsedMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.SUCCESS;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "executed_by", length = 100)
    private String executedBy;

    @Column(name = "client_ip", length = 45)
    private String clientIp;

    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt = LocalDateTime.now();
}
