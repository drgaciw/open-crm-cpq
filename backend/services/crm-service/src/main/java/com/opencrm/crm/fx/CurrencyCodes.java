package com.opencrm.crm.fx;

import java.util.Locale;

/** Small helpers for ISO 4217 currency codes as stored on deals and rates. */
public final class CurrencyCodes {

    private CurrencyCodes() {
    }

    /** Trims and upper-cases a code; returns {@code null} for null or blank input. */
    public static String normalize(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    /** True for three ASCII letters. Does not check that the code is a live ISO currency. */
    public static boolean isWellFormed(String code) {
        return code != null && code.length() == 3 && code.chars().allMatch(c -> c >= 'A' && c <= 'Z');
    }
}
