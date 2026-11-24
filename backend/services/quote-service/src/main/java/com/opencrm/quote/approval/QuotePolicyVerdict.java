package com.opencrm.quote.approval;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Verdict from approval-routing's EvaluateQuote. {@code configured} is false only for the test seam that
 * has no evaluator; production always calls approval-routing.
 */
public record QuotePolicyVerdict(
        boolean configured,
        String verdict,
        Long quoteVersion,
        List<Breach> breaches) {

    public QuotePolicyVerdict {
        breaches = breaches == null ? List.of() : List.copyOf(breaches);
    }

    public record Breach(
            Long policyId,
            Integer policyVersion,
            String thresholdType,
            String actual,
            String limit,
            String reasonCode) {
    }

    public static QuotePolicyVerdict notConfigured() {
        return new QuotePolicyVerdict(false, "PASS", null, List.of());
    }

    /** A blocked quote must not be written to approval-routing. */
    public boolean refusesSubmission() {
        return configured && "BLOCKED".equals(verdict);
    }

    public List<String> reasonCodes() {
        return breaches.stream()
                .map(Breach::reasonCode)
                .filter(code -> code != null && !code.isBlank())
                .distinct()
                .toList();
    }

    public String summary() {
        if (breaches.isEmpty()) {
            return "Commercial policy blocked this quote";
        }
        return breaches.stream()
                .map(breach -> breach.thresholdType() + " actual " + breach.actual() + " against limit " + breach.limit())
                .collect(Collectors.joining("; "));
    }
}
