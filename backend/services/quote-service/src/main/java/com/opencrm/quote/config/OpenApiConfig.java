package com.opencrm.quote.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public GroupedOpenApi quoteApi() {
        return GroupedOpenApi.builder()
                .group("quote")
                .pathsToMatch("/api/v1/**")
                .build();
    }
}
