package com.opencrm.pricing.cost;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * The cost basis of one product effective on a date: the sum of its components per billing unit.
 *
 * <p>Recurring products are per billing unit per month; usage, labour and one-time products are per
 * unit (finance decisions F-2, F-3). {@code unitCost} is the sum of {@code components}.
 */
public record UnitCost(
        String productExternalId,
        String billingUnit,
        BigDecimal unitCost,
        String currency,
        LocalDate effectiveFrom,
        long version,
        Map<CostComponent, BigDecimal> components) {

    public UnitCost {
        components = Map.copyOf(components);
    }
}
