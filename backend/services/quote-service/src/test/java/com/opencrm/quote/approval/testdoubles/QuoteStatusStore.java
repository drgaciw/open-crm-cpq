package com.opencrm.quote.approval.testdoubles;

import com.opencrm.quote.quote.QuoteStatus;

/** Quote status seam for the BFF reject spec. Feature reads and writes {@code Quote.status}. */
public interface QuoteStatusStore {

    QuoteStatus statusOf(long quoteId);

    void update(long quoteId, QuoteStatus status);
}
