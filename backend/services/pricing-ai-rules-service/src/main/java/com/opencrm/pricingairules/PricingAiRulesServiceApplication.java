package com.opencrm.pricingairules;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication(scanBasePackages = {
        "com.opencrm.pricingairules",
        "com.opencrm.common",
        "com.opencrm.security",
        "com.opencrm.rules"
})
@EnableJpaAuditing
@EnableRetry
public class PricingAiRulesServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PricingAiRulesServiceApplication.class, args);
    }
}
