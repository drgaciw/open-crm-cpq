package com.opencrm.contract.asset;

import com.opencrm.security.SecurityUtils;
import com.opencrm.security.web.EntraRoleAuthorities;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Coarse installed-base read capability; this is not account or tenant isolation. */
final class InstalledBaseReadAccess {
    private InstalledBaseReadAccess() { }

    static void require() {
        String authority = EntraRoleAuthorities.authorityFor("InstalledBase.Read");
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            Object scope = jwt.getToken().getClaims().get("scp");
            if (!(scope instanceof String value) || value.isBlank()) {
                throw new AccessDeniedException("Delegated user scope is required");
            }
        }
        if (SecurityUtils.getCurrentUser()
                .filter(user -> user.getRoles() != null && user.getRoles().contains(authority)).isEmpty()) {
            throw new AccessDeniedException("Installed-base read capability is required");
        }
    }
}
