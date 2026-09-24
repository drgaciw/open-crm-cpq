package com.opencrm.approval.service;

import com.opencrm.security.web.EntraRoleAuthorities;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Which Entra app roles may decide an approval of a given tier (ADR-016 decision 7). approval-routing is the canonical
 * writer of approval decisions (ADR-013), so this is the one place the rule lives; the quote BFF does not repeat it.
 *
 * <ul>
 *   <li>A higher role satisfies a lower tier: a CFO may decide a Deal Desk approval.</li>
 *   <li>Quote tiers: {@code DEAL_DESK} by DealDesk, SalesVP or CFO; {@code SALES_VP} by SalesVP or CFO; {@code CFO} by
 *       CFO.</li>
 *   <li>Discount-event tiers: {@code MANAGER} like {@code DEAL_DESK}; {@code DIRECTOR} and {@code VP_SALES} like
 *       {@code SALES_VP}. There is no Director role, so {@code DIRECTOR} maps to the next role up.</li>
 *   <li>{@code Admin} and {@code CPQ.User} decide nothing: administering the system is not approving deals.</li>
 *   <li>An unknown tier can be decided by no one; it fails closed.</li>
 * </ul>
 * The rule is code, not configuration: changing who may approve is a reviewed change.
 */
public final class ApprovalAuthority {

    static final String DEAL_DESK = EntraRoleAuthorities.authorityFor("DealDesk");
    static final String SALES_VP = EntraRoleAuthorities.authorityFor("SalesVP");
    static final String CFO = EntraRoleAuthorities.authorityFor("CFO");

    private static final Map<String, Set<String>> DECIDERS = Map.of(
            "DEAL_DESK", Set.of(DEAL_DESK, SALES_VP, CFO),
            "SALES_VP", Set.of(SALES_VP, CFO),
            "CFO", Set.of(CFO),
            "MANAGER", Set.of(DEAL_DESK, SALES_VP, CFO),
            "DIRECTOR", Set.of(SALES_VP, CFO),
            "VP_SALES", Set.of(SALES_VP, CFO));

    private ApprovalAuthority() {
    }

    /** The Spring authorities that may decide the tier; empty for an unknown tier. */
    public static Set<String> decidersOf(String tier) {
        if (tier == null) {
            return Set.of();
        }
        return DECIDERS.getOrDefault(tier.trim().toUpperCase(Locale.ROOT), Set.of());
    }

    /** Whether any of the caller's authorities may decide the tier. */
    public static boolean mayDecide(Set<String> authorities, String tier) {
        return decidersOf(tier).stream().anyMatch(authorities::contains);
    }
}
