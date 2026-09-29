package com.opencrm.quote.quote;

import com.opencrm.security.web.EntraRoleAuthorities;

import java.util.Set;

/**
 * Which Entra app roles may pass or fail a quote's review (ADR-016, owner decision 1).
 *
 * <p>Passing a review releases a quote to be sent, so it is a controlled step. The same roles that may decide a Deal
 * Desk approval in approval-routing may do it: {@code DealDesk}, {@code SalesVP} and {@code CFO}. {@code CPQ.User}
 * and {@code Admin} may not: an author is not their own reviewer, and administering the system is not reviewing
 * deals. The rule is code, not configuration: changing who may review is a reviewed change.
 */
public final class ReviewAuthority {

    static final String DEAL_DESK = EntraRoleAuthorities.authorityFor("DealDesk");
    static final String SALES_VP = EntraRoleAuthorities.authorityFor("SalesVP");
    static final String CFO = EntraRoleAuthorities.authorityFor("CFO");

    /** The Spring authorities that may pass or fail a review. */
    public static final Set<String> REVIEWERS = Set.of(DEAL_DESK, SALES_VP, CFO);

    private ReviewAuthority() {
    }

    /** Whether any of the caller's authorities may pass or fail a review. */
    public static boolean mayReview(Set<String> authorities) {
        return REVIEWERS.stream().anyMatch(authorities::contains);
    }
}
