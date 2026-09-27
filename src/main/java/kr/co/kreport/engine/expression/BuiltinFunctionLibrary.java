package kr.co.kreport.engine.expression;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

import static kr.co.kreport.engine.expression.ReportFunction.of;
import static kr.co.kreport.engine.expression.ReportFunction.varargs;

/**
 * 어느 기관에서나 쓰는 기본 함수.
 *
 * <p>여기에 넣을지 말지의 기준은 <b>특정 업무를 모르고도 뜻이 통하는가</b>다. 반올림이나
 * 문자열 자르기는 그렇지만, 회계연도 계산이나 부서코드 변환은 기관마다 규칙이 달라 여기
 * 들어오면 안 된다. 그런 함수는 {@link FunctionLibrary} 를 따로 구현해 올린다.</p>
 */
public final class BuiltinFunctionLibrary implements FunctionLibrary {

    @Override
    public String name() {
        return "내장";
    }

    @Override
    public List<ReportFunction> functions() {
        return List.of(
                // --- 분기 / null 처리
                of("IF", 2, 3, "IF(조건, 참일때, 거짓일때)", "조건에 따라 값을 고른다",
                        args -> Values.toBool(arg(args, 0)) ? arg(args, 1) : arg(args, 2)),
                varargs("NVL", 2, "NVL(값1, 값2, ...)", "비어 있지 않은 첫 값을 돌려준다",
                        BuiltinFunctionLibrary::fnNvl),
                varargs("COALESCE", 2, "COALESCE(값1, 값2, ...)", "NVL 과 같다",
                        BuiltinFunctionLibrary::fnNvl),
                of("ISBLANK", 1, "ISBLANK(값)", "null 이거나 빈 문자열이면 참",
                        args -> Values.isBlank(arg(args, 0))),
                varargs("DECODE", 3, "DECODE(값, 비교1, 결과1, ..., 기본값)", "값을 차례로 견주어 바꾼다",
                        BuiltinFunctionLibrary::fnDecode),

                // --- 문자열
                varargs("CONCAT", 1, "CONCAT(값1, 값2, ...)", "이어 붙인다",
                        BuiltinFunctionLibrary::fnConcat),
                of("UPPER", 1, "UPPER(문자열)", "대문자로",
                        args -> Values.toStr(arg(args, 0)).toUpperCase(Locale.ROOT)),
                of("LOWER", 1, "LOWER(문자열)", "소문자로",
                        args -> Values.toStr(arg(args, 0)).toLowerCase(Locale.ROOT)),
                of("TRIM", 1, "TRIM(문자열)", "앞뒤 공백을 없앤다",
                        args -> Values.toStr(arg(args, 0)).trim()),
                of("LEN", 1, "LEN(문자열)", "글자 수",
                        args -> BigDecimal.valueOf(Values.toStr(arg(args, 0)).length())),
                of("LENGTH", 1, "LENGTH(문자열)", "LEN 과 같다",
                        args -> BigDecimal.valueOf(Values.toStr(arg(args, 0)).length())),
                of("SUBSTR", 2, 3, "SUBSTR(문자열, 시작위치, 길이)", "잘라 낸다. 시작위치는 1부터",
                        BuiltinFunctionLibrary::fnSubstr),
                of("REPLACE", 3, "REPLACE(문자열, 찾을값, 바꿀값)", "바꿔 넣는다",
                        BuiltinFunctionLibrary::fnReplace),
                of("LPAD", 2, 3, "LPAD(문자열, 자릿수, 채울문자)", "왼쪽을 채운다",
                        args -> pad(args, true)),
                of("RPAD", 2, 3, "RPAD(문자열, 자릿수, 채울문자)", "오른쪽을 채운다",
                        args -> pad(args, false)),
                of("MASK", 1, 3, "MASK(값, 앞자리, 뒷자리)", "가운데를 * 로 가린다",
                        BuiltinFunctionLibrary::fnMask),

                // --- 숫자
                of("ABS", 1, "ABS(숫자)", "절댓값",
                        args -> Values.toDecimalOrZero(arg(args, 0)).abs()),
                of("ROUND", 1, 2, "ROUND(숫자, 자릿수)", "반올림",
                        args -> scale(args, RoundingMode.HALF_UP)),
                of("TRUNC", 1, 2, "TRUNC(숫자, 자릿수)", "버림",
                        args -> scale(args, RoundingMode.DOWN)),
                of("FLOOR", 1, "FLOOR(숫자)", "내림",
                        args -> Values.toDecimalOrZero(arg(args, 0)).setScale(0, RoundingMode.FLOOR)),
                of("CEIL", 1, "CEIL(숫자)", "올림",
                        args -> Values.toDecimalOrZero(arg(args, 0)).setScale(0, RoundingMode.CEILING)),
                of("TONUMBER", 1, "TONUMBER(값)", "숫자로 바꾼다",
                        args -> Values.toDecimal(arg(args, 0))),

                // --- 날짜
                of("TODAY", 0, "TODAY()", "오늘 날짜",
                        args -> LocalDate.now()),
                of("NOW", 0, "NOW()", "현재 일시",
                        args -> LocalDateTime.now()),
                of("TODATE", 1, "TODATE(값)", "날짜로 바꾼다",
                        args -> Values.toLocalDate(arg(args, 0))),
                of("YEAR", 1, "YEAR(날짜)", "연도",
                        args -> datePart(args, 'y')),
                of("MONTH", 1, "MONTH(날짜)", "월",
                        args -> datePart(args, 'M')),
                of("DAY", 1, "DAY(날짜)", "일",
                        args -> datePart(args, 'd')),
                of("DATEADD", 2, 3, "DATEADD(날짜, 증감, 'DAY'|'WEEK'|'MONTH'|'YEAR')", "날짜를 더하고 뺀다",
                        BuiltinFunctionLibrary::fnDateAdd),
                of("DATEDIFF", 2, "DATEDIFF(시작일, 종료일)", "두 날짜의 일 수 차이",
                        BuiltinFunctionLibrary::fnDateDiff),

                // --- 포맷
                of("FORMAT", 2, "FORMAT(값, 형식)", "표시 형식을 입힌다",
                        args -> ValueFormatter.format(arg(args, 0), Values.toStr(arg(args, 1)))),
                of("TOSTRING", 1, "TOSTRING(값)", "문자열로 바꾼다",
                        args -> Values.toStr(arg(args, 0))),
                of("KORNUM", 1, "KORNUM(숫자)", "금액을 한글로 적는다 (일금 ...)",
                        args -> ValueFormatter.toKoreanNumber(arg(args, 0)))
        );
    }

    // ---------------------------------------------------------------- 구현
    //
    // 인자 개수는 위의 선언대로 레지스트리가 이미 검사하고 넘긴다.
    // 여기서는 개수를 다시 세지 않고 값의 뜻만 다룬다.

    private static Object arg(List<Object> args, int index) {
        return index < args.size() ? args.get(index) : null;
    }

    private static Object fnNvl(List<Object> args) {
        for (Object a : args) {
            if (!Values.isBlank(a)) {
                return a;
            }
        }
        return args.get(args.size() - 1);
    }

    /** DECODE(값, 비교1, 결과1, 비교2, 결과2, ..., 기본값) */
    private static Object fnDecode(List<Object> args) {
        Object target = args.get(0);
        int i = 1;
        while (i + 1 < args.size()) {
            if (Values.equal(target, args.get(i))) {
                return args.get(i + 1);
            }
            i += 2;
        }
        return i < args.size() ? args.get(i) : null;
    }

    private static Object fnConcat(List<Object> args) {
        StringBuilder sb = new StringBuilder();
        for (Object a : args) {
            sb.append(Values.toStr(a));
        }
        return sb.toString();
    }

    /** SUBSTR(문자열, 시작위치(1부터), 길이) */
    private static Object fnSubstr(List<Object> args) {
        String s = Values.toStr(arg(args, 0));
        int start = Values.toDecimalOrZero(arg(args, 1)).intValue();
        int from = Math.max(0, start - 1);
        if (from >= s.length()) {
            return "";
        }
        if (args.size() < 3 || arg(args, 2) == null) {
            return s.substring(from);
        }
        int len = Values.toDecimalOrZero(arg(args, 2)).intValue();
        return s.substring(from, Math.min(s.length(), from + Math.max(0, len)));
    }

    private static Object fnReplace(List<Object> args) {
        return Values.toStr(arg(args, 0))
                .replace(Values.toStr(arg(args, 1)), Values.toStr(arg(args, 2)));
    }

    private static Object pad(List<Object> args, boolean left) {
        String s = Values.toStr(arg(args, 0));
        int width = Values.toDecimalOrZero(arg(args, 1)).intValue();
        String fill = args.size() > 2 ? Values.toStr(arg(args, 2)) : " ";
        if (fill.isEmpty() || s.length() >= width) {
            return s;
        }
        StringBuilder sb = new StringBuilder();
        while (sb.length() < width - s.length()) {
            sb.append(fill);
        }
        String padding = sb.substring(0, width - s.length());
        return left ? padding + s : s + padding;
    }

    /**
     * MASK(값, 앞자리, 뒷자리) — 가운데를 * 로 가린다.
     * 성명, 연락처처럼 개인정보가 섞인 컬럼을 리포트에서 가릴 때 쓴다.
     */
    private static Object fnMask(List<Object> args) {
        String s = Values.toStr(arg(args, 0));
        if (s.isEmpty()) {
            return s;
        }
        int head = args.size() > 1 ? Values.toDecimalOrZero(arg(args, 1)).intValue() : 1;
        int tail = args.size() > 2 ? Values.toDecimalOrZero(arg(args, 2)).intValue() : 0;
        if (head + tail >= s.length()) {
            return s.charAt(0) + "*".repeat(Math.max(0, s.length() - 1));
        }
        return s.substring(0, head)
                + "*".repeat(s.length() - head - tail)
                + s.substring(s.length() - tail);
    }

    private static Object scale(List<Object> args, RoundingMode mode) {
        BigDecimal d = Values.toDecimal(arg(args, 0));
        if (d == null) {
            return null;
        }
        int scale = args.size() > 1 ? Values.toDecimalOrZero(arg(args, 1)).intValue() : 0;
        return d.setScale(scale, mode);
    }

    private static Object datePart(List<Object> args, char part) {
        LocalDateTime dt = Values.toLocalDateTime(arg(args, 0));
        if (dt == null) {
            return null;
        }
        return switch (part) {
            case 'y' -> BigDecimal.valueOf(dt.getYear());
            case 'M' -> BigDecimal.valueOf(dt.getMonthValue());
            default -> BigDecimal.valueOf(dt.getDayOfMonth());
        };
    }

    /** DATEADD(날짜, 증감, 'DAY'|'MONTH'|'YEAR') */
    private static Object fnDateAdd(List<Object> args) {
        LocalDate date = Values.toLocalDate(arg(args, 0));
        if (date == null) {
            return null;
        }
        long amount = Values.toDecimalOrZero(arg(args, 1)).longValue();
        String unit = args.size() > 2 ? Values.toStr(arg(args, 2)).toUpperCase(Locale.ROOT) : "DAY";
        return switch (unit) {
            case "YEAR" -> date.plusYears(amount);
            case "MONTH" -> date.plusMonths(amount);
            case "WEEK" -> date.plusWeeks(amount);
            default -> date.plusDays(amount);
        };
    }

    /** DATEDIFF(시작일, 종료일) — 일 수 차이 */
    private static Object fnDateDiff(List<Object> args) {
        LocalDate from = Values.toLocalDate(arg(args, 0));
        LocalDate to = Values.toLocalDate(arg(args, 1));
        if (from == null || to == null) {
            return null;
        }
        return BigDecimal.valueOf(ChronoUnit.DAYS.between(from, to));
    }
}
