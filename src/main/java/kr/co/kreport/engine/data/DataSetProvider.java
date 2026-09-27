package kr.co.kreport.engine.data;

import kr.co.kreport.template.DataSetDef;

import java.util.Map;

/**
 * 데이터셋 정의와 실행 파라미터로부터 조회 결과를 만든다.
 */
public interface DataSetProvider {

    boolean supports(DataSetDef.SourceType type);

    DataTable fetch(DataSetDef def, Map<String, Object> parameters);
}
