package com.opencrm.product.catalog;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

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

    /** DRG-418: EffectiveDateRange start. Null means sellable immediately. */
    private LocalDate effectiveFrom;

    /** DRG-418: EffectiveDateRange end. Null means no planned end. */
    private LocalDate effectiveTo;

    private Boolean configurable;
    private Boolean bundle;
    private String metadata;
}
