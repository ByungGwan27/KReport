package kr.co.kreport.engine.expression;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 값 + 포맷 패턴을 출력 문자열로 바꾼다.
 *
 * <p>패턴에 날짜 문자(y, M, d, H, m, s)가 들어 있으면 날짜 포맷으로,
 * 아니면 숫자 포맷으로 해석한다. 패턴이 없으면 값의 기본 표기를 쓴다.</p>
 */
public final class ValueFormatter {

    private static final Map<String, DecimalFormat> NUMBER_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, DateTimeFormatter> DATE_CACHE = new ConcurrentHashMap<>();

    private ValueFormatter() {
    }

    public static String format(Object value, String pattern) {
        if (value == null) {
            return "";
        }
        if (pattern == null || pattern.isBlank()) {
            return Values.toStr(value);
        }
        if (isDatePattern(pattern)) {
            LocalDateTime dt = Values.toLocalDateTime(value);
            if (dt == null) {
                return Values.toStr(value);
            }
            return dateFormatter(pattern).format(dt);
        }
        BigDecimal number = Values.toDecimal(value);
        if (number == null) {
            return Values.toStr(value);
        }
        return numberFormat(pattern).format(number);
    }

    /**
     * 날짜 패턴 판별. 숫자 패턴에 쓰이는 기호(#, 0, %)가 하나라도 있으면
     * 숫자 패턴으로 확정하고, 그렇지 않은 상태에서 날짜 문자가 보이면 날짜로 본다.
     */
    private static boolean isDatePattern(String pattern) {
        boolean hasDateChar = false;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '#' || c == '0' || c == '%') {
                return false;
            }
            if (c == 'y' || c == 'M' || c == 'd' || c == 'H' || c == 'm' || c == 's' || c == 'E') {
                hasDateChar = true;
            }
        }
        return hasDateChar;
    }

    private static DecimalFormat numberFormat(String pattern) {
        return NUMBER_CACHE.computeIfAbsent(pattern, p -> {
            DecimalFormat df = new DecimalFormat(p);
            df.setRoundingMode(RoundingMode.HALF_UP);
            return df;
        });
    }

    private static DateTimeFormatter dateFormatter(String pattern) {
        return DATE_CACHE.computeIfAbsent(pattern, DateTimeFormatter::ofPattern);
    }

    // ---------------------------------------------------------------- 한글 금액

    private static final String[] DIGITS = {"", "일", "이", "삼", "사", "오", "육", "칠", "팔", "구"};
    private static final String[] SMALL_UNITS = {"", "십", "백", "천"};
    private static final String[] LARGE_UNITS = {"", "만", "억", "조", "경"};

    /**
     * 숫자를 한글 금액 표기로 바꾼다. 계약서, 지출결의서 등에서 위변조 방지 목적으로 쓴다.
     *
     * <pre>
     * 12034000 -> 일천이백삼만사천
     * 15       -> 일십오
     * </pre>
     *
     * <p>일상 표기에서는 "십오"처럼 앞의 "일"을 빼지만, 금액 표기에서는 빼지 않는다.
     * 빈자리를 남기면 앞에 숫자를 덧붙여 고칠 수 있기 때문이고, 이것이 금액을 한글로
     * 병기하는 이유 그 자체다.</p>
     *
     * <p>소수부는 버리며 음수는 앞에 "마이너스"를 붙인다.</p>
     */
    public static String toKoreanNumber(Object value) {
        BigDecimal decimal = Values.toDecimal(value);
        if (decimal == null) {
            return "";
        }
        boolean negative = decimal.signum() < 0;
        java.math.BigInteger amount = decimal.abs().setScale(0, RoundingMode.DOWN).toBigInteger();
        if (amount.signum() == 0) {
            return "영";
        }

        String digits = amount.toString();
        StringBuilder result = new StringBuilder();

        // 4자리씩 끊어 큰 단위(만, 억, 조)를 붙인다
        int groupCount = (digits.length() + 3) / 4;
        for (int g = 0; g < groupCount; g++) {
            int end = digits.length() - g * 4;
            int start = Math.max(0, end - 4);
            String group = digits.substring(start, end);
            String read = readGroup(group);
            if (!read.isEmpty()) {
                result.insert(0, read + LARGE_UNITS[Math.min(g, LARGE_UNITS.length - 1)]);
            }
        }
        return (negative ? "마이너스" : "") + result;
    }

    /** 4자리 이하 묶음을 읽는다. 자릿수 1도 "일"을 적어 빈자리를 남기지 않는다. */
    private static String readGroup(String group) {
        StringBuilder sb = new StringBuilder();
        int len = group.length();
        for (int i = 0; i < len; i++) {
            int digit = group.charAt(i) - '0';
            if (digit == 0) {
                continue;
            }
            sb.append(DIGITS[digit]).append(SMALL_UNITS[len - i - 1]);
        }
        return sb.toString();
    }
}
