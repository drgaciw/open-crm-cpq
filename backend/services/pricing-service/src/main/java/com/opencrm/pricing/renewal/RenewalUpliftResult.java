package com.opencrm.pricing.renewal;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Pure computation result. Nothing here is stored. The fields are the inputs a later audit row needs:
 * basis, price-as-of, cap, floor, and the rule or policy id that supplied the percent (or the mode, when no percent was merged).
 */
public record RenewalUpliftResult(
        RenewalUpliftResolution resolution,
        FieldSource<RenewalUpliftMode> mode,
        FieldSource<RenewalUpliftBasis> basis,
        FieldSource<BigDecimal> percentPoints,
        BigDecimal exitNet,
        BigDecimal basisAmount,
        FieldSource<RenewalPriceAsOf> priceAsOf,
        LocalDate priceAsOfDate,
        String priceBookVersionId,
        BigDecimal listUnitAtPriceAsOf,
        FieldSource<BigDecimal> maxIncreasePct,
        FieldSource<Boolean> notAboveList,
        FieldSource<Boolean> notBelowExitNet,
        FieldSource<BigDecimal> minIncreasePct,
        FieldSource<BigDecimal> minUnitAmount,
        MonetaryBound capBound,
        MonetaryBound floorBound,
        MonetaryBound capApplied,
        MonetaryBound floorApplied,
        BigDecimal unitPrice,
        BigDecimal extendedAmount,
        Integer flatPeriodCount,
        String appliedRuleOrPolicyId,
        String clauseId,
        boolean rulesSuppliedPercent,
        String explanationCode,
        String retentionMarker,
        String currency,
        BigDecimal quantity,
        Integer termMonths) {
}
