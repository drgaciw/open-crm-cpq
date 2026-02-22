package com.opencrm.rules.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Product fact for rules engine evaluation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductFact {

    private String externalId;
    private String name;
    private String productType;
    private String category;
    private BigDecimal listPrice;
    private String currency;

    @Builder.Default
    private Map<String, String> metadata = new HashMap<>();
}
