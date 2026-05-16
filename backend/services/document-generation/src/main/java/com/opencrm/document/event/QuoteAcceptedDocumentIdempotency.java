package com.opencrm.document.event;

import com.opencrm.common.event.dto.QuoteAcceptedEvent;

/**
 * Stable idempotency keys for quote-accepted document generation jobs.
 */
final class QuoteAcceptedDocumentIdempotency {

    private QuoteAcceptedDocumentIdempotency() {
    }

    static String proposalPdfKey(QuoteAcceptedEvent event) {
        String quoteId = event.getQuoteId();
        if (quoteId == null || quoteId.isBlank()) {
            throw new IllegalArgumentException("QuoteAcceptedEvent requires quoteId for idempotency");
        }
        String correlation = event.getCorrelation() != null ? event.getCorrelation().getCorrelationId() : null;
        if (correlation != null && !correlation.isBlank()) {
            return "quote-" + quoteId + "-corr-" + correlation + "-pdf";
        }
        return "quote-" + quoteId + "-pdf";
    }
}
