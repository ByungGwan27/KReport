package kr.co.kreport.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kr.co.kreport.access.GrantType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 리포트 하나를 누구에게 열어 줄지 적은 규칙 한 줄.
 *
 * <p>정의(JSON)와 따로 두는 이유는 두 가지다. 열람 범위는 디자이너가 아니라 관리자가
 * 정하는 값이고, 조직 개편 때 여러 리포트의 부서 코드를 한꺼번에 바꿔야 하는데 JSON
 * 안에 묻혀 있으면 질의로 찾을 수 없다.</p>
 */
@Entity
@Table(name = "report_access_rule",
        uniqueConstraints = @UniqueConstraint(name = "uk_access_rule",
                columnNames = {"report_id", "grant_type", "grant_value"}),
        indexes = @Index(name = "ix_access_rule_report", columnList = "report_id"))
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class ReportAccessRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** {@link ReportDefinition#getReportId()}. 외래키를 걸지 않고 업무 식별자로 잇는다. */
    @Column(name = "report_id", nullable = false, length = 100)
    private String reportId;

    @Enumerated(EnumType.STRING)
    @Column(name = "grant_type", nullable = false, length = 20)
    private GrantType grantType;

    /** 부서 코드, 사용자 아이디, 권한 이름 중 하나 */
    @Column(name = "grant_value", nullable = false, length = 100)
    private String grantValue;

    /** 왜 열어 줬는지. 감사에서 가장 먼저 묻는 것이라 자리를 만들어 둔다. */
    @Column(length = 200)
    private String note;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public ReportAccessRule(String reportId, GrantType grantType, String grantValue, String createdBy) {
        this.reportId = reportId;
        this.grantType = grantType;
        this.grantValue = grantValue;
        this.createdBy = createdBy;
    }

    public boolean matches(GrantType type, String value) {
        return grantType == type && grantValue.equalsIgnoreCase(value);
    }
}
