package com.opencrm.common.pricing;

/**
 * Price basis of one {@link RampStep} (ADR-023 D-01).
 *
 * <p>{@link #ABSOLUTE} is carried in {@code unitPrice}. {@link #DISCOUNT_FRACTION} is carried in
 * {@code discountFraction}. The step type does not check which of those fields the caller set.
 */
public enum RampPriceBasis {
    ABSOLUTE,
    DISCOUNT_FRACTION
}
