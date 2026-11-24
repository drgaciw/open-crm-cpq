package com.opencrm.approval.domain;

/**
 * How the roles in one matrix step are requested. {@link #SERIAL} is a single role that must finish before the next
 * step. {@link #PARALLEL} requests every role in the step together. Advancing a later step is DRG-444.
 */
public enum MatrixStepMode {
    SERIAL,
    PARALLEL
}
