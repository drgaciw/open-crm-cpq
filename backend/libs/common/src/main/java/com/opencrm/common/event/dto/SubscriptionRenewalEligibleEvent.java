package com.opencrm.common.event.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.dto.v1.SubscriptionRenewalEligibleEventV1;
import lombok.AllArgsConstructor;
import lombok.Builder;
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
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubscriptionRenewalEligibleEvent {

    public static final String EVENT_TYPE = EventContractRegistry.SUBSCRIPTION_RENEWAL_ELIGIBLE_V1.eventType();
    public static final int EVENT_VERSION = EventContractRegistry.SUBSCRIPTION_RENEWAL_ELIGIBLE_V1.eventVersion();

    private String eventType;
    private Integer eventVersion;

    private String subscriptionId;
    private LocalDate renewalDate;
    private String currentPlan;
    private String usageBand;

    public SubscriptionRenewalEligibleEvent(
            String subscriptionId,
            LocalDate renewalDate,
            String currentPlan,
            String usageBand
    ) {
        this.subscriptionId = subscriptionId;
        this.renewalDate = renewalDate;
        this.currentPlan = currentPlan;
        this.usageBand = usageBand;
    }

    public static SubscriptionRenewalEligibleEvent v1(
            String subscriptionId,
            LocalDate renewalDate,
            String currentPlan,
            String usageBand
    ) {
        SubscriptionRenewalEligibleEvent event = new SubscriptionRenewalEligibleEvent(
                subscriptionId,
                renewalDate,
                currentPlan,
                usageBand
        );
        event.setEventType(EVENT_TYPE);
        event.setEventVersion(EVENT_VERSION);
        return event;
    }

    public SubscriptionRenewalEligibleEventV1 toPublishedLanguageV1() {
        return SubscriptionRenewalEligibleEventV1.builder()
                .subscriptionId(subscriptionId)
                .renewalDate(renewalDate)
                .currentPlan(currentPlan)
                .usageBand(usageBand)
                .build();
    }
}
