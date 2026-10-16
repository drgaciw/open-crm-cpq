package com.opencrm.pricing.renewal;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Caller pins for the three price-as-of dates. This slice does not read a price book.
 * The list and version on the selected date are the pin a later audit stores.
 */
public record RenewalPriceAsOfPins(
        LocalDate actionDate,
        String actionPriceBookVersionId,
        BigDecimal actionListUnit,
        LocalDate effectiveDate,
        String effectivePriceBookVersionId,
        BigDecimal effectiveListUnit,
        LocalDate lineStart,
        String lineStartPriceBookVersionId,
        BigDecimal lineStartListUnit) {
}
