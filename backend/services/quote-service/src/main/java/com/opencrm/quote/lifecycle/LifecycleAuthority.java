package com.opencrm.quote.lifecycle;

import com.opencrm.security.web.EntraRoleAuthorities;

import java.util.Set;

/**
 * Who may create or submit a lifecycle change (ADR-016 decision 7, owner decision 1).
 * Admin is refused: administering the system is not a commercial action.
 */
public final class LifecycleAuthority {

    static final String CPQ_USER = EntraRoleAuthorities.authorityFor("CPQ.User");
    static final String DEAL_DESK = EntraRoleAuthorities.authorityFor("DealDesk");
    static final String SALES_VP = EntraRoleAuthorities.authorityFor("SalesVP");
    static final String CFO = EntraRoleAuthorities.authorityFor("CFO");

    public static final Set<String> ALLOWED = Set.of(CPQ_USER, DEAL_DESK, SALES_VP, CFO);

    private LifecycleAuthority() {
    }

    public static boolean mayChange(Set<String> authorities) {
        return authorities != null && ALLOWED.stream().anyMatch(authorities::contains);
    }
}
