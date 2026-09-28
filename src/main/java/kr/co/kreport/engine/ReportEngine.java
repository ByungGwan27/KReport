package kr.co.kreport.engine;

import kr.co.kreport.engine.data.DataSetProvider;
import kr.co.kreport.engine.data.DataSetException;
import kr.co.kreport.engine.data.DataTable;
import kr.co.kreport.engine.data.ParameterBinder;
import kr.co.kreport.engine.layout.RenderedReport;
import kr.co.kreport.engine.layout.ReportLayoutEngine;
import kr.co.kreport.template.DataSetDef;
import kr.co.kreport.template.ReportTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 템플릿 + 조회 조건을 받아 출력 가능한 결과까지 만드는 진입점.
 */
@Slf4j
@Service
public class ReportEngine {

    private final List<DataSetProvider> providers;
    private final ReportLayoutEngine layoutEngine;

    public ReportEngine(List<DataSetProvider> providers, ReportLayoutEngine layoutEngine) {
        this.providers = providers;
        this.layoutEngine = layoutEngine;
    }

    /**
     * @param input 화면에서 들어온 조회 조건 (문자열 형태 허용)
     */
    public RenderedReport run(ReportTemplate template, Map<String, ?> input) {
        TemplateValidator.validate(template);

        Map<String, Object> parameters = ParameterBinder.bind(template, input);
        DataTable data = fetch(template.getDataSet(), parameters);

        return layoutEngine.layout(template, data, parameters);
    }

    /**
     * 데이터를 읽어 온다. 읽을 것이 정해지지 않았으면 빈 표를 돌려준다.
     *
     * <p>조회를 붙이기 전에 배치부터 잡는 순서를 막지 않기 위해서다. 빈 표로 흘리면
     * 머리말과 꼬리말은 그대로 그려지고 본문만 비어, 종이 위 모양을 확인할 수 있다.</p>
     */
    private DataTable fetch(DataSetDef dataSet, Map<String, Object> parameters) {
        if (dataSet == null || isUnset(dataSet)) {
            return DataTable.empty();
        }
        DataSetDef.SourceType type = dataSet.getSourceType();
        return providers.stream()
                .filter(p -> p.supports(type))
                .findFirst()
                .orElseThrow(() -> new DataSetException("지원하지 않는 데이터 원본입니다: " + type))
                .fetch(dataSet, parameters);
    }

    /** SQL 을 아직 적지 않았거나, 직접 입력인데 행이 하나도 없는 상태 */
    private boolean isUnset(DataSetDef dataSet) {
        return dataSet.getSourceType() == DataSetDef.SourceType.SQL
                && (dataSet.getSql() == null || dataSet.getSql().isBlank());
    }
}
