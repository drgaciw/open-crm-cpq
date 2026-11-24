package com.opencrm.quote.approval;

import com.opencrm.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Tenant and account for a quote-approval call.
 * <p>
 * Tenant is the verified JWT {@code tid}. Account is the {@code account_id} claim when the token carries one.
 * Neither is read from a header or a body. No token (dev permit-all and unit tests) is not scoped; a token
 * without {@code tid} is refused. Approval-routing applies the same rule on the relayed token.
 */
final class ApprovalAccess {

    static final String ACCOUNT_CLAIM = "account_id";

    private ApprovalAccess() {
    }

    record Scope(String tenantId, String accountId, boolean jwtPresent) {

        boolean enforced() {
            return tenantId != null;
        }

        boolean jwtWithoutTenant() {
            return jwtPresent && tenantId == null;
        }
    }

    static Scope current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth) || !authentication.isAuthenticated()) {
            return new Scope(null, null, false);
        }
        String tenantId = text(jwtAuth.getToken().getClaimAsString("tid"));
        if (tenantId == null) {
            throw new BusinessException("Tenant is not on the verified token",
                    HttpStatus.FORBIDDEN, "TENANT_REQUIRED");
        }
        return new Scope(tenantId, text(jwtAuth.getToken().getClaimAsString(ACCOUNT_CLAIM)), true);
    }

    private static String text(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}
