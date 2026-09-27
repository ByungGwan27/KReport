package kr.co.kreport.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.kreport.access.AccessMode;
import kr.co.kreport.access.ReportAccessService;
import kr.co.kreport.access.Viewer;
import kr.co.kreport.engine.data.DataSourceRegistry;
import kr.co.kreport.domain.ReportDefinition;
import kr.co.kreport.engine.TemplateValidator;
import kr.co.kreport.repository.ReportDefinitionRepository;
import kr.co.kreport.support.ReportNotFoundException;
import kr.co.kreport.template.ReportTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 리포트 정의의 저장, 조회, 템플릿 직렬화를 담당한다.
 */
@Slf4j
@Service
public class ReportDefinitionService {

    private final ReportDefinitionRepository repository;
    private final ReportAccessService accessService;
    private final DataSourceRegistry dataSources;
    private final ObjectMapper mapper;

    /**
     * 파싱된 템플릿 캐시.
     *
     * <p>리포트 정의는 읽기가 압도적으로 많고 쓰기는 드물다. 실행할 때마다 수백 KB 짜리
     * JSON 을 다시 파싱할 이유가 없다. 저장 시점에 해당 항목만 비운다.</p>
     */
    private final Map<String, CachedTemplate> cache = new ConcurrentHashMap<>();

    public ReportDefinitionService(ReportDefinitionRepository repository,
                                   ReportAccessService accessService,
                                   DataSourceRegistry dataSources,
                                   ObjectMapper templateObjectMapper) {
        this.repository = repository;
        this.accessService = accessService;
        this.dataSources = dataSources;
        this.mapper = templateObjectMapper;
    }

    @Transactional(readOnly = true)
    public List<ReportDefinition> findAll() {
        return repository.findAllByOrderByCategoryAscNameAsc();
    }

    @Transactional(readOnly = true)
    public List<ReportDefinition> search(String keyword) {
        return repository.search(keyword == null || keyword.isBlank() ? null : keyword.trim());
    }

    @Transactional(readOnly = true)
    public List<String> categories() {
        return repository.findCategories();
    }

    @Transactional(readOnly = true)
    public ReportDefinition get(String reportId) {
        return repository.findByReportId(reportId)
                .orElseThrow(() -> new ReportNotFoundException(reportId));
    }

    /**
     * 실행에 쓸 템플릿.
     *
     * <p><b>돌려받은 객체를 고치면 안 된다.</b> 캐시에 들어 있는 바로 그 인스턴스라서,
     * 한 번 고치면 그 뒤로 같은 리포트를 뽑는 모든 사람이 고쳐진 정의로 출력물을 받는다.
     * 그래도 이 경로에서 복사본을 만들지 않는 이유는, 레이아웃이 요소 하나하나를 수만 번
     * 훑는 동안 템플릿은 읽기만 하기 때문이다. 실행할 때마다 수백 KB 를 복제하면 캐시를
     * 둔 뜻이 없어진다. 규약이 지켜지는지는 {@code 실행 뒤 템플릿이 그대로인가} 테스트가
     * 지킨다.</p>
     *
     * <p>고칠 수 있는 사본이 필요하면 {@link #loadEditableTemplate(String)} 을 쓴다.</p>
     */
    @Transactional(readOnly = true)
    public ReportTemplate loadTemplate(String reportId) {
        return cached(get(reportId)).template();
    }

    /**
     * 마음대로 고쳐도 되는 템플릿 사본.
     *
     * <p>디자이너로 내보내거나 화면 모델에 얹는 등 <b>엔진 밖으로 나가는</b> 경로는 모두
     * 이쪽을 쓴다. 저쪽에서 무엇을 하든 캐시가 흔들리지 않는다. 편집은 실행에 견주면
     * 아주 드물게 일어나므로 복제 비용을 치를 만하다.</p>
     */
    @Transactional(readOnly = true)
    public ReportTemplate loadEditableTemplate(String reportId) {
        ReportDefinition definition = get(reportId);
        ReportTemplate copy = parse(cached(definition).json());
        applyIdentity(copy, definition);
        return copy;
    }

    private CachedTemplate cached(ReportDefinition definition) {
        CachedTemplate hit = cache.get(definition.getReportId());
        if (hit != null && hit.matches(definition)) {
            return hit;
        }
        String json = definition.getTemplateJson();
        ReportTemplate template = parse(json);
        applyIdentity(template, definition);

        CachedTemplate fresh = new CachedTemplate(definition.getRowVersion(), template, json);
        cache.put(definition.getReportId(), fresh);
        return fresh;
    }

    /**
     * 목록에 보이는 이름과 템플릿 안의 이름을 맞춘다.
     *
     * <p>둘은 따로 저장된다. 템플릿 JSON 은 디자이너가 저장한 그대로 두고, 목록 검색에
     * 쓰는 이름은 컬럼으로 뽑아 두었기 때문이다. 관리 화면에서 이름만 고친 경우 컬럼 쪽이
     * 더 최신이므로 그쪽을 따른다.</p>
     */
    private void applyIdentity(ReportTemplate template, ReportDefinition definition) {
        template.setReportId(definition.getReportId());
        template.setName(definition.getName());
        template.setDescription(definition.getDescription());
    }

    /**
     * 정의를 저장한다.
     *
     * <p>편집자를 이름이 아니라 {@link Viewer} 로 받는 이유는, 저장 시점이 <b>어느 DB 로
     * 질의를 보낼지 확정되는 자리</b>이기 때문이다. 여기서 확인하지 않으면 밖의 편집자가
     * 데이터소스 이름만 바꿔 우리 업무 DB 를 읽는 정의를 저장할 수 있다.</p>
     */
    @Transactional
    public ReportDefinition save(String reportId, ReportTemplate template, String category, Viewer editor) {
        template.setReportId(reportId);
        TemplateValidator.validate(template);
        requireDataSourceAccess(template, editor);
        String user = editor.username();

        ReportDefinition definition = repository.findByReportId(reportId)
                .orElseGet(() -> {
                    ReportDefinition created = new ReportDefinition();
                    created.setReportId(reportId);
                    created.setCreatedBy(user);
                    return created;
                });

        definition.setName(template.getName());
        definition.setDescription(template.getDescription());
        if (category != null && !category.isBlank()) {
            definition.setCategory(category);
        }
        definition.setTemplateJson(serialize(template));
        definition.setUpdatedBy(user);

        ReportDefinition saved = repository.save(definition);
        cache.remove(reportId);
        log.info("리포트 정의 저장: {} ({})", reportId, user);
        return saved;
    }

    @Transactional
    public void delete(String reportId) {
        ReportDefinition definition = get(reportId);
        repository.delete(definition);
        // 규칙을 남겨 두면 같은 아이디로 리포트를 다시 만들었을 때
        // 옛 열람 범위가 조용히 되살아난다.
        accessService.revokeAll(reportId);
        cache.remove(reportId);
        log.info("리포트 정의 삭제: {}", reportId);
    }

    @Transactional(readOnly = true)
    public boolean exists(String reportId) {
        return repository.existsByReportId(reportId);
    }

    @Transactional(readOnly = true)
    public int count() {
        return (int) repository.count();
    }

    /**
     * 서명이 확인된 배포 패키지를 등록한다.
     *
     * <p>{@link #save} 와 달리 데이터소스 작성 권한을 보지 않는다. 이 정의는 공급사가
     * 서명한 그대로이고, 설치하는 사람이 내용을 지어낼 수 없기 때문이다. 설치 자체는
     * 관리자만 할 수 있게 경로에서 막는다.</p>
     */
    @Transactional
    public ReportDefinition installPackaged(String reportId, ReportTemplate template, String installer) {
        template.setReportId(reportId);
        TemplateValidator.validate(template);

        ReportDefinition definition = repository.findByReportId(reportId)
                .orElseGet(() -> {
                    ReportDefinition created = new ReportDefinition();
                    created.setReportId(reportId);
                    created.setCreatedBy(installer);
                    return created;
                });
        definition.setName(template.getName());
        definition.setDescription(template.getDescription());
        definition.setTemplateJson(serialize(template));
        definition.setUpdatedBy(installer);

        ReportDefinition saved = repository.save(definition);
        cache.remove(reportId);
        return saved;
    }

    /** 정의가 가리키는 데이터소스를 이 사람이 쓸 수 있는지 */
    public void requireDataSourceAccess(ReportTemplate template, Viewer editor) {
        if (template.getDataSet() == null
                || template.getDataSet().getSourceType() != kr.co.kreport.template.DataSetDef.SourceType.SQL) {
            // 고정 데이터는 DB 를 건드리지 않는다
            return;
        }
        dataSources.requireWrite(template.getDataSet().getDataSource(), editor);
    }

    /** 열람 통제 방식 전환 */
    @Transactional
    public ReportDefinition changeAccessMode(String reportId, AccessMode mode, String actor) {
        ReportDefinition definition = get(reportId);
        AccessMode before = definition.getAccessMode();
        definition.setAccessMode(mode);
        // 통제를 풀고 잠그는 일은 감사 대상이라 반드시 남긴다
        log.info("열람 통제 변경: report={} {} -> {} ({})", reportId, before, mode, actor);
        return definition;
    }

    @Transactional
    public void setActive(String reportId, boolean active) {
        get(reportId).setUseYn(active ? "Y" : "N");
        cache.remove(reportId);
    }

    public ReportTemplate parse(String json) {
        try {
            return mapper.readValue(json, ReportTemplate.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("리포트 정의 JSON 을 해석할 수 없습니다: " + e.getOriginalMessage(), e);
        }
    }

    public String serialize(ReportTemplate template) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(template);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("리포트 정의를 JSON 으로 변환하지 못했습니다.", e);
        }
    }

    public void evictCache(String reportId) {
        cache.remove(reportId);
    }

    /**
     * 저장 버전이 그대로면 캐시를 재사용한다.
     *
     * @param json 파싱하기 전의 원본. 사본을 뜰 때 다시 직렬화하지 않으려고 함께 들고 있는다.
     */
    private record CachedTemplate(Long rowVersion, ReportTemplate template, String json) {

        boolean matches(ReportDefinition definition) {
            return rowVersion != null && rowVersion.equals(definition.getRowVersion());
        }
    }
}
