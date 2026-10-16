package com.opencrm.common.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Event published when a subscription becomes eligible for renewal.
 * Topic: cpq.subscription.renewal.eligible
 * Publisher: Contract/Asset Management
 * Subscribers: Quote Management, Pricing Engine
 *
 * <p>Publication is once per term. {@link #dedupeKey} is the outbox aggregate id, which the relay
 * copies onto the Kafka record key. Adding the field is compatible with the v1 contract: consumers
 * ignore unknown properties, and this repository has no schema-registry subject to register.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubscriptionRenewalEligibleEvent {
    private String subscriptionId;
    private LocalDate renewalDate;
    private String currentPlan;
    private String usageBand;

    /**
     * {@code subscriptionId:termEnd:window}. Term end is the subscription end date. The window is the
     * renewal date the nightly scan uses, or {@code none} when that date is absent.
     */
    private String dedupeKey;

    /** Outbox {@code aggregate_id} length. A longer key would be truncated by the column and could collide. */
    public static final int DEDUPE_KEY_MAX_LENGTH = 100;

    /**
     * Deterministic key for one subscription term and renewal window.
     *
     * @param subscriptionId persisted subscription id; must not contain {@code ':'}
     * @param termEnd subscription end date
     * @param window renewal date, or null when the subscription has no window yet
     */
    public static String dedupeKey(String subscriptionId, LocalDate termEnd, LocalDate window) {
        if (subscriptionId == null || subscriptionId.isBlank()) {
            throw new IllegalArgumentException("subscriptionId is required");
        }
        if (subscriptionId.indexOf(':') >= 0) {
            throw new IllegalArgumentException("subscriptionId must not contain ':'");
        }
        if (termEnd == null) {
            throw new IllegalArgumentException("termEnd is required");
        }
        String key = subscriptionId + ":" + termEnd + ":" + (window == null ? "none" : window);
        if (key.length() > DEDUPE_KEY_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "dedupe key length " + key.length() + " exceeds outbox aggregate_id " + DEDUPE_KEY_MAX_LENGTH);
        }
        return key;
    }
}
