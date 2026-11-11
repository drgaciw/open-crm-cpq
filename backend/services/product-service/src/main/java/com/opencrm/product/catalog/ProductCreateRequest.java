package com.opencrm.product.catalog;

import com.fasterxml.jackson.annotation.JsonCreator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
// Omitted optional flags retain primitive defaults; explicit nulls remain invalid.
@NoArgsConstructor(onConstructor_ = @JsonCreator)
@AllArgsConstructor
public class ProductCreateRequest {

    @NotBlank(message = "SKU is required")
    @Size(max = 100, message = "SKU must not exceed 100 characters")
    private String sku;

    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    private String description;

    @NotNull(message = "Product type is required")
    private ProductType productType;

    @NotNull(message = "Category is required")
    private ProductCategory category;

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

    private boolean configurable;
    private boolean bundle;
    private String metadata;
}
