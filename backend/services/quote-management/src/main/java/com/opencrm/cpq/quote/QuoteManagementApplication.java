package com.opencrm.cpq.quote;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaAuditing
@EnableScheduling
@EntityScan(basePackages = {"com.opencrm.cpq.quote", "com.opencrm.cpq.shared"})
@EnableJpaRepositories(basePackages = {"com.opencrm.cpq.quote", "com.opencrm.cpq.shared"})
public class QuoteManagementApplication {
    public static void main(String[] args) {
        SpringApplication.run(QuoteManagementApplication.class, args);
    }
}
