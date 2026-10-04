package com.opencrm.common.event.dto;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

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

    /**
     * Outbox rows written before {@code lineItems} existed are still in every producer schema (the relay has never
     * run). They must parse, and the missing field must stay distinguishable from a quote with no lines.
     */
    @Test
    void readsAPayloadWrittenBeforeLineItemsExisted() throws Exception {
        String payload = """
                {
                  "eventType": "QuoteAccepted",
                  "eventVersion": 1,
                  "quoteId": "3f1c7a52-0000-4000-8000-000000000002",
                  "totalAmount": 900.00,
                  "currencyCode": "USD",
                  "configuration": {"configurationId": "cfg-2", "attributes": {"lineCount": "2"}}
                }
                """;

        QuoteAcceptedEvent event = strictMapper.readValue(payload, QuoteAcceptedEvent.class);

        assertThat(event.getQuoteId()).isEqualTo("3f1c7a52-0000-4000-8000-000000000002");
        assertThat(event.getLineItems()).isNull();
    }

    /** A later additive field on a line, such as the ADR-023 ramp periods, must not break today's consumers. */
    @Test
    void readsLineItemsCarryingFieldsTheConsumerDoesNotKnow() throws Exception {
        String payload = """
                {
                  "eventType": "QuoteAccepted",
                  "eventVersion": 1,
                  "quoteId": "3f1c7a52-0000-4000-8000-000000000003",
                  "lineItems": [
                    {
                      "lineUuid": "line-1",
                      "productExternalId": "prod-seat",
                      "lineItemType": "SUBSCRIPTION",
                      "quantity": 50,
                      "netPrice": 40.0000,
                      "extendedAmount": 24000.00,
                      "sortOrder": 1,
                      "ramp": [{"sequence": 1, "startMonth": 1, "months": 12}]
                    }
                  ]
                }
                """;

        QuoteAcceptedEvent event = strictMapper.readValue(payload, QuoteAcceptedEvent.class);

        assertThat(event.getLineItems()).hasSize(1);
        QuoteAcceptedEvent.LineItemSnapshot line = event.getLineItems().getFirst();
        assertThat(line.getLineUuid()).isEqualTo("line-1");
        assertThat(line.getQuantity()).isEqualTo(50);
        assertThat(line.getExtendedAmount()).isEqualByComparingTo("24000.00");
    }

    @Test
    void lineItemsSurviveARoundTrip() throws Exception {
        QuoteAcceptedEvent event = new QuoteAcceptedEvent();
        event.setQuoteId("3f1c7a52-0000-4000-8000-000000000004");
        event.setLineItems(List.of(QuoteAcceptedEvent.LineItemSnapshot.builder()
                .lineUuid("line-9")
                .productExternalId("prod-support")
                .lineItemType("SERVICE")
                .quantity(1)
                .unitPrice(new BigDecimal("500.00"))
                .netPrice(new BigDecimal("450.0000"))
                .extendedAmount(new BigDecimal("450.00"))
                .billingFrequency("ANNUAL")
                .sortOrder(3)
                .build()));

        String json = strictMapper.writeValueAsString(event);
        QuoteAcceptedEvent read = strictMapper.readValue(json, QuoteAcceptedEvent.class);

        assertThat(read.getLineItems()).containsExactlyElementsOf(event.getLineItems());
    }
}
