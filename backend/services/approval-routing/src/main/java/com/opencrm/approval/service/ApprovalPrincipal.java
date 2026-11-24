package com.opencrm.approval.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Tenant and account for an approval call.
 * <p>
 * Both come from the verified JWT only: {@code tid}, and {@code account_id} when that claim is present.
 * A request header or body cannot set either one. There is no escalate operation on this service; create,
 * read, approve, reject and the inbox all use this scope.
 */
public final class ApprovalPrincipal {

    /** Optional claim. Absent means the caller is not account-scoped. */
    public static final String ACCOUNT_CLAIM = "account_id";

    private ApprovalPrincipal() {
    }

    public record Scope(String tenantId, String accountId, boolean jwtPresent) {

        public boolean enforced() {
            return tenantId != null;
        }

        /** A verified token that does not carry {@code tid}. Fail closed. */
        public boolean jwtWithoutTenant() {
            return jwtPresent && tenantId == null;
        }
    }

    public static Scope current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth) || !authentication.isAuthenticated()) {
            return new Scope(null, null, false);
        }
        return new Scope(
                text(jwtAuth.getToken().getClaimAsString("tid")),
                text(jwtAuth.getToken().getClaimAsString(ACCOUNT_CLAIM)),
                true);
    }

    private static String text(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}
