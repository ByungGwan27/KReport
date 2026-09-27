package kr.co.kreport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class KReportApplication {

    public static void main(String[] args) {
        SpringApplication.run(KReportApplication.class, args);
    }
}
