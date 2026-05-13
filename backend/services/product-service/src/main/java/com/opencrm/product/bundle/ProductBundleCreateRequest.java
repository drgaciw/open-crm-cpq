package com.opencrm.product.bundle;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
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
public class ProductBundleCreateRequest {

    @NotNull(message = "Product ID is required")
    private Long productId;

    @NotBlank(message = "Bundle name is required")
    @Size(max = 255, message = "Name must not exceed 255 characters")
    private String name;

    private String description;

    private DiscountType discountType;

    private BigDecimal discountValue;

    @Min(value = 0, message = "Minimum items cannot be negative")
    private Integer minItems;

    @Min(value = 0, message = "Maximum items cannot be negative")
    private Integer maxItems;
}
