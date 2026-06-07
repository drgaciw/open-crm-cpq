package com.opencrm.pricing.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public GroupedOpenApi pricingApi() {
        return GroupedOpenApi.builder()
                .group("pricing")
                .pathsToMatch("/api/v1/**")
                .build();
    }
}
