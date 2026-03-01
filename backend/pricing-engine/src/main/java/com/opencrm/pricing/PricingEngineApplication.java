package com.opencrm.pricing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EntityScan(basePackages = {"com.opencrm.pricing.domain", "com.opencrm.shared.outbox"})
@EnableJpaRepositories(basePackages = {"com.opencrm.pricing.repository"})
public class PricingEngineApplication {
    public static void main(String[] args) {
        SpringApplication.run(PricingEngineApplication.class, args);
    }
}
