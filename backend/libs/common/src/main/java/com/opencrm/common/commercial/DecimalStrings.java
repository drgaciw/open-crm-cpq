package com.opencrm.common.commercial;

import java.math.BigDecimal;

/** Domain decimal normalization that runs before JCS. Plain notation, no trailing fractional zeroes. */
public final class DecimalStrings {

    private DecimalStrings() {
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new CommercialDigestException("decimal value is required");
        }
        if ("NaN".equals(raw) || "Infinity".equals(raw) || "-Infinity".equals(raw) || raw.indexOf('e') >= 0 || raw.indexOf('E') >= 0) {
            throw new CommercialDigestException("unsupported numeric input: " + raw);
        }
        try {
            return normalize(new BigDecimal(raw));
        } catch (NumberFormatException failure) {
            throw new CommercialDigestException("unsupported numeric input: " + raw);
        }
    }

    public static String normalize(BigDecimal value) {
        if (value == null) {
            throw new CommercialDigestException("decimal value is required");
        }
        if (value.signum() == 0) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString();
    }
}
