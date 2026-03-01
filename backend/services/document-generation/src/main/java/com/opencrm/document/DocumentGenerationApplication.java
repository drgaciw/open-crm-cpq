package com.opencrm.document;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = {"com.opencrm.document", "com.opencrm.common"})
@EntityScan(basePackages = {"com.opencrm.document", "com.opencrm.common"})
@EnableJpaAuditing
@EnableKafka
@EnableAsync
public class DocumentGenerationApplication {
    public static void main(String[] args) {
        SpringApplication.run(DocumentGenerationApplication.class, args);
    }
}
