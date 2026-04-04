package com.opencrm.common.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
