package kr.co.kreport.engine.data;

import kr.co.kreport.template.ParameterDef;
import kr.co.kreport.template.ReportTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 화면에서 문자열로 올라온 조회 조건을 파라미터 정의에 맞는 타입으로 바꾸고 검증한다.
 *
 * <p>정의에 없는 파라미터는 버린다. 클라이언트가 임의 키를 실어 보내도
 * SQL 바인딩 목록에 끼어들 수 없게 하기 위함이다.</p>
 */
public final class ParameterBinder {

    private ParameterBinder() {
    }

    public static Map<String, Object> bind(ReportTemplate template, Map<String, ?> input) {
        Map<String, Object> bound = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();

        for (ParameterDef def : template.getParameters()) {
            Object raw = input == null ? null : input.get(def.getName());
            String text = raw == null ? null : String.valueOf(raw).trim();

            if (text == null || text.isEmpty()) {
                text = def.getDefaultValue();
            }
            if (text == null || text.isBlank()) {
                if (def.isRequired()) {
                    errors.add(label(def) + " 값을 입력하세요.");
                }
                bound.put(def.getName(), null);
                continue;
            }
            try {
                bound.put(def.getName(), convert(def, text));
            } catch (RuntimeException e) {
                errors.add(label(def) + " 값의 형식이 올바르지 않습니다: " + text);
            }
        }

        if (!errors.isEmpty()) {
            throw new ParameterBindingException(errors);
        }
        return bound;
    }

    private static Object convert(ParameterDef def, String text) {
        return switch (def.getDataType()) {
            case NUMBER -> new BigDecimal(text.replace(",", ""));
            case DATE -> LocalDate.parse(text.replace('/', '-').replace('.', '-'));
            case BOOLEAN -> "true".equalsIgnoreCase(text) || "Y".equalsIgnoreCase(text) || "1".equals(text);
            case STRING -> text;
        };
    }

    private static String label(ParameterDef def) {
        return def.getLabel() == null || def.getLabel().isBlank() ? def.getName() : def.getLabel();
    }

}
