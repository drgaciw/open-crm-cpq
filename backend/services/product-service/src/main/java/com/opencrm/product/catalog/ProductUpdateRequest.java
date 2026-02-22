package com.opencrm.product.catalog;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductUpdateRequest {

    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    private String description;

    private ProductType productType;

    private ProductCategory category;

    private ProductStatus status;

    private BigDecimal listPrice;

    @Size(max = 3, message = "Currency code must be 3 characters")
    private String currency;

    private BillingFrequency billingFrequency;

    @Size(max = 50, message = "Unit of measure must not exceed 50 characters")
    private String unitOfMeasure;

    private Boolean configurable;
    private Boolean bundle;
    private String metadata;
}
