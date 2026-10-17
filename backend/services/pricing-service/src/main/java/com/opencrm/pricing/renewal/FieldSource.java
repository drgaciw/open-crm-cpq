package com.opencrm.pricing.renewal;

/**
 * A merged field plus the rung that set it. A null rung means the ADR default, not a caller rung.
 *
 * @param <T> field value
 */
public record FieldSource<T>(T value, RenewalUpliftRungKind rung, String sourceId) {
    public boolean fromDefault() {
        return rung == null;
    }
}
