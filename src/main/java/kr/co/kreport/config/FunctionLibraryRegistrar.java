package kr.co.kreport.config;

import kr.co.kreport.engine.expression.FunctionLibrary;
import kr.co.kreport.engine.expression.FunctionRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 스프링 빈으로 올라온 {@link FunctionLibrary} 를 표현식 엔진에 등록한다.
 *
 * <p>등록은 생성자에서 끝내고, 잠그는 일만 기동 완료 뒤로 미룬다. 리포트 정의를 미리
 * 읽어들이는 초기화 코드가 표현식을 검사할 때 이미 모든 함수가 있어야 하기 때문이다.
 * 잠근 뒤로는 어휘가 바뀌지 않으므로, 같은 템플릿이 언제 실행되든 같은 값을 낸다.</p>
 */
@Slf4j
@Component
public class FunctionLibraryRegistrar {

    public FunctionLibraryRegistrar(List<FunctionLibrary> libraries) {
        for (FunctionLibrary library : libraries) {
            FunctionRegistry.register(library);
            log.info("표현식 함수 묶음 등록: {} ({}개)", library.name(), library.functions().size());
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void lock() {
        FunctionRegistry.freeze();
        log.info("표현식 함수 {}개 확정", FunctionRegistry.names().size());
    }
}
