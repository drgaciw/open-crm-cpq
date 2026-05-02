package com.opencrm.pricing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Pricing Service - Pricing engine with rules and gRPC server.
 *
 * Manages:
 * - Pricing rules with Drools and OpenL Tablets
 * - Pricing products and price resolution via pricebook-service
 * - Rule execution and waterfall calculations
 * - Pricing audit trail
 * - gRPC server for Configuration Engine sync calls
 */
@SpringBootApplication(scanBasePackages = {"com.opencrm.pricing", "com.opencrm.common", "com.opencrm.security", "com.opencrm.rules"})
@EntityScan(basePackages = {"com.opencrm.pricing", "com.opencrm.common"})
@EnableJpaRepositories(basePackages = {"com.opencrm.pricing", "com.opencrm.common"})
@EnableJpaAuditing
@EnableScheduling
public class PricingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PricingServiceApplication.class, args);
    }
}
