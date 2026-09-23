package com.opencrm.common.event;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ADR-018 D4: event correlation scope and its Kafka record interceptor")
class EventCorrelationTest {

    private final EventCorrelationRecordInterceptor interceptor = new EventCorrelationRecordInterceptor();

    @AfterEach
    void noScopeLeaks() {
        assertThat(EventCorrelation.current()).as("a test left a correlation scope open").isEmpty();
    }

    @Test
    @DisplayName("scopes nest, and closing one restores the one before")
    void scopesNest() {
        try (EventCorrelation.Scope outer = EventCorrelation.begin("chain-1", null)) {
            try (EventCorrelation.Scope inner = EventCorrelation.begin("chain-2", "event-9")) {
                assertThat(EventCorrelation.current()).contains(new EventCorrelation.Context("chain-2", "event-9"));
            }
            assertThat(EventCorrelation.current()).contains(new EventCorrelation.Context("chain-1", null));
        }
    }

    @Test
    @DisplayName("blank ids are treated as absent, and closing twice is harmless")
    void blanksAreAbsent() {
        EventCorrelation.Scope scope = EventCorrelation.begin(" ", "");
        assertThat(EventCorrelation.current()).contains(new EventCorrelation.Context(null, null));
        scope.close();
        scope.close();
    }

    @Test
    @DisplayName("a consumed record's correlationId continues the chain, and its eventId becomes the causation")
    void interceptorContinuesTheChain() {
        ConsumerRecord<Object, Object> record = record("event-1", "chain-7");

        interceptor.intercept(record, null);
        assertThat(EventCorrelation.current()).contains(new EventCorrelation.Context("chain-7", "event-1"));

        interceptor.afterRecord(record, null);
        assertThat(EventCorrelation.current()).isEmpty();
    }

    @Test
    @DisplayName("a record sent before D4 has no correlationId: its eventId starts the chain")
    void recordWithoutCorrelationIsCorrelatedByItsEventId() {
        ConsumerRecord<Object, Object> record = record("event-2", null);

        interceptor.intercept(record, null);
        assertThat(EventCorrelation.current()).contains(new EventCorrelation.Context("event-2", "event-2"));
        interceptor.afterRecord(record, null);
    }

    @Test
    @DisplayName("a record with no envelope opens no scope")
    void recordWithoutEnvelopeOpensNoScope() {
        ConsumerRecord<Object, Object> record = record(null, null);

        assertThat(interceptor.intercept(record, null)).isSameAs(record);
        assertThat(EventCorrelation.current()).isEmpty();
        interceptor.afterRecord(record, null);
    }

    @Test
    @DisplayName("a scope that afterRecord did not close is replaced, not stacked, by the next record")
    void staleScopeIsReplaced() {
        interceptor.intercept(record("event-3", "chain-3"), null);
        ConsumerRecord<Object, Object> next = record("event-4", "chain-4");

        interceptor.intercept(next, null);
        assertThat(EventCorrelation.current()).contains(new EventCorrelation.Context("chain-4", "event-4"));

        interceptor.afterRecord(next, null);
        assertThat(EventCorrelation.current()).isEmpty();
    }

    private static ConsumerRecord<Object, Object> record(String eventId, String correlationId) {
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>("cpq.quote.status.accepted", 0, 0L, "key", "{}");
        if (eventId != null) {
            record.headers().add(EventHeaders.EVENT_ID, eventId.getBytes(StandardCharsets.UTF_8));
        }
        if (correlationId != null) {
            record.headers().add(EventHeaders.CORRELATION_ID, correlationId.getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }
}
