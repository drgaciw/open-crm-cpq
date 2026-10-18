package com.opencrm.contract.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.opencrm.common.event.KafkaInbox;
import com.opencrm.common.event.KafkaTopics;
import com.opencrm.common.event.dto.QuoteAcceptedEvent;
import com.opencrm.contract.service.AcceptedQuoteSubscriptionService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Built routing cells only. R4, R5, R6 and R8 are not built: there is no event tenant and no document path.
 * Every built cell calls legacy apply exactly once.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("quote accepted routing (AC-S6-2 built cells, AC-S6-3)")
class QuoteAcceptedEventRoutingTest {

    private static final String LEGACY = """
            {"eventType":"QuoteAccepted","eventVersion":1,"quoteId":"Q-LEGACY","customerId":"C-1","currencyCode":"USD"}""";

    @Mock
    private KafkaInbox inbox;

    @Mock
    private AcceptedQuoteSubscriptionService acceptedQuotes;

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private QuoteAcceptedEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new QuoteAcceptedEventConsumer(mapper, inbox, acceptedQuotes);
        when(inbox.claim(eq(QuoteAcceptedEventConsumer.CONSUMER_GROUP), any())).thenReturn(true);
    }

    @Test
    @DisplayName("R1: absent document id, flag off, legacy apply exactly once")
    void r1AbsentDocumentIdFlagOffAppliesLegacyOnce() {
        bind(AmendmentRoutingFlag.off());

        consumer.handleQuoteAccepted(record("Q-R1", LEGACY));

        QuoteAcceptedEvent applied = captureSingleApply();
        assertThat(applied.getAmendmentDocumentId()).isNull();
        assertLegacyMetrics();
    }

    @Test
    @DisplayName("R2: absent document id, flag set, no event tenant, legacy apply exactly once")
    void r2AbsentDocumentIdFlagOnWithoutEventTenantAppliesLegacyOnce() {
        AmendmentRoutingFlag flag = AmendmentRoutingFlag.forTenants("tenant-on");
        assertThat(flag.isDocumentPathEnabledFor("tenant-on")).isTrue();
        assertThat(flag.isDocumentPathEnabledFor(null)).isFalse();
        assertThat(flag.isDocumentPathEnabledFor("")).isFalse();
        bind(flag);

        consumer.handleQuoteAccepted(record("Q-R2", LEGACY));

        QuoteAcceptedEvent applied = captureSingleApply();
        assertThat(applied.getAmendmentDocumentId()).isNull();
        assertThat(flag.decide(applied)).isEqualTo(AmendmentRoutingDecision.LEGACY);
        assertLegacyMetrics();
    }

    @Test
    @DisplayName("R3: document id present, flag off, legacy apply exactly once and the id is ignored")
    void r3PresentDocumentIdFlagOffAppliesLegacyOnce() throws Exception {
        bind(AmendmentRoutingFlag.off());
        String payload = fixture("r3-present-flag-off.json");

        consumer.handleQuoteAccepted(record("Q-R3", payload));

        QuoteAcceptedEvent applied = captureSingleApply();
        assertThat(applied.getAmendmentDocumentId()).isEqualTo("11111111-1111-4111-8111-0000000000a1");
        assertThat(applied.getLineItems().getFirst().getApplicationUnitNo()).isEqualTo(2);
        assertThat(AmendmentRoutingFlag.off().decide(applied)).isEqualTo(AmendmentRoutingDecision.LEGACY);
        assertLegacyMetrics();
    }

    @Test
    @DisplayName("R7: document id present, no event tenant, treated as flag off, legacy apply exactly once")
    void r7PresentDocumentIdWithoutEventTenantAppliesLegacyOnce() throws Exception {
        AmendmentRoutingFlag flag = AmendmentRoutingFlag.forTenants("tenant-on");
        bind(flag);
        String payload = fixture("r7-present-no-tenant.json");

        consumer.handleQuoteAccepted(record("Q-R7", payload));

        QuoteAcceptedEvent applied = captureSingleApply();
        assertThat(applied.getAmendmentDocumentId()).isEqualTo("11111111-1111-4111-8111-0000000000a7");
        assertThat(applied.getLineItems().getFirst().getApplicationUnitNo()).isEqualTo(8);
        assertThat(flag.decide(applied)).isEqualTo(AmendmentRoutingDecision.LEGACY);
        assertThat(AmendmentRoutingDecision.values()).containsExactly(AmendmentRoutingDecision.LEGACY);
        assertLegacyMetrics();
    }

    @Test
    @DisplayName("AC-S6-3: a legacy event takes the legacy path and the inbox dedupes the second delivery")
    void legacyEventWithoutDocumentIdTakesLegacyAndInboxDedupes() {
        bind(AmendmentRoutingFlag.off());
        Set<String> claimed = new HashSet<>();
        when(inbox.claim(eq(QuoteAcceptedEventConsumer.CONSUMER_GROUP), any())).thenAnswer(invocation -> {
            ConsumerRecord<?, ?> delivered = invocation.getArgument(1);
            return claimed.add(KafkaInbox.eventIdOf(delivered));
        });
        ConsumerRecord<String, String> delivery = record("Q-LEGACY", LEGACY);

        consumer.handleQuoteAccepted(delivery);
        consumer.handleQuoteAccepted(delivery);

        verify(acceptedQuotes, times(1)).apply(any());
        assertThat(meters.counter(AmendmentRoutingFlag.ROUTED_LEGACY).count()).isEqualTo(1.0);
        assertThat(meters.counter(AmendmentRoutingFlag.DOCUMENT_PATH_SELECTED).count()).isZero();
    }

    @Test
    @DisplayName("the same event delivered concurrently applies once")
    void concurrentDuplicateDeliveriesApplyOnce() throws Exception {
        bind(AmendmentRoutingFlag.off());
        AtomicBoolean claimed = new AtomicBoolean(false);
        when(inbox.claim(eq(QuoteAcceptedEventConsumer.CONSUMER_GROUP), any()))
                .thenAnswer(invocation -> claimed.compareAndSet(false, true));
        ConsumerRecord<String, String> delivery = record("Q-LEGACY", LEGACY);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> first = pool.submit(() -> {
                start.await();
                consumer.handleQuoteAccepted(delivery);
                return null;
            });
            Future<?> second = pool.submit(() -> {
                start.await();
                consumer.handleQuoteAccepted(delivery);
                return null;
            });
            start.countDown();
            first.get();
            second.get();
        } finally {
            pool.shutdownNow();
        }

        verify(acceptedQuotes, times(1)).apply(any());
        assertThat(meters.counter(AmendmentRoutingFlag.DOCUMENT_PATH_SELECTED).count()).isZero();
    }

    @Test
    @DisplayName("AC-S6-1b: each existing accepted-v1 fixture takes the legacy path")
    void acceptedV1FixturesTakeLegacyPath() throws Exception {
        bind(AmendmentRoutingFlag.off());
        for (String name : List.of("hybrid-credit-new.json", "r4-accepted-event.json", "ramp-new.json")) {
            String payload = new String(getClass().getResourceAsStream("/accepted-v1/" + name).readAllBytes(),
                    StandardCharsets.UTF_8);
            consumer.handleQuoteAccepted(record(name, payload));
        }

        ArgumentCaptor<QuoteAcceptedEvent> applied = ArgumentCaptor.forClass(QuoteAcceptedEvent.class);
        verify(acceptedQuotes, times(3)).apply(applied.capture());
        assertThat(applied.getAllValues()).allSatisfy(event -> {
            assertThat(event.getAmendmentDocumentId()).isNull();
            assertThat(event.getQuoteId()).isNotBlank();
        });
        assertThat(meters.counter(AmendmentRoutingFlag.ROUTED_LEGACY).count()).isEqualTo(3.0);
        assertThat(meters.counter(AmendmentRoutingFlag.DOCUMENT_PATH_SELECTED).count()).isZero();
    }

    private void bind(AmendmentRoutingFlag flag) {
        consumer.bindRouting(flag, meters);
    }

    private QuoteAcceptedEvent captureSingleApply() {
        ArgumentCaptor<QuoteAcceptedEvent> applied = ArgumentCaptor.forClass(QuoteAcceptedEvent.class);
        verify(acceptedQuotes, times(1)).apply(applied.capture());
        return applied.getValue();
    }

    private void assertLegacyMetrics() {
        MeterRegistry registry = meters;
        assertThat(registry.counter(AmendmentRoutingFlag.ROUTED_LEGACY).count()).isEqualTo(1.0);
        assertThat(registry.counter(AmendmentRoutingFlag.DOCUMENT_PATH_SELECTED).count()).isZero();
    }

    private static ConsumerRecord<String, String> record(String key, String payload) {
        return new ConsumerRecord<>(KafkaTopics.QUOTE_STATUS_ACCEPTED, 0, 7L, key, payload);
    }

    private String fixture(String name) throws Exception {
        return new String(getClass().getResourceAsStream("/accepted-v1-amendment/" + name).readAllBytes(),
                StandardCharsets.UTF_8);
    }
}
