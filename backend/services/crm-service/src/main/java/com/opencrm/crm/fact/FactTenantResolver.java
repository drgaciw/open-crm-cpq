package com.opencrm.crm.fact;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Authenticated tenant for the fact ledger (DRG-512).
 * <p>
 * Tenant is the verified JWT {@code tid} claim — the same Entra claim {@code UserContext}
 * and quote-service {@code QuoteTenantResolver} read. A query parameter cannot set it.
 * When a caller still sends {@code tenant}, it must equal the claim or the request is refused.
 * Missing or blank {@code tid} fails closed ({@code TENANT_REQUIRED}).
 * <p>
 * Until ADR-036 lands, contacts and organizations have no tenant column; subject ownership for
 * this slice is enforced separately via prior {@code crm_fact} rows for that subject.
 */
public final class FactTenantResolver {

    private FactTenantResolver() {
    }

    /**
     * @param requestedTenant optional query value; ignored when null/blank, must match {@code tid} otherwise
     * @return the authenticated tenant id
     */
    public static String requireTenant(String requestedTenant) {
        String authenticated = authenticatedTenant();
        if (requestedTenant != null && !requestedTenant.isBlank()
                && !authenticated.equals(requestedTenant.strip())) {
            throw new BusinessException(
                    "Tenant does not match the verified token",
                    HttpStatus.FORBIDDEN,
                    "TENANT_MISMATCH");
        }
        return authenticated;
    }

    static String authenticatedTenant() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            throw new BusinessException(
                    "Tenant is not on the verified token",
                    HttpStatus.FORBIDDEN,
                    "TENANT_REQUIRED");
        }
        Jwt jwt = jwtAuth.getToken();
        String tid = jwt.getClaimAsString("tid");
        if (tid == null || tid.isBlank()) {
            throw new BusinessException(
                    "Tenant is not on the verified token",
                    HttpStatus.FORBIDDEN,
                    "TENANT_REQUIRED");
        }
        return tid.strip();
    }

    /**
     * Cross-tenant subject access looks like a missing subject (same convention as facts).
     */
    static void refuseForeignSubject(FactSubjectType subjectType, long subjectId) {
        String label = switch (subjectType) {
            case CONTACT -> "Contact";
            case ORGANIZATION -> "Organization";
        };
        throw new ResourceNotFoundException(label, subjectId);
    }
}
