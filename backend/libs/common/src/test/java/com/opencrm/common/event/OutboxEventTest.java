package com.opencrm.common.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OutboxEvent Entity")
class OutboxEventTest {

    @Test
    @DisplayName("should mark as published and set publishedAt timestamp")
    void markAsPublished() {
        OutboxEvent event = new OutboxEvent();

        assertFalse(event.getPublished(), "Should default to false");
        assertNull(event.getPublishedAt(), "publishedAt should initially be null");

        event.markAsPublished();

        assertTrue(event.getPublished(), "Should be marked as published");
        assertNotNull(event.getPublishedAt(), "publishedAt should be set");
    }

    @Test
    @DisplayName("should increment retry count and set error message")
    void incrementRetryCount() {
        OutboxEvent event = new OutboxEvent();

        assertEquals(0, event.getRetryCount(), "Should default to 0");
        assertNull(event.getErrorMessage(), "Error message should initially be null");

        event.incrementRetryCount("Connection timeout");

        assertEquals(1, event.getRetryCount(), "Retry count should be incremented");
        assertEquals("Connection timeout", event.getErrorMessage(), "Error message should be set");

        event.incrementRetryCount("Connection refused");

        assertEquals(2, event.getRetryCount(), "Retry count should be incremented again");
        assertEquals("Connection refused", event.getErrorMessage(), "Error message should be updated");
    }

    @Test
    @DisplayName("new rows are PENDING and due now")
    void newRowIsPendingAndDue() {
        OutboxEvent event = OutboxEvent.builder().aggregateType("Quote").aggregateId("q").eventType("t").payload("{}").build();

        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertNull(event.getNextAttemptAt());
    }

    @Test
    @DisplayName("publishing sets status PUBLISHED and clears any pending retry")
    void markAsPublishedSetsStatus() {
        OutboxEvent event = new OutboxEvent();
        Instant ackedAt = Instant.parse("2026-09-22T12:00:00Z");
        event.setNextAttemptAt(ackedAt.minusSeconds(1));

        event.markAsPublished(ackedAt);

        assertEquals(OutboxStatus.PUBLISHED, event.getStatus());
        assertEquals(ackedAt, event.getPublishedAt());
        assertNull(event.getNextAttemptAt());
    }

    @Test
    @DisplayName("a failed attempt schedules a retry until the limit, then marks the row FAILED")
    void recordFailedAttempt() {
        OutboxEvent event = new OutboxEvent();
        Instant now = Instant.parse("2026-09-22T12:00:00Z");

        assertFalse(event.recordFailedAttempt("timeout", now, 2, Duration.ofSeconds(5)));
        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertEquals(now.plusSeconds(5), event.getNextAttemptAt());

        assertTrue(event.recordFailedAttempt("timeout", now, 2, Duration.ofSeconds(10)));
        assertEquals(OutboxStatus.FAILED, event.getStatus());
        assertNull(event.getNextAttemptAt());
        assertFalse(event.getPublished());
    }

    @Test
    @DisplayName("error messages are truncated to the 500-character column")
    void errorMessageIsTruncated() {
        OutboxEvent event = new OutboxEvent();

        event.incrementRetryCount("x".repeat(800));

        assertEquals(500, event.getErrorMessage().length());
    }
}
