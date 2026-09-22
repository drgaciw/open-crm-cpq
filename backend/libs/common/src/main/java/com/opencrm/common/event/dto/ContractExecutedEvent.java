package com.opencrm.common.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.opencrm.common.event.EventContractRegistry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Versioned published-language event emitted when CLM activates a billable subscription.
 * Topic: cpq.contract.executed
 * Publisher: contract-asset-management
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContractExecutedEvent {

    public static final String EVENT_TYPE = EventContractRegistry.CONTRACT_EXECUTED_V1.eventType();
    public static final int EVENT_VERSION = EventContractRegistry.CONTRACT_EXECUTED_V1.eventVersion();

    private String eventType = EVENT_TYPE;
    private int eventVersion = EVENT_VERSION;
    private String contractId;
    private String subscriptionId;
    private String subscriptionNumber;
    private String quoteId;
    private String customerId;
    private Instant executedAt;
    private BillingSnapshot billing;
    private CorrelationSnapshot correlation;

    public static ContractExecutedEvent v1(
            String contractId,
            String subscriptionId,
            String subscriptionNumber,
            String quoteId,
            String customerId,
            Instant executedAt,
            BillingSnapshot billing,
            CorrelationSnapshot correlation
    ) {
        ContractExecutedEvent event = new ContractExecutedEvent();
        event.setContractId(contractId);
        event.setSubscriptionId(subscriptionId);
        event.setSubscriptionNumber(subscriptionNumber);
        event.setQuoteId(quoteId);
        event.setCustomerId(customerId);
        event.setExecutedAt(executedAt);
        event.setBilling(billing);
        event.setCorrelation(correlation);
        return event;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BillingSnapshot {
        private BigDecimal arr;
        private BigDecimal mrr;
        private String currencyCode;
        private String startDate;
        private String endDate;
        private String renewalDate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CorrelationSnapshot {
        private String correlationId;
        private String causationId;
        private String sourceService;
    }
}
