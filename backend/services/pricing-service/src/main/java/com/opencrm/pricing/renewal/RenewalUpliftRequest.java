package com.opencrm.pricing.renewal;

import java.math.BigDecimal;
import java.util.List;

/**
 * Inputs for one complete flat renewal period. Currency, quantity, and positive term months
 * are required by the resolver. Rungs arrive unmerged. Percent points use the caller's scale:
 * {@code 7} means 7 percent. A rules percent is already in percent points; this type does not convert a fraction.
 */
public record RenewalUpliftRequest(
        String currency,
        BigDecimal exitNet,
        BigDecimal quantity,
        Integer termMonths,
        RenewalPriceAsOfPins pins,
        List<RenewalUpliftRung> rungs) {
}
