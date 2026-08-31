package com.opencrm.contract.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.opencrm.contract.domain.SubscriptionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubscriptionDto {

    private Long id;
    private String uuid;
    private String subscriptionNumber;
    private String customerId;
    private String quoteId;
    private SubscriptionStatus status;
    private String planName;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate renewalDate;
    private BigDecimal mrr;
    private BigDecimal arr;
    private String currencyCode;
    private String usageBand;
    private String erpSyncStatus;
    private String erpSubscriptionId;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private String updatedBy;
    private Long version;
}
