package com.opencrm.common.pricing;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * One explicit ramp period, published as language only (ADR-023 D-01, ADR-011).
 *
 * <p>A caller sets either {@code unitPrice} or {@code discountFraction}. This type stores both fields
 * and does not check that exactly one is present. Services own that check, expansion, and pricing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RampStep {

    /** 1-based order of this period on the line. */
    private Integer sequence;

    /** 1-based month of the term where this period starts. */
    private Integer startMonth;

    /** Length of this period in whole months. */
    private Integer months;

    /** Seats or units priced for this period. */
    private Integer quantity;

    /** Which of the two price fields the caller intends. */
    private RampPriceBasis priceBasis;

    /** Absolute net unit price. Present when {@code priceBasis} is {@link RampPriceBasis#ABSOLUTE}. */
    private BigDecimal unitPrice;

    /**
     * Fraction off list, such as {@code 0.07} for seven percent.
     * Present when {@code priceBasis} is {@link RampPriceBasis#DISCOUNT_FRACTION}.
     */
    private BigDecimal discountFraction;
}
