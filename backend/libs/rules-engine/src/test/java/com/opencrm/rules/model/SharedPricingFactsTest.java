package com.opencrm.rules.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SharedPricingFactsTest {

    @Test
    void allSharedPricingFactsExposeConsistentTypedMetadataAccess() {
        List<MetadataBackedFact> facts = List.of(
                QuoteFact.builder().build(),
                QuoteLineItemFact.builder().build(),
                CustomerFact.builder().build(),
                ProductFact.builder().build(),
                PricingWaterfallFact.builder().build());

        for (MetadataBackedFact fact : facts) {
            fact.setString("source", "seeded-catalog");
            fact.setInt("termMonths", 12);
            fact.setDecimal("discountRate", new BigDecimal("0.12"));
            fact.setBoolean("eligible", true);

            assertThat(fact.getString("source")).isEqualTo("seeded-catalog");
            assertThat(fact.getInt("termMonths")).isEqualTo(12);
            assertThat(fact.getDecimal("discountRate")).isEqualByComparingTo("0.12");
            assertThat(fact.getBoolean("eligible")).isTrue();
        }
    }

    @Test
    void metadataReadersTolerateStringAndNumberInputsFromDrlAndJsonBoundaries() {
        QuoteFact quote = QuoteFact.builder().build();
        quote.getMetadata().put("seatCount", "500");
        quote.getMetadata().put("marginPct", 0.3d);
        quote.getMetadata().put("requiresApproval", "true");

        assertThat(quote.getInt("seatCount")).isEqualTo(500);
        assertThat(quote.getDecimal("marginPct")).isEqualByComparingTo("0.3");
        assertThat(quote.getBoolean("requiresApproval")).isTrue();
        assertThat(quote.getInt("notANumber")).isNull();
        assertThat(quote.getDecimal("notADecimal")).isNull();
    }
}
