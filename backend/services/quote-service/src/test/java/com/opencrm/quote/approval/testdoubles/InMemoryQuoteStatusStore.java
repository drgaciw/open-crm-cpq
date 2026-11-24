package com.opencrm.quote.approval.testdoubles;

import com.opencrm.quote.quote.QuoteStatus;

import java.util.HashMap;
import java.util.Map;

/** In-memory quote statuses for {@code RejectToDraftTest}. */
public final class InMemoryQuoteStatusStore implements QuoteStatusStore {

    private final Map<Long, QuoteStatus> statuses = new HashMap<>();

    public void put(long quoteId, QuoteStatus status) {
        statuses.put(quoteId, status);
    }

    @Override
    public QuoteStatus statusOf(long quoteId) {
        return statuses.get(quoteId);
    }

    @Override
    public void update(long quoteId, QuoteStatus status) {
        statuses.put(quoteId, status);
    }
}
