package com.opencrm.quote.approval;

import java.util.List;

/** Seller-facing EvaluateQuote result. Approval writes stay on {@code POST /approvals}. */
public record QuotePolicyEvaluationResponse(
        Long quoteId,
        Long quoteVersion,
        String verdict,
        List<QuotePolicyVerdict.Breach> breaches) {
}
