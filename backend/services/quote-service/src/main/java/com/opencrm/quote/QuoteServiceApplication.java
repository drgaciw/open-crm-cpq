package com.opencrm.quote;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.opencrm.quote", "com.opencrm.common", "com.opencrm.security"})
@EntityScan(basePackages = {"com.opencrm.quote", "com.opencrm.common"})
@EnableJpaRepositories(basePackages = {"com.opencrm.quote", "com.opencrm.common"})
@EnableJpaAuditing
@EnableScheduling
public class QuoteServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(QuoteServiceApplication.class, args);
    }
}
