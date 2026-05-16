package com.opencrm.common.event.dto;

import com.opencrm.common.event.dto.v1.SubscriptionRenewalEligibleEventV1;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Event published when a subscription becomes eligible for renewal.
 * Topic: cpq.subscription.renewal.eligible
 * Publisher: Contract/Asset Management
 * Subscribers: Quote Management, Pricing Engine
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionRenewalEligibleEvent {
    private String subscriptionId;
    private LocalDate renewalDate;
    private String currentPlan;
    private String usageBand;

    public SubscriptionRenewalEligibleEventV1 toPublishedLanguageV1() {
        return SubscriptionRenewalEligibleEventV1.builder()
                .subscriptionId(subscriptionId)
                .renewalDate(renewalDate)
                .currentPlan(currentPlan)
                .usageBand(usageBand)
                .build();
    }
}
