package com.opencrm.contract.service;

import com.opencrm.contract.domain.RenewalMode;
import com.opencrm.contract.domain.Subscription;

/**
 * Gate for unattended auto-roll intent (DRG-664 AC4 / AC5).
 * Quote-accepted renewal math is unchanged; this only reads {@code renewal_mode}.
 * RED stub — GREEN fills AUTO_ROLL recognition and unknown-token fail-safe.
 */
public final class SubscriptionRenewalModePolicy {
    private SubscriptionRenewalModePolicy() {}

    /**
     * @return true only when the stored mode is explicitly {@link RenewalMode#AUTO_ROLL}.
     *         Null, {@link RenewalMode#MANUAL}, and unknown tokens keep today's no-auto-roll behaviour.
     */
    public static boolean allowsAutoRoll(RenewalMode mode) {
        return false;
    }

    /**
     * Parse a raw persisted / inbound token. Unknown and blank values fail safe (no auto-roll).
     */
    public static boolean allowsAutoRoll(String rawMode) {
        return false;
    }

    /** Convenience for the subscription entity field. */
    public static boolean allowsAutoRoll(Subscription subscription) {
        if (subscription == null) {
            return false;
        }
        return allowsAutoRoll(subscription.getRenewalMode());
    }
}
