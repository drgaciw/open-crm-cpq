package com.opencrm.approval.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Entra {@code tid} of the current token. The same interim tenant key quote-service stores on {@code Quote.tenantId}.
 * A missing claim is null. Never read from a request body.
 */
public final class ApprovalTenancy {

    private ApprovalTenancy() {
    }

    public static String currentTenantId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt)) {
            return null;
        }
        String tenantId = jwt.getToken().getClaimAsString("tid");
        if (tenantId == null || tenantId.isBlank()) {
            return null;
        }
        return tenantId.strip();
    }
}
