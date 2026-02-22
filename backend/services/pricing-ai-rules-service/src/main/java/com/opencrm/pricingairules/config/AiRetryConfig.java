package com.opencrm.pricingairules.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;

@Configuration
public class AiRetryConfig {

    @Bean
    public RetryTemplate pricingAiRulesRetryTemplate() {
        return RetryTemplate.builder()
                .maxAttempts(3)
                .fixedBackoff(500)
                .build();
    }
}
