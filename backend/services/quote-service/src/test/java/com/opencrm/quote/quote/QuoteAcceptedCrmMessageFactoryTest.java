package com.opencrm.quote.quote;

import com.opencrm.common.event.dto.QuoteAcceptedCrmMessage;
import com.opencrm.quote.lineitem.QuoteLineItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuoteAcceptedCrmMessageFactoryTest {

    @Test
    void buildsAcceptedPayloadWithQuoteLineCustomerConfigurationPricingAndTermsSnapshots() {
        Instant acceptedAt = Instant.parse("2026-05-12T20:00:00Z");
        Quote quote = Quote.builder()
                .uuid("quote-uuid-1")
                .quoteNumber("Q-100")
                .name("Enterprise expansion")
                .customerExternalId("cust-1")
                .dealExternalId("deal-1")
                .dealType(DealType.EXPANSION)
                .subscriptionType("ANNUAL")
                .currency("USD")
                .region("NA")
                .durationInMonths(24)
                .startDate(LocalDate.parse("2026-06-01"))
                .endDate(LocalDate.parse("2028-05-31"))
                .subtotal(new BigDecimal("1200.00"))
                .totalDiscount(new BigDecimal("120.00"))
                .totalTax(new BigDecimal("108.00"))
                .totalAmount(new BigDecimal("1188.00"))
                .arr(new BigDecimal("594.00"))
                .tcv(new BigDecimal("1188.00"))
                .acv(new BigDecimal("594.00"))
                .approvalState(ApprovalState.APPROVED)
                .approvalTier("tier-2")
                .pricingWaterfallJson("{\"rule\":\"enterprise\"}")
                .metadata("{\"segment\":\"strategic\"}")
                .acceptedAt(acceptedAt)
                .build();

        QuoteLineItem line = QuoteLineItem.builder()
                .uuid("line-uuid-1")
                .productExternalId("prod-1")
                .productName("CRM Seats")
                .productType("SUBSCRIPTION")
                .lineItemType("NEW")
                .quantity(10)
                .unitOfMeasure("seat")
                .listPrice(new BigDecimal("120.00"))
                .unitPrice(new BigDecimal("108.00"))
                .discountRate(new BigDecimal("0.1000"))
                .discountAmount(new BigDecimal("120.00"))
                .netPrice(new BigDecimal("108.00"))
                .extendedAmount(new BigDecimal("1080.00"))
                .billingFrequency("ANNUAL")
                .sortOrder(1)
                .pricingExplanationCode("VOLUME_DISCOUNT")
                .pricingWaterfallJson("{\"lineRule\":\"volume\"}")
                .metadata("{\"configurationId\":\"cfg-1\"}")
                .build();

        QuoteAcceptedCrmMessage message = QuoteAcceptedCrmMessageFactory.from(quote, List.of(line));

        assertThat(message.getSchemaVersion()).isEqualTo("QuoteAccepted.v1");
        assertThat(message.getQuoteNumber()).isEqualTo("Q-100");
        assertThat(message.getQuoteUuid()).isEqualTo("quote-uuid-1");
        assertThat(message.getAcceptedAt()).isEqualTo(acceptedAt);
        assertThat(message.getCustomer().getCustomerExternalId()).isEqualTo("cust-1");
        assertThat(message.getDeal().getDealExternalId()).isEqualTo("deal-1");
        assertThat(message.getConfiguration().getRegion()).isEqualTo("NA");
        assertThat(message.getPricing().getTotalTax()).isEqualByComparingTo("108.00");
        assertThat(message.getPricing().getPricingWaterfallJson()).isEqualTo("{\"rule\":\"enterprise\"}");
        assertThat(message.getTerms().getStartDate()).isEqualTo(LocalDate.parse("2026-06-01"));
        assertThat(message.getTerms().getEndDate()).isEqualTo(LocalDate.parse("2028-05-31"));
        assertThat(message.getLineItems()).singleElement().satisfies(snapshot -> {
            assertThat(snapshot.getLineUuid()).isEqualTo("line-uuid-1");
            assertThat(snapshot.getProductExternalId()).isEqualTo("prod-1");
            assertThat(snapshot.getQuantity()).isEqualTo(10);
            assertThat(snapshot.getExtendedAmount()).isEqualByComparingTo("1080.00");
            assertThat(snapshot.getPricingWaterfallJson()).isEqualTo("{\"lineRule\":\"volume\"}");
            assertThat(snapshot.getMetadata()).isEqualTo("{\"configurationId\":\"cfg-1\"}");
        });
    }
}
