package com.opencrm.pricing.renewal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** One unmerged rung on the resolve wire. Unknown properties are rejected. */
@JsonIgnoreProperties(ignoreUnknown = false)
public record RenewalResolveRung(
        @NotNull RenewalUpliftRungKind kind,
        @Size(max = 255) String sourceId,
        RenewalUpliftMode mode,
        RenewalUpliftBasis basis,
        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 4) BigDecimal upliftPercentPoints,
        @Digits(integer = 8, fraction = 4) BigDecimal maxIncreasePct,
        Boolean notAboveList,
        Boolean notBelowExitNet,
        @Digits(integer = 8, fraction = 4) BigDecimal minIncreasePct,
        @DecimalMin("0") @Digits(integer = 18, fraction = 6) BigDecimal minUnitAmount,
        RenewalPriceAsOf priceAsOf,
        @Size(max = RenewalResolveLimits.MAX_RETENTION_MARKER_CHARS) String retentionMarker) {

    public RenewalUpliftRung toRung() {
        return new RenewalUpliftRung(kind, sourceId, mode, basis, upliftPercentPoints, maxIncreasePct,
                notAboveList, notBelowExitNet, minIncreasePct, minUnitAmount, priceAsOf, retentionMarker);
    }
}
