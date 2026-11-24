package com.opencrm.approval.domain;

/**
 * One threshold a quote version does not satisfy. {@link #actual} and {@link #limit} are the values
 * {@code ExplainPolicyBreach} reports. {@code "unmeasured"} means the quote version did not carry the fact.
 */
public record PolicyBreach(
        Long policyId,
        Integer policyVersion,
        ThresholdType thresholdType,
        String actual,
        String limit,
        BreachEffect effect,
        String reasonCode) {
}
