package com.opencrm.common.event;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Kafka consumer support: inbox (ADR-018 D6) and dead-letter error handling (D7)")
class KafkaConsumerSupportTest {

    private final ApplicationContextRunner kafkaContext = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    KafkaAutoConfiguration.class, KafkaConsumerErrorHandlingAutoConfiguration.class,
                    KafkaInboxAutoConfiguration.class, DeadLetterMetricsAutoConfiguration.class))
            .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
            .withPropertyValues("spring.kafka.bootstrap-servers=localhost:9092");

    @AfterEach
    void resetTransactionState() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    private static ConsumerRecord<String, String> record(String eventId) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("cpq.quote.status.accepted", 2, 41L, "Q-1", "{}");
        if (eventId != null) {
            record.headers().add(EventHeaders.EVENT_ID, eventId.getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }

    @Test
    @DisplayName("D7: the listener container factory uses the dead-letter error handler")
    void listenerContainersUseDeadLetterErrorHandler() {
        kafkaContext.run(context -> {
            assertThat(context).hasSingleBean(CommonErrorHandler.class);
            assertThat(context.getBean(CommonErrorHandler.class)).isInstanceOf(DefaultErrorHandler.class);
            ConcurrentKafkaListenerContainerFactory<?, ?> factory =
                    context.getBean(ConcurrentKafkaListenerContainerFactory.class);
            assertThat(factory.createContainer("cpq.quote.status.accepted").getCommonErrorHandler())
                    .isSameAs(context.getBean(CommonErrorHandler.class));
        });
    }

    @Test
    @DisplayName("D7: the first poison delivery is published to the DLT with its failure reason")
    void poisonDeliveryPublishesFailureReason() {
        KafkaOperations<String, String> kafka = mock();
        when(kafka.send(ArgumentMatchers.<ProducerRecord<String, String>>any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        ObjectProvider<MeterRegistry> meters = mock();
        DefaultErrorHandler handler = new KafkaConsumerErrorHandlingAutoConfiguration()
                .kafkaDeadLetterErrorHandler(kafka, new KafkaConsumerErrorHandlingAutoConfiguration.RetryProperties(),
                        meters);
        ConsumerRecord<String, String> poison = record("evt-1");
        var failure = new ListenerExecutionFailedException("Listener failed", "document-generation",
                new NonRetryableEventException("QuoteAccepted event is missing quoteId"));

        assertThat(handler.handleOne(failure, poison, null, mock(MessageListenerContainer.class))).isTrue();

        ArgumentCaptor<ProducerRecord<String, String>> publishedRecord = ArgumentCaptor.captor();
        verify(kafka).send(publishedRecord.capture());
        ProducerRecord<String, String> dead = publishedRecord.getValue();
        assertThat(dead.topic()).isEqualTo(poison.topic() + ".DLT");
        assertThat(dead.value()).isEqualTo(poison.value());
        assertThat(dead.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_MESSAGE))
                .isNotNull()
                .extracting(header -> new String(header.value(), StandardCharsets.UTF_8))
                .asString().contains("QuoteAccepted event is missing quoteId");
    }

    @Test
    @DisplayName("D10: dead-letter metrics are registered with the error handler, and switched off with it")
    void deadLetterMetricsFollowTheErrorHandler() {
        kafkaContext.run(context -> assertThat(context).hasSingleBean(DeadLetterMetrics.class));
        kafkaContext.withPropertyValues("opencrm.kafka.consumer.dead-letter.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(DeadLetterMetrics.class));
    }
    @Test
    @DisplayName("D7: opencrm.kafka.consumer.dead-letter.enabled=false leaves Spring Kafka's default handling")
    void deadLetterHandlingCanBeSwitchedOff() {
        kafkaContext.withPropertyValues("opencrm.kafka.consumer.dead-letter.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(CommonErrorHandler.class));
    }

    @Test
    @DisplayName("D6: the inbox is registered only when opencrm.kafka.inbox.enabled=true")
    void inboxIsOptIn() {
        kafkaContext.run(context -> assertThat(context).doesNotHaveBean(KafkaInbox.class));
        kafkaContext.withPropertyValues("opencrm.kafka.inbox.enabled=true").run(context -> {
            assertThat(context).hasSingleBean(KafkaInbox.class);
            assertThat(context).hasSingleBean(KafkaInboxAutoConfiguration.KafkaInboxPurge.class);
        });
    }

    @Test
    @DisplayName("D6: the dedupe key is the eventId header, else topic-partition@offset")
    void dedupeKey() {
        assertThat(KafkaInbox.eventIdOf(record("5f0c9d7e-uuid"))).isEqualTo("5f0c9d7e-uuid");
        assertThat(KafkaInbox.eventIdOf(record(null))).isEqualTo("cpq.quote.status.accepted-2@41");
    }

    @Test
    @DisplayName("D6: claim is true for the first delivery and false for a repeat")
    void claimReportsFirstDeliveryOnly() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), eq("grp"), eq("evt-1"))).thenReturn(1).thenReturn(0);
        KafkaInbox inbox = new KafkaInbox(jdbc);
        TransactionSynchronizationManager.setActualTransactionActive(true);

        assertThat(inbox.claim("grp", record("evt-1"))).isTrue();
        assertThat(inbox.claim("grp", record("evt-1"))).isFalse();
    }

    @Test
    @DisplayName("D6: claim outside a transaction is refused, because it would not be atomic with the side effect")
    void claimRequiresTransaction() {
        KafkaInbox inbox = new KafkaInbox(mock(JdbcTemplate.class));

        assertThatThrownBy(() -> inbox.claim("grp", record("evt-1"))).isInstanceOf(IllegalStateException.class);
    }
}
