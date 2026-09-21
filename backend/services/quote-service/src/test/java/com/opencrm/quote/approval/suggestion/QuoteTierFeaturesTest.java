package com.opencrm.quote.approval.suggestion;

import com.opencrm.quote.lineitem.QuoteLineItem;
import com.opencrm.quote.quote.DealType;
import com.opencrm.quote.quote.Quote;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("QuoteTierFeatures (ADR-015 data minimisation)")
class QuoteTierFeaturesTest {

    private static Quote quote() {
        Quote quote = new Quote();
        quote.setQuoteNumber("Q-2026-000417");
        quote.setName("Acme Corp renewal for Jane Doe");
        quote.setDescription("Negotiated by John Smith, call +1 555 0100");
        quote.setNotes("jane.doe@acme.example asked for 25% off");
        quote.setMetadata("{\"crmAccount\":\"ACME-77\"}");
        quote.setCustomerExternalId("CUST-ACME-77");
        quote.setDealExternalId("DEAL-9001");
        quote.setDealType(DealType.RENEWAL);
        quote.setCurrency("usd");
        quote.setRegion("emea");
        quote.setDurationInMonths(24);
        quote.setSubtotal(new BigDecimal("80000"));
        quote.setTotalDiscount(new BigDecimal("20000"));
        quote.setTotalAmount(new BigDecimal("60000"));
        quote.setLineItems(new ArrayList<>(List.of(new QuoteLineItem(), new QuoteLineItem(), new QuoteLineItem())));
        return quote;
    }

    @Test
    @DisplayName("sends only the shape of the deal")
    void sendsDealShapeOnly() {
        assertThat(QuoteTierFeatures.state(quote()).canonicalJson()).isEqualTo(
                "{\"currency\":\"USD\",\"deal_type\":\"RENEWAL\",\"discount_pct\":25.0,\"duration_months\":24,"
                        + "\"line_count\":3,\"region\":\"EMEA\",\"total_amount_band\":\"50K_250K\"}");
    }

    @Test
    @DisplayName("no name, identifier, note or exact amount reaches the payload")
    void nothingIdentifyingLeaks() {
        String payload = QuoteTierFeatures.state(quote()).canonicalJson().toLowerCase();

        assertThat(payload).doesNotContain("acme", "jane", "john", "555", "@", "cust-", "deal-9001",
                "q-2026", "60000", "80000");
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(value = {
            "NULL, LT_10K", "0, LT_10K", "9999.99, LT_10K", "10000, 10K_50K", "49999, 10K_50K",
            "50000, 50K_250K", "249999, 50K_250K", "250000, 250K_1M", "999999, 250K_1M", "1000000, GT_1M"
    }, nullValues = "NULL")
    @DisplayName("total amount is banded")
    void amountBands(BigDecimal amount, String expected) {
        assertThat(QuoteTierFeatures.amountBand(amount)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "subtotal {0}, discount {1} -> {2}%")
    @CsvSource(value = {
            "80000, 20000, 25.0", "1000, 333, 33.3", "0, 50, 0", "NULL, 50, 0", "1000, NULL, 0", "1000, -50, 0"
    }, nullValues = "NULL")
    @DisplayName("discount is a percentage of subtotal and never negative or a division by zero")
    void discountPct(BigDecimal subtotal, BigDecimal discount, double expected) {
        assertThat(QuoteTierFeatures.discountPct(subtotal, discount)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(value = {
            "emea, EMEA", "NA, NA", "North America (Jane's patch), OTHER", "X, OTHER", "'', NULL", "NULL, NULL"
    }, nullValues = "NULL")
    @DisplayName("free-text region collapses to a short code or OTHER")
    void regionCode(String raw, String expected) {
        assertThat(QuoteTierFeatures.code(raw, 2, 8)).isEqualTo(expected);
    }

    @Test
    @DisplayName("question keys are exactly the ApprovalTier names")
    void questionKeysMatchEnum() {
        assertThat(QuoteTierFeatures.QUESTION.criteria().keySet()).containsExactly("DEAL_DESK", "SALES_VP", "CFO");
    }
}
