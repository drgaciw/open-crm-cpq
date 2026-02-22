package com.opencrm.pricing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Pricing Service - Pricing engine with rules.
 *
 * Manages:
 * - Pricing rules with Drools and OpenL Tablets
 * - Price books and pricing products
 * - Rule execution and waterfall calculations
 * - Pricing audit trail
 */
@SpringBootApplication(scanBasePackages = {"com.opencrm.pricing", "com.opencrm.common", "com.opencrm.security", "com.opencrm.rules"})
@EnableJpaAuditing
public class PricingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PricingServiceApplication.class, args);
    }
}
