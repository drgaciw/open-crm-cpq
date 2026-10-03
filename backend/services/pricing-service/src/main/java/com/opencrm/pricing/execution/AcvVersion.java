package com.opencrm.pricing.execution;

/**
 * ADR-023 D-03. The ACV function {@link MetricsCalculator} applies to every quote.
 *
 * <p>The committed default is {@link #LEGACY}. Selecting {@link #TERM_YEARS} is a
 * separate switch from the ramp flag, which this type does not read.
 */
public enum AcvVersion {

    /** {@code (monthlyPrice × durationInMonths) / 12}. */
    LEGACY,

    /** {@code TCV / (durationInMonths / 12)}. */
    TERM_YEARS
}
