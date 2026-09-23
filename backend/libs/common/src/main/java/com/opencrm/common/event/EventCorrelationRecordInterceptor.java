package com.opencrm.common.event;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.RecordInterceptor;

/**
 * Opens an {@link EventCorrelation} scope around every Kafka listener call (ADR-018 D4). An event published while
 * handling the record continues its chain:
 * <ul>
 *   <li>correlation id: the record's {@code correlationId} header, or its {@code eventId} for a record sent before
 *       D4;</li>
 *   <li>causation id: the record's {@code eventId}.</li>
 * </ul>
 * A record with neither header opens no scope. The scope is closed after the record, whether the listener succeeded or
 * failed, so nothing leaks to the next record on the container thread.
 *
 * <p>Boot's listener container factory picks up a single {@link RecordInterceptor} bean; see
 * {@link EventCorrelationKafkaAutoConfiguration}.
 */
public class EventCorrelationRecordInterceptor implements RecordInterceptor<Object, Object> {

    private final ThreadLocal<EventCorrelation.Scope> scope = new ThreadLocal<>();

    @Override
    public ConsumerRecord<Object, Object> intercept(ConsumerRecord<Object, Object> record,
                                                    Consumer<Object, Object> consumer) {
        closeScope();
        String eventId = EventHeaders.value(record, EventHeaders.EVENT_ID);
        String correlationId = EventHeaders.value(record, EventHeaders.CORRELATION_ID);
        if (eventId != null || correlationId != null) {
            scope.set(EventCorrelation.begin(correlationId != null ? correlationId : eventId, eventId));
        }
        return record;
    }

    @Override
    public void afterRecord(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
        closeScope();
    }

    private void closeScope() {
        EventCorrelation.Scope open = scope.get();
        if (open != null) {
            scope.remove();
            open.close();
        }
    }
}
