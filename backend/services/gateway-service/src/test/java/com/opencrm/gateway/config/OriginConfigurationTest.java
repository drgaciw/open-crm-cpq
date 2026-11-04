package com.opencrm.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OriginConfigurationTest {
    @Test
    void corsAndCsrfHonorExplicitOriginsThenFrontendUrlThenLocalDefaults() throws Exception {
        var yaml = new YamlPropertySourceLoader().load("application", new FileSystemResource("src/main/resources/application.yml"));
        for (var overrides : java.util.List.of(
                Map.of("CORS_ALLOWED_ORIGINS", "https://allowed.example", "FRONTEND_URL", "https://frontend.example"),
                Map.of("FRONTEND_URL", "https://frontend.example"),
                Map.<String, String>of())) {
            var environment = new StandardEnvironment();
            environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
            environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
            environment.getPropertySources().addFirst(new MapPropertySource("overrides", Map.copyOf(overrides)));
            for (PropertySource<?> source : yaml) environment.getPropertySources().addLast(source);
            String expected = overrides.getOrDefault("CORS_ALLOWED_ORIGINS", overrides.getOrDefault("FRONTEND_URL", "http://localhost:4200,http://127.0.0.1:4200"));
            assertEquals(expected, environment.getProperty("security.csrf.allowed-origins"));
            assertEquals(expected, environment.getProperty("spring.cloud.gateway.server.webflux.globalcors.corsConfigurations[/**].allowedOrigins"));
        }
    }
}
