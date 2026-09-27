package kr.co.kreport.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import kr.co.kreport.access.AccessMode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 등록된 리포트 한 건. 레이아웃 정의는 JSON 문자열로 보관한다.
 *
 * <p>정의를 정규화된 테이블로 쪼개지 않은 이유는, 리포트 정의가 통째로 읽고
 * 통째로 쓰는 문서형 데이터이기 때문이다. 밴드와 요소를 테이블로 펼치면
 * 저장할 때마다 delete-insert 가 되고 구조가 바뀔 때마다 마이그레이션이 필요하다.</p>
 */
@Entity
@Table(name = "report_definition")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class ReportDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 업무에서 부르는 식별자. URL 과 API 에서 이 값을 쓴다. */
    @Column(name = "report_id", nullable = false, unique = true, length = 100)
    private String reportId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 500)
    private String description;

    /** 목록 분류 (예: 세입세출, 민원, 인사) */
    @Column(length = 100)
    private String category;

    @Lob
    @Column(name = "template_json", nullable = false)
    private String templateJson;

    @Column(name = "use_yn", nullable = false, length = 1)
    private String useYn = "Y";

    /**
     * 열람 통제 방식. 기본은 공개다.
     *
     * <p>기본값을 공개로 둔 것은 이미 등록된 리포트가 이 기능을 켜는 순간 전부 막히는
     * 일을 피하기 위해서다. 통제가 필요한 리포트만 골라 {@code RESTRICTED} 로 바꾸고
     * 규칙을 단다.</p>
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "access_mode", nullable = false, length = 20)
    private AccessMode accessMode = AccessMode.PUBLIC;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * 낙관적 잠금. 디자이너를 두 사람이 같이 열어 두는 상황이 실제로 자주 생기는데,
     * 뒤에 저장한 쪽이 앞사람 작업을 조용히 덮어쓰면 원인을 찾기가 어렵다.
     */
    @Version
    @Column(name = "row_version")
    private Long rowVersion;

    public boolean isActive() {
        return "Y".equalsIgnoreCase(useYn);
    }

    /** 목록 화면에서 자물쇠 표시에 쓴다 */
    public boolean isRestricted() {
        return accessMode != null && accessMode.isRestricted();
    }
}
