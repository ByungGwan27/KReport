package kr.co.kreport.engine.expression;

import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;

/**
 * 표현식 함수 호출 창구.
 *
 * <p>실제 목록은 {@link FunctionRegistry} 가 들고 있다. 이 클래스는 평가기가 부르는
 * 짧은 이름을 유지하려고 남겨 둔 얇은 껍데기다. 함수를 새로 넣으려면 여기가 아니라
 * {@link FunctionLibrary} 를 구현한다.</p>
 */
public final class Functions {

    /**
     * @deprecated {@link ReportFunction.Body} 를 쓴다. 인자 개수 검사를 선언으로 옮기기 위해 갈라졌다.
     */
    @Deprecated(forRemoval = true)
    @FunctionalInterface
    public interface Fn {
        Object apply(List<Object> args);
    }

    private Functions() {
    }

    public static boolean exists(String name) {
        return FunctionRegistry.exists(name);
    }

    public static Object call(String name, List<Object> args) {
        return FunctionRegistry.call(name, args);
    }

    public static Set<String> names() {
        return new LinkedHashSet<>(FunctionRegistry.names());
    }
}
