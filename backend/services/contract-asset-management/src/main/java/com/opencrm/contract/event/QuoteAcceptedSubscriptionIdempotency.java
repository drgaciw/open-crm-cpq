package com.opencrm.contract.event;

import com.opencrm.common.event.dto.QuoteAcceptedEvent;

/**
 * Idempotency helpers for subscription activation from quote acceptance.
 */
final class QuoteAcceptedSubscriptionIdempotency {

    private QuoteAcceptedSubscriptionIdempotency() {
    }

    static String subscriptionNumber(QuoteAcceptedEvent event) {
        String quoteId = event.getQuoteId();
        if (quoteId == null || quoteId.isBlank()) {
            throw new IllegalArgumentException("QuoteAcceptedEvent requires quoteId");
        }
        return "SUB-" + quoteId;
    }
}
