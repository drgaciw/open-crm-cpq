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
        assertThat(event.getEventVersion()).isEqualTo(1);
    }

    @Test
    void carriesTheDealAndCommercialTotalsCrmNeeds() {
        Quote quote = Quote.builder()
                .uuid("quote-uuid-2")
                .quoteNumber("Q-201")
                .dealExternalId("deal-uuid-7")
                .dealType(DealType.RENEWAL)
                .currency("EUR")
                .totalAmount(new BigDecimal("1200.00"))
                .arr(new BigDecimal("1200.00"))
                .tcv(new BigDecimal("3600.00"))
                .acv(new BigDecimal("1200.00"))
                .build();

        QuoteAcceptedEvent event = QuoteAcceptedKafkaEventFactory.from(quote, List.of());

        assertThat(event.getDeal().getDealExternalId()).isEqualTo("deal-uuid-7");
        assertThat(event.getDeal().getDealType()).isEqualTo("RENEWAL");
        assertThat(event.getPricing().getArr()).isEqualByComparingTo("1200.00");
        assertThat(event.getPricing().getTcv()).isEqualByComparingTo("3600.00");
        assertThat(event.getPricing().getAcv()).isEqualByComparingTo("1200.00");
    }

    @Test
    void carriesEveryAcceptedLineInTheQuotesOrder() {
        Quote quote = Quote.builder()
                .uuid("quote-uuid-3")
                .quoteNumber("Q-202")
                .currency("USD")
                .totalAmount(new BigDecimal("24500.00"))
                .build();
        QuoteLineItem support = QuoteLineItem.builder()
                .uuid("line-uuid-support")
                .productExternalId("prod-support")
                .lineItemType("SERVICE")
                .quantity(1)
                .listPrice(new BigDecimal("500.00"))
                .extendedAmount(new BigDecimal("500.00"))
                .sortOrder(2)
                .build();
        QuoteLineItem seats = QuoteLineItem.builder()
                .uuid("line-uuid-seats")
                .productExternalId("prod-seat")
                .productName("Named seat")
                .productType("SUBSCRIPTION")
                .lineItemType("SUBSCRIPTION")
                .quantity(50)
                .unitOfMeasure("SEAT")
                .listPrice(new BigDecimal("50.00"))
                .unitPrice(new BigDecimal("48.00"))
                .discountRate(new BigDecimal("0.1667"))
                .discountAmount(new BigDecimal("400.00"))
                .netPrice(new BigDecimal("40.0000"))
                .extendedAmount(new BigDecimal("2000.00"))
                .billingFrequency("MONTHLY")
                .sortOrder(1)
                .build();

        QuoteAcceptedEvent event = QuoteAcceptedKafkaEventFactory.from(quote, List.of(support, seats));

        assertThat(event.getLineItems())
                .extracting(QuoteAcceptedEvent.LineItemSnapshot::getLineUuid)
                .containsExactly("line-uuid-seats", "line-uuid-support");
        QuoteAcceptedEvent.LineItemSnapshot first = event.getLineItems().getFirst();
        assertThat(first.getProductExternalId()).isEqualTo("prod-seat");
        assertThat(first.getProductName()).isEqualTo("Named seat");
        assertThat(first.getProductType()).isEqualTo("SUBSCRIPTION");
        assertThat(first.getLineItemType()).isEqualTo("SUBSCRIPTION");
        assertThat(first.getQuantity()).isEqualTo(50);
        assertThat(first.getUnitOfMeasure()).isEqualTo("SEAT");
        assertThat(first.getListPrice()).isEqualByComparingTo("50.00");
        assertThat(first.getUnitPrice()).isEqualByComparingTo("48.00");
        assertThat(first.getDiscountRate()).isEqualByComparingTo("0.1667");
        assertThat(first.getDiscountAmount()).isEqualByComparingTo("400.00");
        assertThat(first.getNetPrice()).isEqualByComparingTo("40.0000");
        assertThat(first.getExtendedAmount()).isEqualByComparingTo("2000.00");
        assertThat(first.getBillingFrequency()).isEqualTo("MONTHLY");
        assertThat(first.getSortOrder()).isEqualTo(1);
        assertThat(event.getConfiguration().getAttributes()).containsEntry("lineCount", "2");
    }

    @Test
    void aQuoteWithNoLinesCarriesAnEmptyListNotAMissingField() {
        Quote quote = Quote.builder()
                .uuid("quote-uuid-4")
                .quoteNumber("Q-203")
                .build();

        QuoteAcceptedEvent event = QuoteAcceptedKafkaEventFactory.from(quote, List.of());

        assertThat(event.getLineItems()).isNotNull().isEmpty();
    }
}
