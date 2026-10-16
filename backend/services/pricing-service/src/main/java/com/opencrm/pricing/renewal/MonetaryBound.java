package com.opencrm.pricing.renewal;

import java.math.BigDecimal;

/**
 * A cap or floor before and after directional scale-4 normalization.
 *
 * @param exact      decimal value before unit-scale rounding
 * @param normalized scale 4, floored for a cap and ceiled for a floor
 */
public record MonetaryBound(BigDecimal exact, BigDecimal normalized) {
}
