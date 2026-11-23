package com.opencrm.product.bundle;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Mutable bundle details. The parent product and lifecycle status are not editable here. */
public record ProductBundleUpdateRequest(
        @NotBlank @Size(max = 255) String name,
        String description,
        DiscountType discountType,
        @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal discountValue,
        @PositiveOrZero Integer minItems,
        @PositiveOrZero Integer maxItems) {
}
