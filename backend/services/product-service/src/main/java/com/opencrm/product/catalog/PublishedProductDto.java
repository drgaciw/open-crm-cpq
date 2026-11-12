package com.opencrm.product.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Seller-facing product projection taken from an immutable catalog version snapshot. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PublishedProductDto {

    private String catalogVersionId;
    private Long productId;
    private String productUuid;
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
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private boolean configurable;
    private boolean bundle;
}
