package kr.co.kreport.engine.data;

import kr.co.kreport.template.DataSetDef;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 템플릿에 포함된 고정 데이터를 그대로 돌려준다.
 * DB 연결 없이 디자이너에서 배치를 확인할 때 쓴다.
 */
@Component
public class StaticDataSetProvider implements DataSetProvider {

    @Override
    public boolean supports(DataSetDef.SourceType type) {
        return type == DataSetDef.SourceType.STATIC;
    }

    @Override
    public DataTable fetch(DataSetDef def, Map<String, Object> parameters) {
        List<Map<String, Object>> rows = new ArrayList<>();
        LinkedHashSet<String> columns = new LinkedHashSet<>();

        for (DataSetDef.Column column : def.getColumns()) {
            if (column.getName() != null) {
                columns.add(column.getName());
            }
        }
        for (Map<String, Object> source : def.getRows()) {
            columns.addAll(source.keySet());
            rows.add(new LinkedHashMap<>(source));
        }
        return new DataTable(new ArrayList<>(columns), rows);
    }
}
