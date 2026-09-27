package kr.co.kreport.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonConfig {

    /**
     * 템플릿 JSON 전용 매퍼.
     *
     * <p>웹 응답용 매퍼와 분리했다. 템플릿은 저장 포맷이라 필드가 추가되어도
     * 구버전 파일을 계속 읽어야 하는 반면, 웹 응답은 그런 하위 호환 요구가 없다.
     * 한쪽 설정을 바꿨다가 다른 쪽이 깨지는 일을 애초에 막는다.</p>
     */
    @Bean
    public ObjectMapper templateObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }
}
