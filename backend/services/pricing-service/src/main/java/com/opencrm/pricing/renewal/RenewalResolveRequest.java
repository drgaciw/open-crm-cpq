package com.opencrm.pricing.renewal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Wire body for renewal resolve. There is no tenant field. A {@code tenantId} property is rejected.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record RenewalResolveRequest(
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        @NotNull @DecimalMin("0") @Digits(integer = 18, fraction = 6) BigDecimal exitNet,
        @NotNull @DecimalMin("0") @Digits(integer = 18, fraction = 6) BigDecimal quantity,
        @NotNull @Min(1) @Max(RenewalResolveLimits.MAX_TERM_MONTHS) Integer termMonths,
        @NotNull @Valid RenewalPriceAsOfPins pins,
        @NotNull @Size(max = RenewalResolveLimits.MAX_RUNGS) List<@Valid RenewalResolveRung> rungs) {

    public RenewalUpliftRequest toUpliftRequest() {
        List<RenewalUpliftRung> mapped = rungs == null
                ? List.of()
                : rungs.stream().map(RenewalResolveRung::toRung).toList();
        return new RenewalUpliftRequest(currency, exitNet, quantity, termMonths, pins, mapped);
    }
}
