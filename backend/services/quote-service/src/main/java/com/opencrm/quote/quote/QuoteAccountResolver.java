package com.opencrm.quote.quote;

import com.opencrm.security.SecurityUtils;
import com.opencrm.security.UserContext;

/**
 * Entra {@code account_id} claim of the current user token (ADR-016).
 * It is the caller's account, matched to a quote's {@code customer_external_id}.
 * Service tokens, anonymous callers, and a blank claim have none. Never read from a request body.
 */
public final class QuoteAccountResolver {

    private QuoteAccountResolver() {
    }

    public static String currentAccountId() {
        return SecurityUtils.getCurrentUser()
                .map(UserContext::getAccountId)
                .filter(value -> !value.isBlank())
                .map(String::strip)
                .orElse(null);
    }
}
