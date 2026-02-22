package com.opencrm.pricingairules.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuoteLineItemDTO {
    private Long id;
    private ProductDTO product;
    private ProductBundleDTO bundle;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal listPrice;
    private BigDecimal discountRate;
    private BigDecimal finalPrice;
    private String currency;
    private String lineItemType;
    private JsonNode metadata;
}
