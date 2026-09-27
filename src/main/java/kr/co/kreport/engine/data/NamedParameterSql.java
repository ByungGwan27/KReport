package kr.co.kreport.engine.data;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code :파라미터명} 형태의 이름 바인딩을 JDBC 의 {@code ?} 로 바꾸고
 * 등장 순서를 기록한다.
 *
 * <p>값을 SQL 문자열에 끼워 넣지 않는다는 점이 핵심이다. 리포트 도구는
 * 업무 담당자가 조회 조건을 직접 넣는 구조라서, 문자열 치환 방식이면
 * 조건 입력란이 그대로 SQL 주입 통로가 된다.</p>
 *
 * <p>문자열 리터럴, 한 줄 주석, 블록 주석, PostgreSQL 캐스트 연산자({@code ::})
 * 안쪽은 파라미터로 보지 않는다.</p>
 */
public record NamedParameterSql(String sql, List<String> parameterNames) {

    public static NamedParameterSql parse(String source) {
        StringBuilder out = new StringBuilder(source.length());
        List<String> names = new ArrayList<>();

        int i = 0;
        int len = source.length();
        while (i < len) {
            char c = source.charAt(i);

            // 작은따옴표 문자열
            if (c == '\'') {
                int end = findQuoteEnd(source, i, '\'');
                out.append(source, i, end);
                i = end;
                continue;
            }
            // 식별자 인용부호
            if (c == '"') {
                int end = findQuoteEnd(source, i, '"');
                out.append(source, i, end);
                i = end;
                continue;
            }
            // 한 줄 주석
            if (c == '-' && i + 1 < len && source.charAt(i + 1) == '-') {
                int end = source.indexOf('\n', i);
                end = end < 0 ? len : end + 1;
                out.append(source, i, end);
                i = end;
                continue;
            }
            // 블록 주석
            if (c == '/' && i + 1 < len && source.charAt(i + 1) == '*') {
                int end = source.indexOf("*/", i + 2);
                end = end < 0 ? len : end + 2;
                out.append(source, i, end);
                i = end;
                continue;
            }
            // PostgreSQL 캐스트 :: 는 그대로 둔다
            if (c == ':' && i + 1 < len && source.charAt(i + 1) == ':') {
                out.append("::");
                i += 2;
                continue;
            }
            // 이름 바인딩
            if (c == ':' && i + 1 < len && isNameStart(source.charAt(i + 1))) {
                int j = i + 1;
                while (j < len && isNamePart(source.charAt(j))) {
                    j++;
                }
                names.add(source.substring(i + 1, j));
                out.append('?');
                i = j;
                continue;
            }

            out.append(c);
            i++;
        }
        return new NamedParameterSql(out.toString(), List.copyOf(names));
    }

    /** 여는 따옴표 위치에서 시작해 닫는 따옴표 다음 위치를 돌려준다 */
    private static int findQuoteEnd(String s, int start, char quote) {
        int i = start + 1;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == quote) {
                if (i + 1 < s.length() && s.charAt(i + 1) == quote) {
                    i += 2;
                    continue;
                }
                return i + 1;
            }
            i++;
        }
        return s.length();
    }

    private static boolean isNameStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isNamePart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
