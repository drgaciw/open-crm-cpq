package com.opencrm.common.event.dto.v1;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Versioned published-language contract for {@code cpq.price.calculated}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceCalculatedEventV1 {

    public static final int SCHEMA_VERSION = 1;

    @Builder.Default
    private int schemaVersion = SCHEMA_VERSION;

    private String quoteId;
    private Instant calculatedAt;
    private boolean success;
    private BigDecimal subtotalAmount;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private String currencyCode;
    private List<PriceCalculatedLineV1> lines;
    private List<String> explanationCodes;
    private String approvalState;
    private String approvalTier;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PriceCalculatedLineV1 {
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
