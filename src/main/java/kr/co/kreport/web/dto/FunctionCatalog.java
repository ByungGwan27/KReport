package kr.co.kreport.web.dto;

import kr.co.kreport.engine.expression.ReportFunction;

import java.util.List;

/**
 * 디자이너 도움말에 뿌리는 표현식 어휘.
 *
 * <p>이름만 주면 편집기에서 인자를 몇 개 넣어야 하는지 알 수 없어 저장 버튼을 눌러 봐야
 * 안다. 호출 형태와 설명을 함께 내보내 그 왕복을 없앤다.</p>
 */
public record FunctionCatalog(
        List<FunctionInfo> functions,
        List<String> aggregates,
        List<String> variables) {

    /** @param signature 예: {@code SUBSTR(문자열, 시작위치, 길이)} */
    public record FunctionInfo(String name, String signature, String description,
                               int minArgs, int maxArgs) {

        public static FunctionInfo from(ReportFunction function) {
            return new FunctionInfo(
                    function.name(),
                    function.signature(),
                    function.description(),
                    function.minArgs(),
                    // 가변 인자의 상한은 Integer.MAX_VALUE 라 그대로 내보내면 화면에 뜻 없는
                    // 숫자가 찍힌다. 제한 없음은 -1 로 적는다.
                    function.maxArgs() == ReportFunction.UNLIMITED ? -1 : function.maxArgs());
        }
    }
}
