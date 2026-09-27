package kr.co.kreport.engine.data;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 데이터셋 SQL 이 조회문인지 검사한다.
 *
 * <p>리포트 정의는 운영 중에 화면에서 편집되는 자산이다. 편집 권한을 가진 사람이
 * 실수로든 고의로든 DML/DDL 을 넣는 경로를 막아 둔다.
 * PreparedStatement 바인딩과 별개로 필요한, 권한 오남용에 대한 2차 방어선이다.</p>
 */
public final class SqlGuard {

    /** 구문 선두에 올 수 있는 키워드 */
    private static final List<String> ALLOWED_PREFIX = List.of("SELECT", "WITH");

    /** 조회문 어디에도 나와서는 안 되는 키워드 */
    private static final List<String> FORBIDDEN = List.of(
            "INSERT", "UPDATE", "DELETE", "MERGE", "TRUNCATE",
            "DROP", "ALTER", "CREATE", "GRANT", "REVOKE",
            "EXEC", "EXECUTE", "CALL", "SHUTDOWN"
    );

    /** 주석 제거용 */
    private static final Pattern LINE_COMMENT = Pattern.compile("--[^\\n]*");
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern STRING_LITERAL = Pattern.compile("'(?:[^']|'')*'");

    private SqlGuard() {
    }

    /**
     * @throws IllegalArgumentException 조회문이 아니거나 금지 키워드가 섞여 있을 때
     */
    public static void verifySelect(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("데이터셋 SQL 이 비어 있습니다.");
        }

        String stripped = strip(sql);
        String upper = stripped.toUpperCase(Locale.ROOT).trim();

        boolean allowed = ALLOWED_PREFIX.stream().anyMatch(p -> upper.startsWith(p + " ") || upper.startsWith(p + "("));
        if (!allowed) {
            throw new IllegalArgumentException("데이터셋 SQL 은 SELECT 또는 WITH 로 시작해야 합니다.");
        }

        // 세미콜론 뒤에 다른 구문이 붙는 다중 실행 차단
        int semicolon = stripped.indexOf(';');
        if (semicolon >= 0 && !stripped.substring(semicolon + 1).isBlank()) {
            throw new IllegalArgumentException("데이터셋 SQL 에는 구문을 하나만 작성할 수 있습니다.");
        }

        for (String keyword : FORBIDDEN) {
            if (containsWord(upper, keyword)) {
                throw new IllegalArgumentException("데이터셋 SQL 에 사용할 수 없는 키워드가 있습니다: " + keyword);
            }
        }
    }

    /** 검사 대상에서 문자열 리터럴과 주석을 들어낸다 */
    private static String strip(String sql) {
        String s = BLOCK_COMMENT.matcher(sql).replaceAll(" ");
        s = LINE_COMMENT.matcher(s).replaceAll(" ");
        s = STRING_LITERAL.matcher(s).replaceAll("''");
        return s;
    }

    /** 단어 경계를 지켜 검사한다. CREATED_AT 같은 컬럼명이 CREATE 로 걸리지 않도록. */
    private static boolean containsWord(String text, String word) {
        int from = 0;
        while (true) {
            int at = text.indexOf(word, from);
            if (at < 0) {
                return false;
            }
            boolean leftOk = at == 0 || !isWordChar(text.charAt(at - 1));
            int after = at + word.length();
            boolean rightOk = after >= text.length() || !isWordChar(text.charAt(after));
            if (leftOk && rightOk) {
                return true;
            }
            from = at + 1;
        }
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
