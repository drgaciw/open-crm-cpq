package com.opencrm.common.event.dto.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PublishedLanguageEventV1Test {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Test
    void serializesQuoteAcceptedWithSchemaVersion() throws Exception {
        QuoteAcceptedEventV1 event = QuoteAcceptedEventV1.builder()
                .quoteId("quote-1")
                .acceptedAt(Instant.parse("2026-05-12T00:00:00Z"))
                .customerId("customer-1")
                .totalAmount(new BigDecimal("1250.00"))
                .currencyCode("USD")
                .build();

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(event));

        assertThat(json.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(json.get("quoteId").asText()).isEqualTo("quote-1");
    }

    @Test
    void serializesPriceCalculatedPlaceholderWithLines() throws Exception {
        PriceCalculatedEventV1 event = PriceCalculatedEventV1.builder()
                .quoteId("quote-1")
                .calculatedAt(Instant.parse("2026-05-12T00:00:00Z"))
                .success(true)
                .subtotalAmount(new BigDecimal("100.00"))
                .discountAmount(new BigDecimal("10.00"))
                .totalAmount(new BigDecimal("90.00"))
                .currencyCode("USD")
                .lines(List.of(PriceCalculatedEventV1.PriceCalculatedLineV1.builder()
                        .lineId("line-1")
                        .productExternalId("sku-1")
                        .quantity(2)
                        .finalPrice(new BigDecimal("90.00"))
                        .build()))
                .explanationCodes(List.of("BASE_PRICE", "DISCOUNT_APPLIED"))
                .approvalState("NOT_REQUIRED")
                .build();

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(event));

        assertThat(json.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(json.get("lines")).hasSize(1);
        assertThat(json.get("lines").get(0).get("lineId").asText()).isEqualTo("line-1");
    }

    @Test
    void serializesOtherPublishedLanguageContractsWithSchemaVersion() throws Exception {
        assertSchemaVersion(DiscountApprovalRequiredEventV1.builder()
                .quoteId("quote-1")
                .lineId("line-1")
                .marginDelta(new BigDecimal("15.00"))
                .requestedDiscount(new BigDecimal("25.00"))
                .currencyCode("USD")
                .build());
        assertSchemaVersion(ProductRetiredEventV1.builder()
                .productId("product-1")
                .retiredAt(Instant.parse("2026-05-12T00:00:00Z"))
                .reasonCode("REPLACED")
                .build());
        assertSchemaVersion(SubscriptionRenewalEligibleEventV1.builder()
                .subscriptionId("subscription-1")
                .renewalDate(LocalDate.of(2026, 6, 1))
                .currentPlan("enterprise")
                .usageBand("high")
                .build());
    }

    private void assertSchemaVersion(Object event) throws Exception {
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(event));

        assertThat(json.get("schemaVersion").asInt()).isEqualTo(1);
    }
}
