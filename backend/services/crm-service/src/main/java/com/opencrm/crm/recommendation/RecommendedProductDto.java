package com.opencrm.crm.recommendation;

import java.math.BigDecimal;

/**
 * One advisory product suggestion. {@code productId} is a stable catalog-corpus key, not a
 * product-service surrogate id. {@code confidence} is a rank in {@code [0, 1]}, not a calibrated probability.
 */
public record RecommendedProductDto(String productId, String rationale, BigDecimal confidence) {

    public RecommendedProductDto {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId is required");
        }
        if (rationale == null || rationale.isBlank()) {
            throw new IllegalArgumentException("rationale is required");
        }
        if (confidence == null
                || confidence.compareTo(BigDecimal.ZERO) < 0
                || confidence.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("confidence must be from 0 to 1 inclusive");
        }
    }
}
