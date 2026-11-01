package com.opencrm.contract.asset;

import com.opencrm.security.SecurityUtils;
import com.opencrm.security.web.EntraRoleAuthorities;
import org.springframework.security.access.AccessDeniedException;

/** Coarse installed-base read capability; this is not account or tenant isolation. */
final class InstalledBaseReadAccess {
    private InstalledBaseReadAccess() { }

    static void require() {
        String authority = EntraRoleAuthorities.authorityFor("InstalledBase.Read");
        if (SecurityUtils.getCurrentUser()
                .filter(user -> user.getRoles() != null && user.getRoles().contains(authority)).isEmpty()) {
            throw new AccessDeniedException("Installed-base read capability is required");
        }
    }
}
