package com.opencrm.cpq.docgen;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
@EnableAsync
@EntityScan(basePackages = {"com.opencrm.cpq.docgen", "com.opencrm.cpq.shared"})
@EnableJpaRepositories(basePackages = {"com.opencrm.cpq.docgen", "com.opencrm.cpq.shared"})
public class DocumentGenerationApplication {
    public static void main(String[] args) {
        SpringApplication.run(DocumentGenerationApplication.class, args);
    }
}
