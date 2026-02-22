package com.opencrm.product.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductDto {

    private Long id;
    private String uuid;
    private String sku;
    private String name;
    private String description;
    private ProductType productType;
    private ProductCategory category;
    private ProductStatus status;
    private BigDecimal listPrice;
    private String currency;
    private BillingFrequency billingFrequency;
    private String unitOfMeasure;
    private boolean configurable;
    private boolean bundle;
    private String metadata;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private String updatedBy;
    private Long version;
}
