package com.opencrm.pricing.execution.margin;

import java.util.Locale;

/**
 * {@code opencrm.pricing.margin.source} (DRG-438 margin slice).
 *
 * <p>{@link #PLACEHOLDER} is today's behaviour: {@code estimatedMarginPct} is the non-authoritative constant
 * 40.0 and no margin policy runs (ADR-023 D-06). {@link #COST_BASIS} derives margin from the Finance cost basis
 * (F-1) and governs it, failing closed when a cost is missing.
 */
public enum MarginSource {

    PLACEHOLDER("placeholder"),
    COST_BASIS("cost-basis");

    private final String wireValue;

    MarginSource(String wireValue) {
        this.wireValue = wireValue;
    }

    /** The value used in configuration, responses and the audit trace. */
    public String wireValue() {
        return wireValue;
    }

    /** Parses {@code placeholder}, {@code cost-basis} and their enum-name spellings. */
    public static MarginSource from(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Margin source is required");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        for (MarginSource source : values()) {
            if (source.wireValue.equals(normalized)) {
                return source;
            }
        }
        throw new IllegalArgumentException("Unknown margin source: " + value);
    }
}
