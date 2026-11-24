package com.opencrm.common.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Event published when a discount requires approval.
 * Topic: cpq.discount.approval.required
 * Publisher: Pricing Engine
 * Subscribers: Approval Routing, Quote Management
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DiscountApprovalRequiredEvent {
    private String quoteId;
    private String lineId;
    private BigDecimal marginDelta;
    private BigDecimal requestedDiscount;
    private String currencyCode;
    /** Optional ramp snapshot binding; absent on legacy flat-price events. */
    private String pricingFingerprint;
    private Integer governingPeriod;
    private String approvalTier;
    private String reason;
    /** False retires the previous ramp request after policy no longer requires approval. Null preserves legacy behavior. */
    private Boolean approvalRequired;
    /**
     * Entra tenant of the quote (Security H4). Set from {@code Quote.tenantId} by the quote-owned ramp producer;
     * approval-routing stamps it on the request. Absent on legacy events and on quotes created before quote tenancy,
     * in which case the request has no tenant and approval-routing refuses decisions on it (fail closed).
     */
    private String tenantId;

    public DiscountApprovalRequiredEvent(String quoteId, String lineId, BigDecimal marginDelta,
                                         BigDecimal requestedDiscount, String currencyCode, String pricingFingerprint,
                                         Integer governingPeriod, String approvalTier, String reason,
                                         Boolean approvalRequired) {
        this.quoteId = quoteId;
        this.lineId = lineId;
        this.marginDelta = marginDelta;
        this.requestedDiscount = requestedDiscount;
        this.currencyCode = currencyCode;
        this.pricingFingerprint = pricingFingerprint;
        this.governingPeriod = governingPeriod;
        this.approvalTier = approvalTier;
        this.reason = reason;
        this.approvalRequired = approvalRequired;
    }

    public DiscountApprovalRequiredEvent(String quoteId, String lineId, BigDecimal marginDelta,
                                         BigDecimal requestedDiscount, String currencyCode) {
        this(quoteId, lineId, marginDelta, requestedDiscount, currencyCode, null, null, null, null, null);
    }

    public DiscountApprovalRequiredEvent(String quoteId, String lineId, BigDecimal marginDelta,
                                         BigDecimal requestedDiscount, String currencyCode, String pricingFingerprint,
                                         Integer governingPeriod, String approvalTier, String reason) {
        this(quoteId, lineId, marginDelta, requestedDiscount, currencyCode, pricingFingerprint,
                governingPeriod, approvalTier, reason, true);
    }
}
