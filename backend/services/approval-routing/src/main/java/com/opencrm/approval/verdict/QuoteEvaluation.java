package com.opencrm.approval.verdict;

import java.util.List;

/**
 * Minimal {@code EvaluateQuote} result. {@link #breaches()} is {@code ExplainPolicyBreach}. This record does not
 * emit {@code QuoteEvaluated}; that publication belongs to DRG-442.
 */
public record QuoteEvaluation(
        String quoteId,
        String quoteVersion,
        PolicyVerdict verdict,
        List<PolicyBreach> breaches) {
}
