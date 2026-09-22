package com.opencrm.common.event;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Kafka consumer support: inbox (ADR-018 D6) and dead-letter error handling (D7)")
class KafkaConsumerSupportTest {

    private final ApplicationContextRunner kafkaContext = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    KafkaAutoConfiguration.class, KafkaConsumerErrorHandlingAutoConfiguration.class,
                    KafkaInboxAutoConfiguration.class))
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
