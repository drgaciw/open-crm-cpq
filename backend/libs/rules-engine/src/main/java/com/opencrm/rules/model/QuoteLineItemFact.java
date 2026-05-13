package com.opencrm.rules.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Quote line-item pricing fact for Drools rule evaluation.
 *
 * <p>Canonical fields capture the stable line-level inputs and outputs currently
 * used by the seeded pricing catalog. Metadata is reserved for product-policy
 * extensions that should not force a fact-model change for every rule.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteLineItemFact implements MetadataBackedFact {

    private String externalId;
    private String lineItemType;
    private String productExternalId;
    private int quantity;
    private BigDecimal listPrice;
    private BigDecimal unitPrice;
    private BigDecimal discountRate;
    private BigDecimal finalPrice;
    private QuoteFact quote;

    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

}
