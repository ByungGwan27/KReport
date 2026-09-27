package kr.co.kreport.engine.expression;

import java.util.List;

/**
 * 표현식에서 부를 수 있는 함수 하나.
 *
 * <p>구현체를 익명 클래스로 만들지 않고 이 레코드로 감싸는 이유는 <b>인자 개수 검사와
 * 도움말을 선언으로 옮기기 위해서</b>다. 예전에는 함수마다 첫 줄에서 인자 수를 직접
 * 세어 예외를 던졌는데, 같은 검사가 열 군데 흩어지면 새 함수를 만들 때 그 줄을 빠뜨리기
 * 쉽다. 여기에 {@code minArgs}/{@code maxArgs} 를 적어 두면 호출 직전에 레지스트리가
 * 한 곳에서 검사하고, 같은 선언이 디자이너의 함수 도움말로도 그대로 나간다.</p>
 *
 * @param name        대문자 함수 이름
 * @param minArgs     최소 인자 수
 * @param maxArgs     최대 인자 수. 제한이 없으면 {@link #UNLIMITED}
 * @param signature   도움말에 보일 호출 형태. 예: {@code SUBSTR(문자열, 시작위치, 길이)}
 * @param description 한 줄 설명
 * @param body        실제 계산
 */
public record ReportFunction(
        String name,
        int minArgs,
        int maxArgs,
        String signature,
        String description,
        Body body) {

    public static final int UNLIMITED = Integer.MAX_VALUE;

    @FunctionalInterface
    public interface Body {
        Object apply(List<Object> args);
    }

    public ReportFunction {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("함수 이름이 없습니다.");
        }
        if (minArgs < 0 || maxArgs < minArgs) {
            throw new IllegalArgumentException("함수 " + name + " 의 인자 수 범위가 잘못되었습니다.");
        }
        name = name.toUpperCase(java.util.Locale.ROOT);
    }

    /** 인자 개수가 고정인 함수 */
    public static ReportFunction of(String name, int args, String signature, String description, Body body) {
        return new ReportFunction(name, args, args, signature, description, body);
    }

    /** 인자 개수에 범위가 있는 함수 */
    public static ReportFunction of(String name, int minArgs, int maxArgs,
                                    String signature, String description, Body body) {
        return new ReportFunction(name, minArgs, maxArgs, signature, description, body);
    }

    /** 인자를 몇 개든 받는 함수 */
    public static ReportFunction varargs(String name, int minArgs,
                                         String signature, String description, Body body) {
        return new ReportFunction(name, minArgs, UNLIMITED, signature, description, body);
    }

    Object invoke(List<Object> args) {
        int given = args.size();
        if (given < minArgs || given > maxArgs) {
            throw new ExpressionException(name + " 함수의 인자 수가 맞지 않습니다. "
                    + arityText() + " 필요한데 " + given + "개가 왔습니다. " + signature, name);
        }
        return body.apply(args);
    }

    private String arityText() {
        if (maxArgs == UNLIMITED) {
            return minArgs + "개 이상이";
        }
        return minArgs == maxArgs ? minArgs + "개가" : minArgs + "~" + maxArgs + "개가";
    }
}
