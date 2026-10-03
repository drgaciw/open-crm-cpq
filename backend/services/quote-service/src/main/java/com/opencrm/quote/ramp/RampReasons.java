package com.opencrm.quote.ramp;

/**
 * Machine-readable ramp reason codes. The HTTP {@code errorCode} is
 * {@link #SCHEDULE_INVALID}, {@link #NOT_SUPPORTED}, or {@link #SCHEDULE_STALE}.
 * FR-02 reasons travel on the exception, and in its message, so the API body
 * stays readable without a new response type.
 */
public final class RampReasons {

    public static final String NOT_SUPPORTED = "RAMP_NOT_SUPPORTED";
    public static final String SCHEDULE_INVALID = "RAMP_SCHEDULE_INVALID";
    public static final String SCHEDULE_STALE = "RAMP_SCHEDULE_STALE";

    public static final String GAP = "RAMP_GAP";
    public static final String OVERLAP = "RAMP_OVERLAP";
    public static final String TERM_MISMATCH = "RAMP_TERM_MISMATCH";
    public static final String PERIOD_EMPTY = "RAMP_PERIOD_EMPTY";
    public static final String NEGATIVE_VALUE = "RAMP_NEGATIVE_VALUE";
    public static final String NOT_ALLOWED_FOR_LINE_TYPE = "RAMP_NOT_ALLOWED_FOR_LINE_TYPE";
    public static final String TOO_MANY_PERIODS = "RAMP_TOO_MANY_PERIODS";
    public static final String NOT_BILLABLE = "RAMP_NOT_BILLABLE";
    public static final String PRICE_BASIS = "RAMP_PRICE_BASIS";
    public static final String QUANTITY_REQUIRED = "RAMP_QUANTITY_REQUIRED";
    public static final String SEQUENCE = "RAMP_SEQUENCE";
    public static final String ESCALATOR_INVALID = "RAMP_ESCALATOR_INVALID";

    private RampReasons() {
    }
}
