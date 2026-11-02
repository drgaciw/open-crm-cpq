package com.opencrm.crm.guided;

import com.opencrm.security.client.OutboundAuth;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Builds the configuration-session client. User-initiated calls to {@code /api/v1/configurations}
 * relay the caller's token (ADR-016 decision 5). A blank base URL leaves the starter local.
 */
@Configuration
public class GuidedSellingConfiguration {

    @Bean
    public ConfigurationSessionStarter configurationSessionStarter(
            @Value("${opencrm.configuration.base-url:}") String baseUrl,
            OutboundAuth outboundAuth) {
        String trimmed = baseUrl == null ? "" : baseUrl.trim();
        if (trimmed.isEmpty()) {
            return new HttpConfigurationSessionStarter("", null);
        }
        String root = trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
        RestClient client = RestClient.builder()
                .baseUrl(root)
                .requestInterceptor(outboundAuth.relayUserToken())
                .build();
        return new HttpConfigurationSessionStarter(root, client);
    }
}
