package kr.co.kreport.template;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 리포트 한 건의 전체 정의. JSON 으로 직렬화되어 저장된다.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReportTemplate {

    /** 템플릿 포맷 버전. 하위 호환 판단에 쓴다. */
    private int schemaVersion = 1;

    private String reportId;
    private String name;
    private String description;

    private PageSetup page = new PageSetup();

    private DataSetDef dataSet = new DataSetDef();

    private List<ParameterDef> parameters = new ArrayList<>();

    private List<GroupDef> groups = new ArrayList<>();

    private List<Band> bands = new ArrayList<>();

    /** 지정한 종류의 첫 밴드 */
    @JsonIgnore
    public Optional<Band> band(BandType type) {
        return bands.stream().filter(b -> b.getType() == type).findFirst();
    }

    /** 지정한 그룹의 헤더/푸터 밴드 */
    @JsonIgnore
    public Optional<Band> groupBand(BandType type, String groupName) {
        return bands.stream()
                .filter(b -> b.getType() == type && groupName.equals(b.getGroupName()))
                .findFirst();
    }

    @JsonIgnore
    public Optional<GroupDef> group(String name) {
        return groups.stream().filter(g -> name.equals(g.getName())).findFirst();
    }

    @JsonIgnore
    public Optional<ParameterDef> parameter(String name) {
        return parameters.stream().filter(p -> name.equals(p.getName())).findFirst();
    }
}
