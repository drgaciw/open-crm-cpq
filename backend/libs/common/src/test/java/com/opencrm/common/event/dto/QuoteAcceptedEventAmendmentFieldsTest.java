package com.opencrm.common.event.dto;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC-S6-1. Direction A: a frozen pre-extension parser reads an event that carries the new fields.
 * Direction B: the current class reads the existing accepted-v1 fixtures, and the new fields stay null.
 */
@DisplayName("QuoteAcceptedEvent amendment fields (AC-S6-1)")
class QuoteAcceptedEventAmendmentFieldsTest {

    private static final ObjectMapper STRICT = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private static final ObjectMapper FIXTURES = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Test
    @DisplayName("AC-S6-1a: old strict parser ignores amendmentDocumentId and applicationUnitNo")
    void oldStrictParserIgnoresNewFields() throws Exception {
        String extended = """
                {
                  "eventType": "QuoteAccepted",
                  "eventVersion": 1,
                  "quoteId": "q-old",
                  "totalAmount": 10.00,
                  "currencyCode": "USD",
                  "amendmentDocumentId": "11111111-1111-4111-8111-0000000000a1",
                  "futureTopLevelField": true,
                  "lineItems": [
                    {
                      "lineUuid": "line-1",
                      "quantity": 2,
                      "applicationUnitNo": 4,
                      "futureLineField": true
                    }
                  ]
                }
                """;

        PreExtensionQuoteAcceptedEvent parsed = STRICT.readValue(extended, PreExtensionQuoteAcceptedEvent.class);

        assertThat(parsed.getQuoteId()).isEqualTo("q-old");
        assertThat(parsed.getEventVersion()).isEqualTo(1);
        assertThat(parsed.getLineItems()).hasSize(1);
        assertThat(parsed.getLineItems().getFirst().getLineUuid()).isEqualTo("line-1");
        assertThat(parsed.getLineItems().getFirst().getQuantity()).isEqualTo(2);
        assertThat(namesOf(PreExtensionQuoteAcceptedEvent.class)).doesNotContain("amendmentDocumentId", "tenantId");
        assertThat(namesOf(PreExtensionQuoteAcceptedEvent.LineItemSnapshot.class)).doesNotContain("applicationUnitNo");
    }

    @Test
    @DisplayName("AC-S6-1: current class round-trips the new fields and omits them when null")
    void currentClassRoundTripsAmendmentFieldsAndOmitsNulls() throws Exception {
        QuoteAcceptedEvent event = new QuoteAcceptedEvent();
        event.setQuoteId("q-1");
        event.setEventType(QuoteAcceptedEvent.EVENT_TYPE);
        event.setEventVersion(QuoteAcceptedEvent.EVENT_VERSION);

        String without = STRICT.writeValueAsString(event);
        assertThat(without).doesNotContain("amendmentDocumentId");
        assertThat(without).doesNotContain("applicationUnitNo");
        assertThat(without).doesNotContain("tenantId");

        event.setAmendmentDocumentId("11111111-1111-4111-8111-0000000000a1");
        event.setLineItems(List.of(QuoteAcceptedEvent.LineItemSnapshot.builder()
                .lineUuid("line-1")
                .quantity(2)
                .applicationUnitNo(4)
                .build()));

        String json = STRICT.writeValueAsString(event);
        assertThat(json).contains("\"amendmentDocumentId\":\"11111111-1111-4111-8111-0000000000a1\"");
        assertThat(json).contains("\"applicationUnitNo\":4");
        assertThat(json).doesNotContain("tenantId");

        QuoteAcceptedEvent read = STRICT.readValue(json, QuoteAcceptedEvent.class);
        assertThat(read.getAmendmentDocumentId()).isEqualTo("11111111-1111-4111-8111-0000000000a1");
        assertThat(read.getLineItems().getFirst().getApplicationUnitNo()).isEqualTo(4);
        assertThat(read.getEventType()).isEqualTo("QuoteAccepted");
        assertThat(read.getEventVersion()).isEqualTo(1);
        assertThat(QuoteAcceptedEvent.EVENT_TYPE).isEqualTo("QuoteAccepted");
        assertThat(QuoteAcceptedEvent.EVENT_VERSION).isEqualTo(1);
    }

    @Test
    @DisplayName("identity equality ignores the additive amendment fields")
    void equalityStaysOnTheExistingIdentityFields() {
        Instant acceptedAt = Instant.parse("2026-10-18T00:00:00Z");
        QuoteAcceptedEvent left = identity(acceptedAt);
        QuoteAcceptedEvent right = identity(acceptedAt);
        right.setAmendmentDocumentId("11111111-1111-4111-8111-0000000000a1");
        right.setLineItems(List.of(QuoteAcceptedEvent.LineItemSnapshot.builder()
                .lineUuid("line-1")
                .applicationUnitNo(9)
                .build()));

        assertThat(left).isEqualTo(right);
        assertThat(left.hashCode()).isEqualTo(right.hashCode());
    }

    @Test
    @DisplayName("AC-S6-1b: current class reads each accepted-v1 fixture with the new fields null")
    void currentClassReadsEachAcceptedV1Fixture() throws Exception {
        Path dir = acceptedV1Dir();
        List<String> names = List.of("hybrid-credit-new.json", "r4-accepted-event.json", "ramp-new.json");
        assertThat(names).allSatisfy(name -> assertThat(dir.resolve(name)).exists());

        for (String name : names) {
            QuoteAcceptedEvent event = FIXTURES.readValue(Files.readString(dir.resolve(name)), QuoteAcceptedEvent.class);
            assertThat(event.getQuoteId()).as(name).isNotBlank();
            assertThat(event.getAmendmentDocumentId()).as(name).isNull();
            if (event.getLineItems() != null) {
                assertThat(event.getLineItems()).as(name).allSatisfy(line ->
                        assertThat(line.getApplicationUnitNo()).isNull());
            }
        }
    }

    private static QuoteAcceptedEvent identity(Instant acceptedAt) {
        QuoteAcceptedEvent event = new QuoteAcceptedEvent();
        event.setQuoteId("q-eq");
        event.setAcceptedAt(acceptedAt);
        event.setCustomerId("cust");
        event.setTotalAmount(new BigDecimal("10.00"));
        event.setCurrencyCode("USD");
        return event;
    }

    private static List<String> namesOf(Class<?> type) {
        return Stream.of(type.getDeclaredFields()).map(Field::getName).toList();
    }

    private static Path acceptedV1Dir() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            Path fixtures = dir.resolve("services")
                    .resolve("contract-asset-management")
                    .resolve("src")
                    .resolve("test")
                    .resolve("resources")
                    .resolve("accepted-v1");
            if (Files.isDirectory(fixtures)) {
                return fixtures;
            }
            Path nested = dir.resolve("backend").resolve("services")
                    .resolve("contract-asset-management")
                    .resolve("src")
                    .resolve("test")
                    .resolve("resources")
                    .resolve("accepted-v1");
            if (Files.isDirectory(nested)) {
                return nested;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("accepted-v1 fixtures not found above " + Path.of("").toAbsolutePath());
    }
}
