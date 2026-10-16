package com.opencrm.pricing.renewal;

import java.math.BigDecimal;

/**
 * One unmerged precedence rung. Null fields are unset and fall through.
 * On {@link RenewalUpliftRungKind#RULES} only {@code upliftPercentPoints}, {@code sourceId},
 * and {@code retentionMarker} are read. The rules rung does not supply mode, basis, cap, floor, or price-as-of.
 */
public record RenewalUpliftRung(
        RenewalUpliftRungKind kind,
        String sourceId,
        RenewalUpliftMode mode,
        RenewalUpliftBasis basis,
        BigDecimal upliftPercentPoints,
        BigDecimal maxIncreasePct,
        Boolean notAboveList,
        Boolean notBelowExitNet,
        BigDecimal minIncreasePct,
        BigDecimal minUnitAmount,
        RenewalPriceAsOf priceAsOf,
        String retentionMarker) {
}
