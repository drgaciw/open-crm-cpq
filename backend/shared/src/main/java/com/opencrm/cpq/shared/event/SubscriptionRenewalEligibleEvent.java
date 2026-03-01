package com.opencrm.cpq.shared.event;

import java.io.Serializable;
import java.time.LocalDate;

public record SubscriptionRenewalEligibleEvent(
        String subscriptionId,
        LocalDate renewalDate,
        String currentPlan,
        String usageBand
) implements Serializable {
}
