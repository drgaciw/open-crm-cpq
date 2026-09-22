package com.opencrm.common.event.dto;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-018 D5: consumers must tolerate properties they do not know, so fields can be added to a published event
 * without breaking a consumer built against the previous version. D8 added {@code deal} and ARR/TCV/ACV to
 * {@link QuoteAcceptedEvent}; this pins that a strict mapper still reads a payload carrying fields it has never seen.
 */
@DisplayName("QuoteAcceptedEvent tolerates unknown properties (ADR-018 D5)")
class QuoteAcceptedEventToleranceTest {

    private final ObjectMapper strictMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Test
    void readsAPayloadWithUnknownTopLevelAndNestedFields() throws Exception {
        String payload = """
                {
                  "eventType": "QuoteAccepted",
                  "eventVersion": 1,
                  "quoteId": "3f1c7a52-0000-4000-8000-000000000001",
                  "totalAmount": 1200.00,
                  "currencyCode": "USD",
                  "futureTopLevelField": {"anything": true},
                  "pricing": {"totalAmount": 1200.00, "currencyCode": "USD", "futurePricingField": 42},
                  "deal": {"dealExternalId": "deal-uuid-7", "dealType": "RENEWAL", "futureDealField": "x"},
                  "quote": {"quoteNumber": "Q-7", "futureQuoteField": "y"},
                  "customer": {"customerId": "c-1", "futureCustomerField": "z"},
                  "terms": {"termMonths": 12, "futureTermField": 1},
                  "configuration": {"configurationId": "cfg-1", "futureConfigField": 1},
                  "correlation": {"correlationId": "corr-1", "futureCorrelationField": 1}
                }
                """;

        QuoteAcceptedEvent event = strictMapper.readValue(payload, QuoteAcceptedEvent.class);

        assertThat(event.getQuoteId()).isEqualTo("3f1c7a52-0000-4000-8000-000000000001");
        assertThat(event.getDeal().getDealExternalId()).isEqualTo("deal-uuid-7");
        assertThat(event.getPricing().getCurrencyCode()).isEqualTo("USD");
        assertThat(event.getTerms().getTermMonths()).isEqualTo(12);
    }
}
