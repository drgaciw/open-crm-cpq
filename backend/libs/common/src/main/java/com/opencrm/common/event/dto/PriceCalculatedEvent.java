package com.opencrm.common.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Event published when pricing completes for a quote.
 * Topic: cpq.price.calculated
 * Publisher: Pricing Engine
 *
 * <p>RESERVED in {@link com.opencrm.common.event.EventContractRegistry}: agreed, not produced yet (ADR-018 D5).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PriceCalculatedEvent {
    private String quoteId;
    private Instant calculatedAt;
    private boolean success;
    private BigDecimal subtotalAmount;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private String currencyCode;
    private List<PriceCalculatedLine> lines;
    private List<String> explanationCodes;
    private String approvalState;
    private String approvalTier;

    /** One priced quote line. Same JSON shape as the retired {@code dto.v1.PriceCalculatedEventV1.PriceCalculatedLineV1}. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PriceCalculatedLine {
        private String lineId;
        private String productId;
        private String productExternalId;
        private Integer quantity;
        private BigDecimal listPrice;
        private BigDecimal unitPrice;
        private BigDecimal discountRate;
        private BigDecimal discountAmount;
        private BigDecimal finalPrice;
        private List<String> explanationCodes;
    }
}
