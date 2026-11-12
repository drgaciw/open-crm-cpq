package com.opencrm.configuration.session;

import com.opencrm.security.SecurityUtils;
import com.opencrm.security.UserContext;

/**
 * Entra {@code tid} of the current user token, recorded on a configuration session at StartSession
 * (DRG-424). Service tokens, anonymous callers, and dev-mode principals have none. A blank claim is
 * stored as null. Never read from a request body. Mirrors {@code quote-service}'s
 * {@code QuoteTenantResolver} convention.
 */
public final class ConfigurationSessionTenantResolver {

    private ConfigurationSessionTenantResolver() {
    }

    public static String currentTenantId() {
        return SecurityUtils.getCurrentUser()
                .map(UserContext::getTenantId)
                .filter(value -> value != null && !value.isBlank())
                .map(String::strip)
                .orElse(null);
    }
}
