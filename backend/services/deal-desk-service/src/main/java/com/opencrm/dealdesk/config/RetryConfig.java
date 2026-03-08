package com.opencrm.dealdesk.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(SalesforceProperties.class)
public class RetryConfig {

    @Bean
    public RetryTemplate salesforceRetryTemplate(SalesforceProperties salesforceProperties) {
        int maxAttempts = Math.max(1, salesforceProperties.getRetry().getMaxAttempts());
        long backoffMs = Math.max(0L, salesforceProperties.getRetry().getBackoffMs());
        return RetryTemplate.builder()
                .maxAttempts(maxAttempts)
                .fixedBackoff(backoffMs)
                .build();
    }

    @Bean
    public RestClient salesforceRestClient(RestClient.Builder builder, SalesforceProperties salesforceProperties) {
        String baseUrl = salesforceProperties.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return builder.build();
        }
        return builder.baseUrl(baseUrl).build();
    }
}
