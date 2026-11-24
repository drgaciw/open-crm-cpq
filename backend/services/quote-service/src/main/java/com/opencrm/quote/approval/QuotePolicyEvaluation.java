package com.opencrm.quote.approval;

import com.opencrm.quote.quote.Quote;

/**
 * Calls {@code EvaluateQuote} for a quote version. The not-configured seam keeps tests that construct the
 * approval BFF directly on the pre-evaluator submit path.
 */
public interface QuotePolicyEvaluation {

    QuotePolicyVerdict evaluate(Quote quote);

    static QuotePolicyEvaluation notConfigured() {
        return quote -> QuotePolicyVerdict.notConfigured();
    }
}
