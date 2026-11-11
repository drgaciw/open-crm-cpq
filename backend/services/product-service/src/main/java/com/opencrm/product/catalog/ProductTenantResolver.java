package com.opencrm.product.catalog;

import com.opencrm.security.SecurityUtils;
import com.opencrm.security.UserContext;

/**
 * Entra {@code tid} of the current user token, the same deliberately weak per-deployment interim quote-service
 * uses for {@code quotes.tenant_id} (DRG-632). Service tokens, anonymous callers, and dev-mode principals have
 * none. A blank claim is stored as null. Never read from a request body.
 *
 * <p>ADR-036 ("account-tenant ownership") is Proposed, not accepted, and authorizes no verified per-customer
 * tenant; this resolver does not claim to supply one.
 */
public final class ProductTenantResolver {

    private ProductTenantResolver() {
    }

    public static String currentTenantId() {
        return SecurityUtils.getCurrentUser()
                .map(UserContext::getTenantId)
                .filter(value -> !value.isBlank())
                .map(String::strip)
                .orElse(null);
    }
}
