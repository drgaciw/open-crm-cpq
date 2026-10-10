package com.opencrm.contract.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.KafkaInbox;
import com.opencrm.common.event.KafkaTopics;
import com.opencrm.common.event.NonRetryableEventException;
import com.opencrm.contract.domain.ErpSyncStatus;
import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.SubscriptionStatus;
import com.opencrm.contract.repository.SubscriptionRepository;
import com.opencrm.contract.repository.SubscriptionRatePeriodRepository;
import com.opencrm.contract.repository.AppliedQuoteAcceptanceRepository;
import com.opencrm.contract.service.AcceptedQuoteSubscriptionService;
import com.opencrm.contract.service.AcceptedRampValidator;
import org.springframework.jdbc.core.JdbcTemplate;
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

    @Mock private SubscriptionRatePeriodRepository rates;
    @Mock private AppliedQuoteAcceptanceRepository ledger;
    @Mock private JdbcTemplate jdbc;

    private QuoteAcceptedEventConsumer consumer;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        consumer = new QuoteAcceptedEventConsumer(mapper, inbox,
                new AcceptedQuoteSubscriptionService(repository, rates, ledger, new AcceptedRampValidator(), mapper, jdbc));
        when(repository.save(any())).thenAnswer(call -> {
            Subscription subscription = call.getArgument(0);
            subscription.setId(5L);
            return subscription;
        });
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
    @DisplayName("uses the accepted ramp's actual term, pricing ARR and sold product names")
    void createsExactRampCommercialFacts() {
        String payload = """
                {"eventType":"QuoteAccepted","eventVersion":1,"quoteId":"Q-RAMP","customerId":"C-1",
                 "totalAmount":2000,"currencyCode":"USD",
                 "pricing":{"totalAmount":2000,"currencyCode":"USD","arr":24000,"tcv":81000,"acv":27000,"exitArr":30000},
                 "terms":{"termMonths":36,"startDate":"2027-01-01","endDate":"2029-12-31"},
                 "deal":{"dealType":"NEW"},
                 "lineItems":[{"lineUuid":"11111111-1111-4111-8111-000000000201","productExternalId":"SEAT",
                   "productName":"Named seat","productType":"SAAS_SEAT_NAMED","lineItemType":"SUBSCRIPTION",
                   "quantity":50,"billingFrequency":"MONTHLY","extendedAmount":2000,
                   "ramp":[{"sequence":1,"startMonth":1,"months":12,"quantity":50,"netUnitPrice":40,"netMonthly":2000,"periodAmount":24000},
                           {"sequence":2,"startMonth":13,"months":12,"quantity":50,"netUnitPrice":45,"netMonthly":2250,"periodAmount":27000},
                           {"sequence":3,"startMonth":25,"months":12,"quantity":50,"netUnitPrice":50,"netMonthly":2500,"periodAmount":30000}]}]}
                """;

        consumer.handleQuoteAccepted(record(payload));

        ArgumentCaptor<Subscription> saved = ArgumentCaptor.forClass(Subscription.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getStartDate()).isEqualTo(java.time.LocalDate.of(2027, 1, 1));
        assertThat(saved.getValue().getEndDate()).isEqualTo(java.time.LocalDate.of(2029, 12, 31));
        assertThat(saved.getValue().getArr()).isEqualByComparingTo("24000");
        assertThat(saved.getValue().getMrr()).isEqualByComparingTo("2000");
        assertThat(saved.getValue().getPlanName()).isEqualTo("Named seat");
    }

    @Test
    @DisplayName("a typed amendment without an explicit existing target never creates a subscription")
    void amendmentWithoutTargetIsRejected() {
        String payload = """
                {"quoteId":"Q-AMEND","customerId":"C-1","totalAmount":1200,"currencyCode":"USD",
                 "deal":{"dealType":"AMENDMENT"}}
                """;

        assertThatThrownBy(() -> consumer.handleQuoteAccepted(record(payload)))
                .isInstanceOf(NonRetryableEventException.class);
        verify(repository, never()).save(any());
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
