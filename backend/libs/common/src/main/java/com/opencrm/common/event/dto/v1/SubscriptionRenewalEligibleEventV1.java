package com.opencrm.common.event.dto.v1;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Versioned published-language contract for {@code cpq.subscription.renewal.eligible}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionRenewalEligibleEventV1 {

    public static final int SCHEMA_VERSION = 1;

    @Builder.Default
    private int schemaVersion = SCHEMA_VERSION;

    private String subscriptionId;
    private LocalDate renewalDate;
    private String currentPlan;
    private String usageBand;
}
