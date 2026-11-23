package com.opencrm.quote.lineitem;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

public record BulkLineItemRemovalRequest(
        @NotEmpty List<@NotNull @Positive Long> lineItemIds,
        @NotNull @PositiveOrZero Long expectedQuoteVersion) {
}
