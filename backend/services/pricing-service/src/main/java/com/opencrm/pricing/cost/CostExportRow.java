package com.opencrm.pricing.cost;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One well-formed row of the F-2 cost export. {@code line} is the 1-based line of the file where the record starts
 * (the header is line 1). {@code unitCost} has scale 4.
 */
public record CostExportRow(
        int line,
        String productExternalId,
        CostComponent component,
        String billingUnit,
        BigDecimal unitCost,
        String currency,
        LocalDate effectiveFrom,
        long version,
        String approvedBy) {

    /** The idempotency key without the version: product, component and effective date. */
    Key key() {
        return new Key(productExternalId, component, effectiveFrom);
    }

    record Key(String productExternalId, CostComponent component, LocalDate effectiveFrom) {
    }
}
