package com.opencrm.document.event;

import com.opencrm.common.event.dto.QuoteAcceptedEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuoteAcceptedDocumentIdempotencyTest {

    @Test
    void usesQuoteIdWhenCorrelationMissing() {
        QuoteAcceptedEvent event = QuoteAcceptedEvent.v1(
                QuoteAcceptedEvent.QuoteSnapshot.builder().quoteId("quote-1").build(),
                null,
                null,
                null,
                null,
                null
        );

        assertThat(QuoteAcceptedDocumentIdempotency.proposalPdfKey(event))
                .isEqualTo("quote-quote-1-pdf");
    }

    @Test
    void includesCorrelationWhenPresent() {
        QuoteAcceptedEvent event = QuoteAcceptedEvent.v1(
                QuoteAcceptedEvent.QuoteSnapshot.builder().quoteId("quote-1").build(),
                null,
                null,
                null,
                null,
                QuoteAcceptedEvent.CorrelationSnapshot.builder().correlationId("corr-9").build()
        );

        assertThat(QuoteAcceptedDocumentIdempotency.proposalPdfKey(event))
                .isEqualTo("quote-quote-1-corr-corr-9-pdf");
    }
}
