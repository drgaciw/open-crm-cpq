package com.opencrm.contract.service;

import com.opencrm.contract.domain.RenewalMode;
import com.opencrm.contract.domain.Subscription;

/**
 * Gate for unattended auto-roll intent (DRG-664 AC4 / AC5).
 * Quote-accepted renewal math is unchanged; this only reads {@code renewal_mode}.
 * Null, {@link RenewalMode#MANUAL}, blank, and unknown tokens keep today's no-auto-roll behaviour.
 * Raw tokens must match the persisted CHECK values exactly ({@code AUTO_ROLL}, {@code MANUAL}).
 */
public final class SubscriptionRenewalModePolicy {
    private SubscriptionRenewalModePolicy() {}

    /**
     * @return true only when the stored mode is explicitly {@link RenewalMode#AUTO_ROLL}.
     */
    public static boolean allowsAutoRoll(RenewalMode mode) {
        return mode == RenewalMode.AUTO_ROLL;
    }

    /**
     * Parse a raw persisted / inbound token. Unknown and blank values fail safe (no auto-roll).
     */
    public static boolean allowsAutoRoll(String rawMode) {
        if (rawMode == null) {
            return false;
        }
        String token = rawMode.trim();
        if (token.isEmpty()) {
            return false;
        }
        try {
            return allowsAutoRoll(RenewalMode.valueOf(token));
        } catch (IllegalArgumentException unknown) {
            return false;
        }
    }

    /** Convenience for the subscription entity field. */
    public static boolean allowsAutoRoll(Subscription subscription) {
        if (subscription == null) {
            return false;
        }
        return allowsAutoRoll(subscription.getRenewalMode());
    }
}
