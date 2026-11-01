package com.opencrm.quote.quote;

import com.opencrm.security.SecurityUtils;
import com.opencrm.security.UserContext;

/**
 * Entra {@code tid} of the current user token. Service tokens, anonymous callers, and
 * dev-mode principals have none. A blank claim is stored as null. Never read from a request body.
 */
public final class QuoteTenantResolver {

    private QuoteTenantResolver() {
    }

    public static String currentTenantId() {
        return SecurityUtils.getCurrentUser()
                .map(UserContext::getTenantId)
                .filter(value -> !value.isBlank())
                .map(String::strip)
                .orElse(null);
    }
}
