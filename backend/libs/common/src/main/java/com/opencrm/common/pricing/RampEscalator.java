package com.opencrm.common.pricing;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Authoring input kept with a ramp schedule (ADR-023 D-01, ADR-011).
 *
 * <p>Services turn {@code startPrice}, {@code mode}, {@code amount}, and {@code everyMonths} into
 * explicit {@link RampStep} rows. This type stores those inputs and has no method that produces steps.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RampEscalator {

    /** How {@link #amount} is applied to {@link #startPrice}. */
    public enum Mode {
        PERCENT,
        AMOUNT
    }

    /** Unit price of the first period. */
    private BigDecimal startPrice;

    /** Percent of the previous unit price, or a fixed amount added to it. */
    private Mode mode;

    /** Percent (for example {@code 0.05}) or money amount, according to {@link #mode}. */
    private BigDecimal amount;

    /** Whole months between applications. */
    private Integer everyMonths;
}
