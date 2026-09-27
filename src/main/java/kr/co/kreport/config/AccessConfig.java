package kr.co.kreport.config;

import kr.co.kreport.access.PropertiesViewerDirectory;
import kr.co.kreport.access.ViewerDirectory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 열람 통제에 필요한 빈.
 *
 * <p>기본 디렉터리를 {@code @Component} 가 아니라 여기서 등록하는 이유가 있다.
 * {@link ConditionalOnMissingBean} 은 자동 구성의 {@code @Bean} 메서드를 위한 것이라
 * 컴포넌트 스캔에서는 판정 순서가 보장되지 않는다. 기관 구현을 올렸을 때 기본 구현이
 * 확실히 물러나게 하려면 이 자리에 두어야 한다.</p>
 */
@Configuration
public class AccessConfig {

    @Bean
    @ConditionalOnMissingBean(ViewerDirectory.class)
    public ViewerDirectory viewerDirectory(KReportProperties properties) {
        return new PropertiesViewerDirectory(properties);
    }
}
