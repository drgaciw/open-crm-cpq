package com.opencrm.gateway.config;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("unit")
class CorsConfigurationTest {

    @Test
    void applicationYamlUsesFrontendUrlNotAzureWildcard() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertTrue(yaml.contains("${FRONTEND_URL"),
                "CORS/CSRF must use FRONTEND_URL env var for deployed frontend origin");
        assertFalse(yaml.contains("*.azurewebsites.net"),
                "Shared-host azurewebsites.net wildcard must not appear in gateway config");
    }

    @Test
    void applicationYamlRequiresFrontendUrlInStagingAndProductionProfiles() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertTrue(yaml.contains("on-profile: staging | production"),
                "Staging/production profile must restrict origins to FRONTEND_URL");
        assertTrue(yaml.contains("- \"${FRONTEND_URL}\""),
                "Staging/production profile must require FRONTEND_URL without empty default");
    }
}
