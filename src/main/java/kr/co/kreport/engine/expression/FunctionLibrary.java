package kr.co.kreport.engine.expression;

import java.util.List;

/**
 * 함수 묶음. 엔진을 고치지 않고 표현식 어휘를 늘리는 통로다.
 *
 * <p>기관마다 리포트에 필요한 셈이 다르다. 어떤 곳은 회계연도 계산이, 어떤 곳은 내부
 * 부서코드를 이름으로 바꾸는 일이 필요한데, 그때마다 엔진의 내장 함수 표를 고치면 엔진이
 * 특정 기관의 사정을 알게 되고 다음 납품 때 그 코드를 들어내야 한다. 이 인터페이스를
 * 구현한 클래스를 스프링 빈으로 올려 두기만 하면 기동할 때 함께 등록된다.</p>
 *
 * <pre>{@code
 * @Component
 * class FiscalFunctions implements FunctionLibrary {
 *     public String name() { return "회계연도"; }
 *     public List<ReportFunction> functions() {
 *         return List.of(ReportFunction.of("FISCALYEAR", 1,
 *                 "FISCALYEAR(날짜)", "3월 시작 회계연도", args -> ...));
 *     }
 * }
 * }</pre>
 *
 * <p>내장 함수와 같은 이름은 등록할 수 없다. 같은 표현식이 어느 서버에 올렸느냐에 따라
 * 다른 값을 내면 리포트를 신뢰할 수 없게 되기 때문이다.</p>
 */
public interface FunctionLibrary {

    /** 로그와 오류 문구에 쓰는 묶음 이름 */
    String name();

    List<ReportFunction> functions();
}
