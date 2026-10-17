package com.opencrm.pricing.renewal;

/**
 * Bounds for the internal renewal resolve request. There is no gateway rate limit on this path;
 * these constants are the slice's abuse bound. See the DRG-549 security criteria.
 */
public final class RenewalResolveLimits {

    /** Maximum accepted body size, in bytes. Larger bodies are rejected before the resolver runs. */
    public static final int MAX_BODY_BYTES = 65_536;

    public static final int MAX_RUNGS = 5;

    public static final int MAX_TERM_MONTHS = 120;

    public static final int MAX_RETENTION_MARKER_CHARS = 128;

    private RenewalResolveLimits() {
    }
}
