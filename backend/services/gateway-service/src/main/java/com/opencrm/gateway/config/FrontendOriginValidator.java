package com.opencrm.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Ensures FRONTEND_URL is set to an exact browser origin in staging/production.
 */
@Component
@Profile("staging | production")
public class FrontendOriginValidator {

    @Value("${FRONTEND_URL:}")
    private String frontendUrl;

    @EventListener(ApplicationReadyEvent.class)
    void validateFrontendUrlOnStartup() {
        if (!StringUtils.hasText(frontendUrl)) {
            throw new IllegalStateException(
                    "FRONTEND_URL must be set to the deployed frontend origin when using staging or production profile");
        }
        validateOriginFormat(frontendUrl.trim());
    }

    static void validateOriginFormat(String origin) {
        try {
            URI uri = new URI(origin);
            if (!StringUtils.hasText(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
                throw new IllegalStateException("FRONTEND_URL must be a valid origin (scheme + host): " + origin);
            }
            if (StringUtils.hasText(uri.getPath()) && !"/".equals(uri.getPath())) {
                throw new IllegalStateException("FRONTEND_URL must not include a path: " + origin);
            }
            if (origin.contains("*")) {
                throw new IllegalStateException("FRONTEND_URL must not contain wildcards: " + origin);
            }
        } catch (URISyntaxException e) {
            throw new IllegalStateException("FRONTEND_URL must be a valid origin URI: " + origin, e);
        }
    }
}
