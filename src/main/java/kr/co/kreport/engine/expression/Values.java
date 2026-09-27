package kr.co.kreport.engine.expression;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * 표현식 값 변환 규칙을 한곳에 모은 유틸.
 *
 * <p>수치는 모두 {@link BigDecimal} 로 다룬다. 집계 대상이 금액인 경우가 대부분이라
 * double 누적 오차가 결산 리포트에서 그대로 금액 불일치로 드러나기 때문이다.</p>
 */
public final class Values {

    /** 나눗셈 결과의 최대 소수 자릿수 */
    public static final int DIVISION_SCALE = 10;

    private Values() {
    }

    public static boolean isNumber(Object v) {
        return v instanceof Number || v instanceof BigDecimal;
    }

    public static BigDecimal toDecimal(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof BigDecimal d) {
            return d;
        }
        if (v instanceof Integer i) {
            return BigDecimal.valueOf(i);
        }
        if (v instanceof Long l) {
            return BigDecimal.valueOf(l);
        }
        if (v instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        if (v instanceof Boolean b) {
            return b ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        String s = v.toString().trim().replace(",", "");
        if (s.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 산술 연산용. null 과 숫자가 아닌 값은 0으로 본다. */
    public static BigDecimal toDecimalOrZero(Object v) {
        BigDecimal d = toDecimal(v);
        return d == null ? BigDecimal.ZERO : d;
    }

    public static String toStr(Object v) {
        if (v == null) {
            return "";
        }
        if (v instanceof BigDecimal d) {
            return d.stripTrailingZeros().toPlainString();
        }
        if (v instanceof Double || v instanceof Float) {
            return toDecimal(v).stripTrailingZeros().toPlainString();
        }
        return v.toString();
    }

    /**
     * 조건식 판정. null/false/0/빈문자열을 거짓으로 본다.
     */
    public static boolean toBool(Object v) {
        if (v == null) {
            return false;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        if (isNumber(v)) {
            return toDecimalOrZero(v).signum() != 0;
        }
        String s = v.toString().trim();
        if (s.isEmpty() || "false".equalsIgnoreCase(s) || "0".equals(s) || "N".equalsIgnoreCase(s)) {
            return false;
        }
        return true;
    }

    public static LocalDate toLocalDate(Object v) {
        LocalDateTime dt = toLocalDateTime(v);
        return dt == null ? null : dt.toLocalDate();
    }

    public static LocalDateTime toLocalDateTime(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof LocalDateTime dt) {
            return dt;
        }
        if (v instanceof LocalDate d) {
            return d.atStartOfDay();
        }
        if (v instanceof Timestamp ts) {
            return ts.toLocalDateTime();
        }
        if (v instanceof java.sql.Date sd) {
            return sd.toLocalDate().atStartOfDay();
        }
        if (v instanceof Date d) {
            return LocalDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault());
        }
        String s = v.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            if (s.length() <= 10) {
                return LocalDate.parse(s.replace('/', '-')).atStartOfDay();
            }
            return LocalDateTime.parse(s.replace(' ', 'T'));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 두 값을 비교한다. 양쪽 모두 숫자로 읽히면 수치 비교, 아니면 문자열 비교.
     */
    @SuppressWarnings("unchecked")
    public static int compare(Object a, Object b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return -1;
        }
        if (b == null) {
            return 1;
        }
        BigDecimal da = toDecimal(a);
        BigDecimal db = toDecimal(b);
        if (da != null && db != null) {
            return da.compareTo(db);
        }
        if (a instanceof Comparable && a.getClass() == b.getClass()) {
            return ((Comparable<Object>) a).compareTo(b);
        }
        return toStr(a).compareTo(toStr(b));
    }

    public static boolean equal(Object a, Object b) {
        if (a == null || b == null) {
            return a == b;
        }
        return compare(a, b) == 0;
    }

    public static BigDecimal divide(BigDecimal a, BigDecimal b) {
        if (b == null || b.signum() == 0) {
            return null;
        }
        return a.divide(b, DIVISION_SCALE, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    /** 표현식 결과가 비어 있는지 (null 또는 공백 문자열) */
    public static boolean isBlank(Object v) {
        return v == null || v.toString().isBlank();
    }
}
