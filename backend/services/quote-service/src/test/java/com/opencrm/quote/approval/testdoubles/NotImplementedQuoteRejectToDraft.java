package com.opencrm.quote.approval.testdoubles;

import com.opencrm.quote.quote.QuoteStatus;

import java.util.Objects;

/**
 * Not-implemented BFF reject. Keeps today's gap: the quote is set to {@link QuoteStatus#REJECTED}, not
 * {@link QuoteStatus#DRAFT}. Writes still go through the quote-status seam (ADR-013: the BFF is the writer).
 */
public final class NotImplementedQuoteRejectToDraft implements QuoteRejectToDraft {

    private final QuoteApprovalLink links;
    private final QuoteStatusStore quotes;

    public NotImplementedQuoteRejectToDraft(QuoteApprovalLink links, QuoteStatusStore quotes) {
        this.links = Objects.requireNonNull(links);
        this.quotes = Objects.requireNonNull(quotes);
    }

    @Override
    public QuoteStatus reject(long approvalId, String comments) {
        long quoteId = links.quoteIdOf(approvalId);
        if (quotes.statusOf(quoteId) == null) {
            throw new IllegalStateException("quote " + quoteId + " missing");
        }
        quotes.update(quoteId, QuoteStatus.REJECTED);
        return QuoteStatus.REJECTED;
    }
}
