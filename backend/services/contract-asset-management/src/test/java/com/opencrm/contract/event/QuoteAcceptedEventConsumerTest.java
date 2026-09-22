package com.opencrm.contract.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.KafkaInbox;
import com.opencrm.common.event.KafkaTopics;
import com.opencrm.common.event.NonRetryableEventException;
import com.opencrm.contract.domain.ErpSyncStatus;
import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.SubscriptionStatus;
import com.opencrm.contract.repository.SubscriptionRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** ADR-018 D6: the quote-accepted consumer is idempotent and makes no ERP call. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("contract QuoteAcceptedEventConsumer (ADR-018 D6)")
class QuoteAcceptedEventConsumerTest {

    private static final String EVENT = """
            {"quoteId":"Q-9","customerId":"C-1","totalAmount":1200.00,"currencyCode":"EUR"}""";

    @Mock
    private SubscriptionRepository repository;

    @Mock
    private KafkaInbox inbox;

    private QuoteAcceptedEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new QuoteAcceptedEventConsumer(repository, new ObjectMapper(), inbox);
        when(inbox.claim(eq(QuoteAcceptedEventConsumer.CONSUMER_GROUP), any())).thenReturn(true);
        when(repository.existsBySubscriptionNumber("SUB-Q-9")).thenReturn(false);
    }

    private static ConsumerRecord<String, String> record(String payload) {
        return new ConsumerRecord<>(KafkaTopics.QUOTE_STATUS_ACCEPTED, 0, 3L, "Q-9", payload);
    }

    @Test
    @DisplayName("creates a PENDING subscription with the ERP sync queued, not performed")
    void createsSubscriptionWithErpSyncQueued() {
        consumer.handleQuoteAccepted(record(EVENT));

        ArgumentCaptor<Subscription> saved = ArgumentCaptor.forClass(Subscription.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getSubscriptionNumber()).isEqualTo("SUB-Q-9");
        assertThat(saved.getValue().getStatus()).isEqualTo(SubscriptionStatus.PENDING);
        assertThat(saved.getValue().getErpSyncStatus()).isEqualTo(ErpSyncStatus.QUEUED);
        assertThat(saved.getValue().getArr()).isEqualByComparingTo("1200.00");
        assertThat(saved.getValue().getCurrencyCode()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("an event the inbox has already seen creates nothing")
    void alreadyProcessedEventIsSkipped() {
        when(inbox.claim(eq(QuoteAcceptedEventConsumer.CONSUMER_GROUP), any())).thenReturn(false);

        consumer.handleQuoteAccepted(record(EVENT));

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("an existing SUB-<quoteId> makes the event a duplicate instead of a UNIQUE violation")
    void existingSubscriptionIsADuplicate() {
        when(repository.existsBySubscriptionNumber("SUB-Q-9")).thenReturn(true);

        consumer.handleQuoteAccepted(record(EVENT));

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("D7: malformed JSON or a missing quoteId is not retryable")
    void unreadableEventIsNotRetryable() {
        assertThatThrownBy(() -> consumer.handleQuoteAccepted(record("{not json")))
                .isInstanceOf(NonRetryableEventException.class);
        assertThatThrownBy(() -> consumer.handleQuoteAccepted(record("{\"customerId\":\"C-1\"}")))
                .isInstanceOf(NonRetryableEventException.class);

        verify(repository, never()).save(any());
    }
}
