package com.opencrm.quote.quote;

import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.dto.QuoteAcceptedEvent;
import com.opencrm.quote.lineitem.QuoteLineItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuoteAcceptedKafkaEventFactoryTest {

    @Test
    void buildsPublishedLanguageQuoteAcceptedEventFromQuoteSnapshots() {
        Instant acceptedAt = Instant.parse("2026-05-16T12:00:00Z");
        Quote quote = Quote.builder()
                .uuid("quote-uuid-1")
                .quoteNumber("Q-200")
                .customerExternalId("cust-9")
                .currency("USD")
                .region("NA")
                .subscriptionType("ANNUAL")
                .durationInMonths(12)
                .startDate(LocalDate.parse("2026-06-01"))
                .endDate(LocalDate.parse("2027-05-31"))
                .totalAmount(new BigDecimal("5000.00"))
                .metadata("cfg-42")
                .acceptedAt(acceptedAt)
                .build();

        QuoteLineItem line = QuoteLineItem.builder()
                .productExternalId("prod-1")
                .quantity(5)
                .build();

        QuoteAcceptedEvent event = QuoteAcceptedKafkaEventFactory.from(quote, List.of(line));

        assertThat(event.getEventType()).isEqualTo(EventContractRegistry.QUOTE_ACCEPTED_V1.eventType());
        assertThat(event.getEventVersion()).isEqualTo(1);
        assertThat(event.getQuoteId()).isEqualTo("quote-uuid-1");
        assertThat(event.getQuote().getQuoteNumber()).isEqualTo("Q-200");
        assertThat(event.getCustomerId()).isEqualTo("cust-9");
        assertThat(event.getTotalAmount()).isEqualByComparingTo("5000.00");
        assertThat(event.getConfiguration().getConfigurationId()).isEqualTo("cfg-42");
        assertThat(event.getTerms().getTermMonths()).isEqualTo(12);
        assertThat(event.getCorrelation().getSourceService()).isEqualTo("quote-service");
        assertThat(event.toPublishedLanguageV1().getSchemaVersion()).isEqualTo(1);
    }
}
