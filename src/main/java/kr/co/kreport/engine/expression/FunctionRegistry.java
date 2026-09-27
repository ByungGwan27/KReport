package kr.co.kreport.engine.expression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 표현식이 부를 수 있는 함수의 전체 목록.
 *
 * <p>여기에 등록된 함수만 호출된다. 리플렉션으로 임의의 메서드를 부르는 통로가 없으므로,
 * 템플릿을 편집할 수 있는 사용자가 서버 코드를 실행할 방법이 없다.</p>
 *
 * <h2>왜 정적이면서도 안전한가</h2>
 * <p>표현식 평가는 레이아웃 루프 한가운데에서 수만 번 불린다. 그 자리에 의존성을 들고
 * 다니게 만들면 호출부가 전부 지저분해져서, 조회표는 정적으로 둔다. 대신 <b>기동이
 * 끝나면 잠근다</b>({@link #freeze()}). 운영 중에 어휘가 바뀌면 같은 리포트가 시각에
 * 따라 다른 결과를 내게 되는데, 그건 결재 문서를 뽑는 도구가 해서는 안 되는 일이다.
 * 잠근 뒤의 등록 시도는 조용히 무시하지 않고 예외로 알린다.</p>
 */
public final class FunctionRegistry {

    private static final Map<String, ReportFunction> REGISTRY = new LinkedHashMap<>();
    private static final Map<String, String> ORIGIN = new LinkedHashMap<>();
    private static final AtomicBoolean FROZEN = new AtomicBoolean(false);

    static {
        install(new BuiltinFunctionLibrary());
    }

    private FunctionRegistry() {
    }

    /**
     * 함수 묶음을 등록한다.
     *
     * @throws IllegalStateException    이미 잠긴 뒤에 불린 경우
     * @throws IllegalArgumentException 이미 있는 이름을 다시 등록한 경우
     */
    public static synchronized void register(FunctionLibrary library) {
        if (FROZEN.get()) {
            throw new IllegalStateException(
                    "함수 목록이 이미 확정되었습니다. 기동 중에만 등록할 수 있습니다: " + library.name());
        }
        install(library);
    }

    private static void install(FunctionLibrary library) {
        for (ReportFunction function : library.functions()) {
            String key = function.name();
            String previous = ORIGIN.get(key);
            if (previous != null) {
                throw new IllegalArgumentException("함수 이름이 겹칩니다: " + key
                        + " ('" + previous + "' 에 이미 있습니다. '" + library.name() + "' 의 이름을 바꾸세요)");
            }
            REGISTRY.put(key, function);
            ORIGIN.put(key, library.name());
        }
    }

    /** 기동이 끝났음을 알리고 목록을 잠근다. 두 번 불러도 탈이 없다. */
    public static void freeze() {
        FROZEN.set(true);
    }

    public static boolean isFrozen() {
        return FROZEN.get();
    }

    public static boolean exists(String name) {
        return REGISTRY.containsKey(upper(name));
    }

    public static Object call(String name, List<Object> args) {
        ReportFunction function = REGISTRY.get(upper(name));
        if (function == null) {
            throw new ExpressionException("알 수 없는 함수입니다", name + "(...)");
        }
        return function.invoke(args);
    }

    /** 등록된 함수 이름. 디자이너의 자동완성 목록에 쓴다. */
    public static List<String> names() {
        return List.copyOf(REGISTRY.keySet());
    }

    /** 이름과 설명까지 담은 목록. 디자이너 도움말에 쓴다. */
    public static List<ReportFunction> descriptors() {
        return List.copyOf(REGISTRY.values());
    }

    /** 어떤 묶음에서 온 함수인지. 이름이 겹쳤을 때 원인을 짚는 데 쓴다. */
    public static Map<String, String> origins() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(ORIGIN));
    }

    /**
     * 내장 함수만 남기고 되돌린다. 테스트 전용.
     *
     * <p>테스트마다 다른 함수 묶음을 올려 보려면 잠금을 풀 수단이 있어야 한다.
     * 운영 코드에서 부를 일은 없다.</p>
     */
    static synchronized void resetForTest() {
        REGISTRY.clear();
        ORIGIN.clear();
        FROZEN.set(false);
        install(new BuiltinFunctionLibrary());
    }

    /** 테스트에서 임시 묶음을 올렸다 내리기 위한 통로 */
    static synchronized List<String> registeredLibraries() {
        return new ArrayList<>(ORIGIN.values().stream().distinct().toList());
    }

    private static String upper(String name) {
        return name == null ? "" : name.toUpperCase(Locale.ROOT);
    }
}
