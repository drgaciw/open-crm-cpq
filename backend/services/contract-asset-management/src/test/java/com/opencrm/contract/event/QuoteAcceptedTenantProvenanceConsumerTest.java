package com.opencrm.contract.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.KafkaInbox;
import com.opencrm.common.event.KafkaTopics;
import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.repository.AppliedQuoteAcceptanceRepository;
import com.opencrm.contract.repository.SubscriptionRatePeriodRepository;
import com.opencrm.contract.repository.SubscriptionRepository;
import com.opencrm.contract.service.AcceptedQuoteSubscriptionService;
import com.opencrm.contract.service.AcceptedRampValidator;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DRG-662 AC8: only the DRG-631 top-level {@code tenantId} provenance field is read.
 * Nested spoof fields are ignored. libs/common is out of allowlist — consumer parses raw JSON.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DRG-662 QuoteAccepted tenant provenance via consumer")
class QuoteAcceptedTenantProvenanceConsumerTest {

    static final UUID TENANT = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Mock SubscriptionRepository repository;
    @Mock KafkaInbox inbox;
    @Mock SubscriptionRatePeriodRepository rates;
    @Mock AppliedQuoteAcceptanceRepository ledger;
    @Mock JdbcTemplate jdbc;

    SimpleMeterRegistry meters = new SimpleMeterRegistry();
    QuoteAcceptedEventConsumer consumer;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        AcceptedQuoteSubscriptionService accepted = new AcceptedQuoteSubscriptionService(
                repository, rates, ledger, new AcceptedRampValidator(), mapper, jdbc);
        accepted.bindTenantMeters(meters);
        consumer = new QuoteAcceptedEventConsumer(mapper, inbox, accepted);
        when(repository.save(any())).thenAnswer(call -> {
            Subscription subscription = call.getArgument(0);
            subscription.setId(5L);
            return subscription;
        });
        when(inbox.claim(eq(QuoteAcceptedEventConsumer.CONSUMER_GROUP), any())).thenReturn(true);
        when(repository.existsBySubscriptionNumber(any())).thenReturn(false);
    }

    @Test
    @DisplayName("AC8: top-level tenantId is stored; nested spoof tenantId is ignored")
    void readsOnlyTopLevelProvenanceField() {
        String payload = """
                {"quoteId":"Q-TENANT","customerId":"C-1","totalAmount":100,"currencyCode":"USD",
                 "tenantId":"%s",
                 "customer":{"customerId":"C-1","tenantId":"cccccccc-cccc-cccc-cccc-cccccccccccc"},
                 "quote":{"quoteId":"Q-TENANT","tenantId":"dddddddd-dddd-dddd-dddd-dddddddddddd"},
                 "deal":{"dealType":"NEW","tenantId":"eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"}}
                """.formatted(TENANT);

        consumer.handleQuoteAccepted(record(payload));

        ArgumentCaptor<Subscription> saved = ArgumentCaptor.forClass(Subscription.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getTenantId()).isEqualTo(TENANT);
        assertThat(saved.getValue().getSubscriptionNumber()).isEqualTo("SUB-Q-TENANT");
    }

    @Test
    @DisplayName("AC8: nested-only tenantId does not populate the subscription")
    void ignoresNestedTenantWhenTopLevelAbsent() {
        String payload = """
                {"quoteId":"Q-NEST","customerId":"C-1","totalAmount":100,"currencyCode":"USD",
                 "customer":{"customerId":"C-1","tenantId":"%s"},
                 "deal":{"dealType":"NEW"}}
                """.formatted(TENANT);

        consumer.handleQuoteAccepted(record(payload));

        ArgumentCaptor<Subscription> saved = ArgumentCaptor.forClass(Subscription.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getTenantId()).isNull();
    }

    private static ConsumerRecord<String, String> record(String payload) {
        return new ConsumerRecord<>(KafkaTopics.QUOTE_STATUS_ACCEPTED, 0, 3L, "Q", payload);
    }
}
