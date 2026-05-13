package com.opencrm.rules.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Product context fact for pricing rules.
 *
 * <p>The stable fields identify the priced product and its list-price context.
 * Metadata carries catalog attributes that are still owned by product/pricebook
 * services but need to be visible to rules during evaluation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductFact implements MetadataBackedFact {

    private String externalId;
    private String name;
    private String productType;
    private String category;
    private BigDecimal listPrice;
    private String currency;

    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();
}
