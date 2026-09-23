package com.opencrm.common.event;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.listener.ConsumerAwareRecordRecoverer;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.support.KafkaHeaders;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ADR-018 D7/D10: dead-letter counting, triage helpers and the DeadLetterTool command line")
class DeadLetterSupportTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @SuppressWarnings("unchecked")
    private ObjectProvider<MeterRegistry> provider() {
        ObjectProvider<MeterRegistry> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(registry);
        return provider;
    }

    @SuppressWarnings("unchecked")
    private static Consumer<Object, Object> consumerOf(String group) {
        Consumer<Object, Object> consumer = mock(Consumer.class);
        when(consumer.groupMetadata()).thenReturn(new ConsumerGroupMetadata(group));
        return consumer;
    }

    @Test
    @DisplayName("a dead-lettered record is counted by topic, group and the listener's own exception")
    void countsDeadLetters() {
        ConsumerAwareRecordRecoverer delegate = mock(ConsumerAwareRecordRecoverer.class);
        DeadLetterCountingRecoverer recoverer = new DeadLetterCountingRecoverer(delegate, provider());
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>("cpq.quote.status.accepted", 0, 7L, "k", "{");
        Exception failure = new ListenerExecutionFailedException("listener failed",
                new NonRetryableEventException("unreadable"));

        recoverer.accept(record, consumerOf("document-generation"), failure);

        verify(delegate).accept(eq(record), any(), eq(failure));
        Counter counter = registry.find(DeadLetterCountingRecoverer.METER)
                .tags("topic", "cpq.quote.status.accepted", "group", "document-generation",
                        "exception", "NonRetryableEventException")
                .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("a failed DLT publish is not counted, and the failure reaches the error handler")
    void failedPublishIsNotCounted() {
        ConsumerAwareRecordRecoverer delegate = mock(ConsumerAwareRecordRecoverer.class);
        doThrow(new IllegalStateException("broker down")).when(delegate).accept(any(), any(), any());
        DeadLetterCountingRecoverer recoverer = new DeadLetterCountingRecoverer(delegate, provider());
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>("cpq.quote.status.accepted", 0, 7L, "k", "{");

        assertThatThrownBy(() -> recoverer.accept(record, consumerOf("g"), new RuntimeException("x")))
                .hasMessage("broker down");
        assertThat(registry.find(DeadLetterCountingRecoverer.METER).counter()).isNull();
    }

    @Test
    @DisplayName("the DLT header names match Spring Kafka's")
    void headerNamesMatchSpringKafka() {
        assertThat(DeadLetterTriage.ORIGINAL_TOPIC).isEqualTo(KafkaHeaders.DLT_ORIGINAL_TOPIC);
        assertThat(DeadLetterTriage.ORIGINAL_GROUP).isEqualTo(KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP);
        assertThat(DeadLetterTriage.EXCEPTION_CAUSE_FQCN).isEqualTo(KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN);
        assertThat(DeadLetterTriage.EXCEPTION_FQCN).isEqualTo(KafkaHeaders.DLT_EXCEPTION_FQCN);
        assertThat(DeadLetterTriage.EXCEPTION_MESSAGE).isEqualTo(KafkaHeaders.DLT_EXCEPTION_MESSAGE);
        assertThat(KafkaHeaders.DLT_ORIGINAL_TOPIC).startsWith(DeadLetterTriage.DLT_HEADER_PREFIX);
        assertThat(DeadLetterTriage.DLT_SUFFIX).isEqualTo(KafkaConsumerErrorHandlingAutoConfiguration.DLT_SUFFIX);
    }

    @Test
    @DisplayName("replay keeps the envelope headers and drops Spring Kafka's kafka_dlt-* headers")
    void replayHeadersKeepTheEnvelope() {
        ConsumerRecord<byte[], byte[]> record = dead("cpq.quote.status.accepted.DLT", Map.of(
                EventHeaders.EVENT_ID, "e-1",
                EventHeaders.CORRELATION_ID, "c-1",
                KafkaHeaders.DLT_ORIGINAL_TOPIC, "cpq.quote.status.accepted",
                KafkaHeaders.DLT_EXCEPTION_MESSAGE, "boom"));

        assertThat(StreamSupport.stream(DeadLetterTriage.replayHeaders(record).spliterator(), false)
                .map(Header::key))
                .containsExactlyInAnyOrder(EventHeaders.EVENT_ID, EventHeaders.CORRELATION_ID);
    }

    @Test
    @DisplayName("a record without an origin header is sent back to the topic its DLT is named after")
    void originFallsBackToTheTopicName() {
        DeadLetterTriage.DeadLetter letter = DeadLetterTriage.describe(dead("cpq.product.updated.DLT", Map.of()));

        assertThat(letter.originalTopic()).isEqualTo("cpq.product.updated");
        assertThat(DeadLetterTriage.deadLetterTopic("cpq.product.updated")).isEqualTo("cpq.product.updated.DLT");
        assertThat(DeadLetterTriage.deadLetterTopic("cpq.product.updated.DLT")).isEqualTo("cpq.product.updated.DLT");
    }

    @Test
    @DisplayName("the command line: topic to DLT, broker from the environment, options and usage errors")
    void commandLine() {
        DeadLetterTool.Command command = DeadLetterTool.parse(
                new String[] {"replay", "cpq.quote.status.accepted", "--max", "5", "--dry-run"},
                Map.of("KAFKA_BOOTSTRAP_SERVERS", "kafka:29092"));
        assertThat(command).isEqualTo(new DeadLetterTool.Command(
                "replay", "cpq.quote.status.accepted.DLT", 5, true, "kafka:29092"));

        assertThat(DeadLetterTool.parse(new String[] {"depth", "t.DLT", "--bootstrap-servers", "h:1"}, Map.of())
                .bootstrapServers()).isEqualTo("h:1");
        assertThat(DeadLetterTool.parse(new String[] {"peek", "t"}, Map.of()).bootstrapServers())
                .isEqualTo("localhost:9092");

        for (String[] bad : new String[][] {
                {}, {"purge", "t"}, {"peek", "t", "--max", "0"}, {"skip", "t", "--dry-run"}, {"peek", "t", "--max"}}) {
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            int exit = DeadLetterTool.run(bad, Map.of(), new PrintStream(new ByteArrayOutputStream()),
                    new PrintStream(err, true, StandardCharsets.UTF_8));
            assertThat(exit).as(String.join(" ", bad)).isEqualTo(2);
            assertThat(err.toString(StandardCharsets.UTF_8)).contains("usage: DeadLetterTool");
        }
    }

    private static ConsumerRecord<byte[], byte[]> dead(String topic, Map<String, String> headers) {
        ConsumerRecord<byte[], byte[]> record = new ConsumerRecord<>(topic, 0, 3L,
                "k".getBytes(StandardCharsets.UTF_8), "{}".getBytes(StandardCharsets.UTF_8));
        headers.forEach((name, value) -> record.headers().add(name, value.getBytes(StandardCharsets.UTF_8)));
        return record;
    }
}
