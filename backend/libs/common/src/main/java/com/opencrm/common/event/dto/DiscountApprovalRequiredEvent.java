package com.opencrm.common.event.dto;

import com.opencrm.common.event.dto.v1.DiscountApprovalRequiredEventV1;
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

    public DiscountApprovalRequiredEventV1 toPublishedLanguageV1() {
        return DiscountApprovalRequiredEventV1.builder()
                .quoteId(quoteId)
                .lineId(lineId)
                .marginDelta(marginDelta)
                .requestedDiscount(requestedDiscount)
                .currencyCode(currencyCode)
                .build();
    }
}
