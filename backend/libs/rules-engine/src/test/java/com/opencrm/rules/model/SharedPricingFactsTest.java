package com.opencrm.rules.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stabilizes shared pricing facts used by pricing-service Drools rules.
 */
@DisplayName("Shared pricing facts")
class SharedPricingFactsTest {

    @Test
    @DisplayName("QuoteFact exposes fields referenced by approval and renewal rules")
    void quoteFactSupportsPricingServiceRuleBindings() {
        CustomerFact customer = CustomerFact.builder()
                .externalId("customer-1")
                .segment("ENTERPRISE")
                .region("NA")
                .build();

        QuoteFact quote = QuoteFact.builder()
                .externalId("quote-1")
                .durationInMonths(12)
                .basePrice(new BigDecimal("1000.00"))
                .finalPrice(new BigDecimal("850.00"))
                .currency("USD")
                .customer(customer)
                .build();

        assertThat(quote.getDurationInMonths()).isEqualTo(12);
        assertThat(quote.getBasePrice()).isEqualByComparingTo("1000.00");
        assertThat(quote.getFinalPrice()).isEqualByComparingTo("850.00");
        assertThat(quote.getCustomer().getSegment()).isEqualTo("ENTERPRISE");
        assertThat(quote.getString("renewalEligible")).isNull();
    }

    @Test
    @DisplayName("QuoteLineItemFact links to parent quote for join conditions in DRL")
    void quoteLineItemFactLinksToParentQuote() {
        QuoteFact quote = QuoteFact.builder()
                .externalId("quote-1")
                .durationInMonths(12)
                .build();

        QuoteLineItemFact lineItem = QuoteLineItemFact.builder()
                .externalId("line-1")
                .lineItemType("SAAS_SEAT_NAMED")
                .productExternalId("sku-named-seat")
                .quantity(25)
                .listPrice(new BigDecimal("120.00"))
                .finalPrice(new BigDecimal("96.00"))
                .quote(quote)
                .build();

        assertThat(lineItem.getQuote()).isSameAs(quote);
        assertThat(lineItem.getLineItemType()).isEqualTo("SAAS_SEAT_NAMED");
        assertThat(lineItem.getQuantity()).isEqualTo(25);
        assertThat(lineItem.getListPrice()).isEqualByComparingTo("120.00");
    }

    @Test
    @DisplayName("CustomerFact carries segmentation fields used by renewal uplift rules")
    void customerFactSupportsSegmentationBindings() {
        CustomerFact customer = CustomerFact.builder()
                .externalId("customer-1")
                .segment("MID_MARKET")
                .region("EMEA")
                .industry("MANUFACTURING")
                .build();

        assertThat(customer.getSegment()).isEqualTo("MID_MARKET");
        assertThat(customer.getRegion()).isEqualTo("EMEA");
    }

    @Test
    @DisplayName("PricingWaterfallFact tracks waterfall steps and approval state")
    void pricingWaterfallFactSupportsExplanationAndApprovalState() {
        PricingWaterfallFact waterfall = PricingWaterfallFact.builder()
                .listPrice(new BigDecimal("100.00"))
                .basePrice(new BigDecimal("100.00"))
                .netPrice(new BigDecimal("90.00"))
                .approvalState("REQUIRED")
                .build();

        assertThat(waterfall.getListPrice()).isEqualByComparingTo("100.00");
        assertThat(waterfall.getNetPrice()).isEqualByComparingTo("90.00");
        assertThat(waterfall.getApprovalState()).isEqualTo("REQUIRED");
        assertThat(waterfall.getSteps()).isEmpty();
        assertThat(waterfall.getExplanationCodes()).isEmpty();
    }
}
