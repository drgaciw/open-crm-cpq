package com.opencrm.quote.approval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.quote.client.QuotePolicyEvaluationRequest;
import com.opencrm.quote.lineitem.QuoteLineItem;
import com.opencrm.quote.quote.Quote;
import com.opencrm.quote.ramp.QuoteRampStep;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuoteVersionFactsFactoryTest {

    private final QuoteVersionFactsFactory factory = new QuoteVersionFactsFactory(new ObjectMapper());

    @Test
    void governingDiscountIsTheHighestRampPeriodNotTheLineBlend() {
        Quote quote = quote();
        QuoteLineItem line = line("0.14", "Software");
        line.setRampSteps(List.of(step(1, "0.32"), step(2, "0.10")));
        quote.setLineItems(List.of(line));

        QuotePolicyEvaluationRequest facts = factory.from(quote);

        assertThat(facts.getDiscountPercent()).isEqualByComparingTo("32");
        assertThat(facts.getProductFamily()).isEqualTo("Software");
        assertThat(facts.getTermMonths()).isEqualTo(24);
        assertThat(facts.getMarginPercent()).isNull();
        assertThat(facts.getQuoteVersion()).isEqualTo(4L);
    }

    @Test
    void staleScheduleUsesTheLineDiscountAndIgnoresThePlaceholderMarginField() {
        Quote quote = quote();
        quote.setRampScheduleStale(true);
        quote.setPricingWaterfallJson("{\"estimatedMarginPct\":40.0}");
        quote.setMetadata("{\"marginPercent\":18.5,\"segment\":\"Enterprise\",\"nonStandardClauses\":[\"NET-90\"]}");
        QuoteLineItem line = line("0.14", "Software");
        line.setRampSteps(List.of(step(1, "0.32")));
        quote.setLineItems(List.of(line));

        QuotePolicyEvaluationRequest facts = factory.from(quote);

        assertThat(facts.getDiscountPercent()).isEqualByComparingTo("14");
        assertThat(facts.getMarginPercent()).isEqualByComparingTo("18.5");
        assertThat(facts.getSegment()).isEqualTo("Enterprise");
        assertThat(facts.getClauseCodes()).containsExactly("NET-90");
    }

    @Test
    void mixedProductFamiliesDoNotInventOneFamily() {
        Quote quote = quote();
        quote.setLineItems(List.of(line("0", "Software"), line("0.05", "Services")));

        QuotePolicyEvaluationRequest facts = factory.from(quote);

        assertThat(facts.getProductFamily()).isNull();
        assertThat(facts.getDiscountPercent()).isEqualByComparingTo("5");
    }

    private static Quote quote() {
        Quote quote = Quote.builder().quoteNumber("Q-1").region("AMER").durationInMonths(24).tenantId("tenant-1").build();
        quote.setId(42L);
        quote.setVersion(4L);
        return quote;
    }

    private static QuoteLineItem line(String discountFraction, String productType) {
        return QuoteLineItem.builder()
                .productExternalId("sku")
                .listPrice(new BigDecimal("100"))
                .discountRate(new BigDecimal(discountFraction))
                .productType(productType)
                .build();
    }

    private static QuoteRampStep step(int sequence, String discountFraction) {
        return QuoteRampStep.builder()
                .sequence(sequence)
                .startMonth(1)
                .months(12)
                .quantity(1)
                .priceBasis("LIST")
                .discountFraction(new BigDecimal(discountFraction))
                .build();
    }
}
