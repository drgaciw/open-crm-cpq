package com.opencrm.common.event;

/**
 * Thrown by a Kafka listener when a record can never be processed, however often it is redelivered: an unreadable
 * payload, a missing mandatory field, a value outside the contract. The shared error handler (ADR-018 D7) sends such
 * records straight to {@code <topic>.DLT} instead of retrying them.
 */
public class NonRetryableEventException extends RuntimeException {

    public NonRetryableEventException(String message, Throwable cause) {
        super(message, cause);
    }

    public NonRetryableEventException(String message) {
        super(message);
    }
}
