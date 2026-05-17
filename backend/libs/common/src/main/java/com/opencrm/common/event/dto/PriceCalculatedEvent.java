package com.opencrm.common.event.dto;

import com.opencrm.common.event.dto.v1.PriceCalculatedEventV1;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Event published when pricing completes for a quote.
 * Topic: cpq.price.calculated
 * Publisher: Pricing Engine
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PriceCalculatedEvent {
    private String quoteId;
    private Instant calculatedAt;
    private boolean success;
    private BigDecimal subtotalAmount;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private String currencyCode;
    private List<PriceCalculatedEventV1.PriceCalculatedLineV1> lines;
    private List<String> explanationCodes;
    private String approvalState;
    private String approvalTier;

    public PriceCalculatedEventV1 toPublishedLanguageV1() {
        return PriceCalculatedEventV1.builder()
                .quoteId(quoteId)
                .calculatedAt(calculatedAt)
                .success(success)
                .subtotalAmount(subtotalAmount)
                .discountAmount(discountAmount)
                .totalAmount(totalAmount)
                .currencyCode(currencyCode)
                .lines(lines)
                .explanationCodes(explanationCodes)
                .approvalState(approvalState)
                .approvalTier(approvalTier)
                .build();
    }
}
