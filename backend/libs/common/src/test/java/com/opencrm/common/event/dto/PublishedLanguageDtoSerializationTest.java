package com.opencrm.common.event.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Published language DTO serialization contracts")
class PublishedLanguageDtoSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    @DisplayName("QuoteAcceptedEvent keeps the Kafka field contract")
    void quoteAcceptedEventKeepsKafkaFieldContract() throws Exception {
        QuoteAcceptedEvent event = new QuoteAcceptedEvent(
                "quote-123",
                Instant.parse("2026-05-12T12:34:56Z"),
                "customer-456",
                new BigDecimal("1999.95"),
                "USD");

        JsonNode json = objectMapper.valueToTree(event);

        assertFieldNames(json, "quoteId", "acceptedAt", "customerId", "totalAmount", "currencyCode");
        assertEquals("quote-123", json.get("quoteId").asText());
        assertEquals("2026-05-12T12:34:56Z", json.get("acceptedAt").asText());
        assertEquals("customer-456", json.get("customerId").asText());
        assertEquals(0, new BigDecimal("1999.95").compareTo(json.get("totalAmount").decimalValue()));
        assertEquals("USD", json.get("currencyCode").asText());

        QuoteAcceptedEvent roundTrip = objectMapper.treeToValue(json, QuoteAcceptedEvent.class);
        assertEquals(event, roundTrip);
    }

    @Test
    @DisplayName("DiscountApprovalRequiredEvent keeps the pricing approval field contract")
    void discountApprovalRequiredEventKeepsPricingApprovalFieldContract() throws Exception {
        DiscountApprovalRequiredEvent event = new DiscountApprovalRequiredEvent(
                "quote-123",
                "line-1",
                new BigDecimal("-125.50"),
                new BigDecimal("15.00"),
                "USD");

        JsonNode json = objectMapper.valueToTree(event);

        assertFieldNames(json, "quoteId", "lineId", "marginDelta", "requestedDiscount", "currencyCode");
        assertEquals("quote-123", json.get("quoteId").asText());
        assertEquals("line-1", json.get("lineId").asText());
        assertEquals(0, new BigDecimal("-125.50").compareTo(json.get("marginDelta").decimalValue()));
        assertEquals(0, new BigDecimal("15.00").compareTo(json.get("requestedDiscount").decimalValue()));
        assertEquals("USD", json.get("currencyCode").asText());

        DiscountApprovalRequiredEvent roundTrip = objectMapper.treeToValue(json, DiscountApprovalRequiredEvent.class);
        assertEquals(event.getQuoteId(), roundTrip.getQuoteId());
        assertEquals(event.getLineId(), roundTrip.getLineId());
        assertEquals(0, event.getMarginDelta().compareTo(roundTrip.getMarginDelta()));
        assertEquals(0, event.getRequestedDiscount().compareTo(roundTrip.getRequestedDiscount()));
        assertEquals(event.getCurrencyCode(), roundTrip.getCurrencyCode());
    }

    @Test
    @DisplayName("ProductRetiredEvent keeps the product lifecycle field contract")
    void productRetiredEventKeepsProductLifecycleFieldContract() throws Exception {
        ProductRetiredEvent event = new ProductRetiredEvent(
                "product-789",
                Instant.parse("2026-05-12T12:34:56Z"),
                "END_OF_LIFE");

        JsonNode json = objectMapper.valueToTree(event);

        assertFieldNames(json, "productId", "retiredAt", "reasonCode");
        assertEquals("product-789", json.get("productId").asText());
        assertEquals("2026-05-12T12:34:56Z", json.get("retiredAt").asText());
        assertEquals("END_OF_LIFE", json.get("reasonCode").asText());

        ProductRetiredEvent roundTrip = objectMapper.treeToValue(json, ProductRetiredEvent.class);
        assertEquals(event, roundTrip);
    }

    @Test
    @DisplayName("SubscriptionRenewalEligibleEvent keeps the renewal field contract")
    void subscriptionRenewalEligibleEventKeepsRenewalFieldContract() throws Exception {
        SubscriptionRenewalEligibleEvent event = new SubscriptionRenewalEligibleEvent(
                "subscription-123",
                LocalDate.parse("2026-06-01"),
                "ENTERPRISE",
                "HIGH_USAGE");

        JsonNode json = objectMapper.valueToTree(event);

        assertFieldNames(json, "subscriptionId", "renewalDate", "currentPlan", "usageBand");
        assertEquals("subscription-123", json.get("subscriptionId").asText());
        assertEquals("2026-06-01", json.get("renewalDate").asText());
        assertEquals("ENTERPRISE", json.get("currentPlan").asText());
        assertEquals("HIGH_USAGE", json.get("usageBand").asText());

        SubscriptionRenewalEligibleEvent roundTrip =
                objectMapper.treeToValue(json, SubscriptionRenewalEligibleEvent.class);
        assertEquals(event, roundTrip);
    }

    @Test
    @DisplayName("QuoteAcceptedCrmMessage keeps the Redis CRM field contract")
    void quoteAcceptedCrmMessageKeepsRedisCrmFieldContract() throws Exception {
        QuoteAcceptedCrmMessage message = QuoteAcceptedCrmMessage.builder()
                .quoteNumber("Q-1001")
                .quoteUuid("quote-uuid-123")
                .customerExternalId("customer-456")
                .dealExternalId("deal-789")
                .dealType("NEW_BUSINESS")
                .totalAmount(new BigDecimal("12000.00"))
                .arr(new BigDecimal("6000.00"))
                .tcv(new BigDecimal("12000.00"))
                .acv(new BigDecimal("6000.00"))
                .currency("USD")
                .durationInMonths(24)
                .acceptedAt(Instant.parse("2026-05-12T12:34:56Z"))
                .build();

        JsonNode json = objectMapper.valueToTree(message);

        assertFieldNames(json,
                "quoteNumber",
                "quoteUuid",
                "customerExternalId",
                "dealExternalId",
                "dealType",
                "totalAmount",
                "arr",
                "tcv",
                "acv",
                "currency",
                "durationInMonths",
                "acceptedAt");
        assertEquals("Q-1001", json.get("quoteNumber").asText());
        assertEquals("quote-uuid-123", json.get("quoteUuid").asText());
        assertEquals("customer-456", json.get("customerExternalId").asText());
        assertEquals("deal-789", json.get("dealExternalId").asText());
        assertEquals("NEW_BUSINESS", json.get("dealType").asText());
        assertEquals(0, new BigDecimal("12000.00").compareTo(json.get("totalAmount").decimalValue()));
        assertEquals(0, new BigDecimal("6000.00").compareTo(json.get("arr").decimalValue()));
        assertEquals(0, new BigDecimal("12000.00").compareTo(json.get("tcv").decimalValue()));
        assertEquals(0, new BigDecimal("6000.00").compareTo(json.get("acv").decimalValue()));
        assertEquals("USD", json.get("currency").asText());
        assertEquals(24, json.get("durationInMonths").asInt());
        assertEquals("2026-05-12T12:34:56Z", json.get("acceptedAt").asText());

        QuoteAcceptedCrmMessage roundTrip = objectMapper.treeToValue(json, QuoteAcceptedCrmMessage.class);
        assertEquals(message.getQuoteNumber(), roundTrip.getQuoteNumber());
        assertEquals(message.getQuoteUuid(), roundTrip.getQuoteUuid());
        assertEquals(message.getCustomerExternalId(), roundTrip.getCustomerExternalId());
        assertEquals(message.getDealExternalId(), roundTrip.getDealExternalId());
        assertEquals(message.getDealType(), roundTrip.getDealType());
        assertEquals(0, message.getTotalAmount().compareTo(roundTrip.getTotalAmount()));
        assertEquals(0, message.getArr().compareTo(roundTrip.getArr()));
        assertEquals(0, message.getTcv().compareTo(roundTrip.getTcv()));
        assertEquals(0, message.getAcv().compareTo(roundTrip.getAcv()));
        assertEquals(message.getCurrency(), roundTrip.getCurrency());
        assertEquals(message.getDurationInMonths(), roundTrip.getDurationInMonths());
        assertEquals(message.getAcceptedAt(), roundTrip.getAcceptedAt());
    }

    private void assertFieldNames(JsonNode json, String... expectedFieldNames) {
        Iterator<String> names = json.fieldNames();
        Iterable<String> iterable = () -> names;
        Set<String> actualFieldNames = StreamSupport.stream(iterable.spliterator(), false)
                .collect(Collectors.toSet());

        assertEquals(Set.of(expectedFieldNames), actualFieldNames);
    }
}
