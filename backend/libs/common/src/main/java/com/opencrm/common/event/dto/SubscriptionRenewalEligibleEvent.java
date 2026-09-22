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
}
