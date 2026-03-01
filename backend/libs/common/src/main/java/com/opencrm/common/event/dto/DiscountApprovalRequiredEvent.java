package com.opencrm.common.event.dto;

import lombok.AllArgsConstructor;
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
@AllArgsConstructor
public class DiscountApprovalRequiredEvent {
    private String quoteId;
    private String lineId;
    private BigDecimal marginDelta;
    private BigDecimal requestedDiscount;
    private String currencyCode;
}
