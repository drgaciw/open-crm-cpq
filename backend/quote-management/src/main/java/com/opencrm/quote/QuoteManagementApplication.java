package com.opencrm.quote;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EntityScan(basePackages = {"com.opencrm.quote.domain", "com.opencrm.shared.outbox", "com.opencrm.shared.domain"})
@EnableJpaRepositories(basePackages = {"com.opencrm.quote.repository"})
public class QuoteManagementApplication {
    public static void main(String[] args) {
        SpringApplication.run(QuoteManagementApplication.class, args);
    }
}
