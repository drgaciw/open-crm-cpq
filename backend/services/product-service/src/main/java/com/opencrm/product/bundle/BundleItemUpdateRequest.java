package com.opencrm.product.bundle;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/** Mutable fields of an existing bundle item. */
public record BundleItemUpdateRequest(
        @NotNull @Positive Integer quantity,
        @NotNull Boolean optional,
        @NotNull @PositiveOrZero Integer sortOrder) {
}
