package com.opencrm.configuration.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public GroupedOpenApi configurationApi() {
        return GroupedOpenApi.builder()
                .group("configuration")
                .pathsToMatch("/api/v1/**")
                .build();
    }
}
