package com.opencrm.pricing.support;

import java.math.BigDecimal;
import java.util.Collection;

/**
 * Small null-handling helpers for pricing service guard clauses and defaults.
 */
public final class NullSafe {

    private NullSafe() {
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static boolean isEmpty(Collection<?> values) {
        return values == null || values.isEmpty();
    }

    public static BigDecimal zeroIfNull(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
