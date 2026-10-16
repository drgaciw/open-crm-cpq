package com.opencrm.quote.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.EventHeaders;
import com.opencrm.common.event.KafkaInbox;
import com.opencrm.common.event.KafkaTopics;
import com.opencrm.common.event.NonRetryableEventException;
import com.opencrm.common.event.dto.SubscriptionRenewalEligibleEvent;
import com.opencrm.quote.quote.QuoteContinuationService;
import com.opencrm.quote.quote.QuoteDto;
import com.opencrm.quote.quote.QuoteStatus;
import com.opencrm.quote.ramp.RampPricingProperties;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.annotation.KafkaListener;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
@DisplayName("Subscription renewal eligibility consumer")
class SubscriptionRenewalEligibleEventConsumerTest {

    private static final String PAYLOAD = """
            {"subscriptionId":"SUB-R3","renewalDate":"2029-12-01","currentPlan":"Growth","usageBand":"HIGH"}""";

    private QuoteContinuationService continuations;
    private KafkaInbox inbox;
    private RampPricingProperties ramp;
    private SubscriptionRenewalEligibleEventConsumer consumer;

    @BeforeEach
    void setUp() {
        continuations = mock(QuoteContinuationService.class);
        inbox = mock(KafkaInbox.class);
        ramp = new RampPricingProperties();
        ramp.setEnabled(true);
        consumer = new SubscriptionRenewalEligibleEventConsumer(continuations, inbox,
                new ObjectMapper().findAndRegisterModules(), ramp);
        when(inbox.claim(eq(SubscriptionRenewalEligibleEventConsumer.CONSUMER_GROUP), any())).thenReturn(true);
        when(inbox.claimKey(eq(SubscriptionRenewalEligibleEventConsumer.CONSUMER_GROUP), any())).thenReturn(true);
        when(continuations.draftRenewal(any(), any(), any(), any())).thenReturn(QuoteDto.builder()
                .id(1L).quoteNumber("Q-NEXT").status(QuoteStatus.DRAFT).build());
    }

    @Test
    @DisplayName("the listener is bound to the published registry contract")
    void listenerIsBoundToTheRegistryContract() throws Exception {
        Method method = SubscriptionRenewalEligibleEventConsumer.class.getMethod("onRenewalEligible", ConsumerRecord.class);
        KafkaListener listener = method.getAnnotation(KafkaListener.class);
        assertThat(listener.topics()).containsExactly(KafkaTopics.SUBSCRIPTION_RENEWAL_ELIGIBLE);
        assertThat(listener.groupId()).isEqualTo(SubscriptionRenewalEligibleEventConsumer.CONSUMER_GROUP);
        assertThat(KafkaTopics.SUBSCRIPTION_RENEWAL_ELIGIBLE)
                .isEqualTo(EventContractRegistry.SUBSCRIPTION_RENEWAL_ELIGIBLE_V1.topic());
        assertThat(EventContractRegistry.SUBSCRIPTION_RENEWAL_ELIGIBLE_V1.payloadClass())
                .isEqualTo(SubscriptionRenewalEligibleEvent.class);
        assertThat(EventContractRegistry.SUBSCRIPTION_RENEWAL_ELIGIBLE_V1.isPublished()).isTrue();
    }

    @Test
    @DisplayName("a first delivery opens one renewal draft")
    void createsOneDraftAndClaimsTheEvent() {
        consumer.onRenewalEligible(record("event-1", PAYLOAD));

        verify(inbox).claim(eq(SubscriptionRenewalEligibleEventConsumer.CONSUMER_GROUP), any());
        verify(continuations).draftRenewal(any(SubscriptionRenewalEligibleEvent.class), isNull(), eq("event-1"), isNull());
    }

    @Test
    @DisplayName("re-delivery of the same event id does not author again")
    void redeliveryDoesNotAuthorAgain() {
        when(inbox.claim(eq(SubscriptionRenewalEligibleEventConsumer.CONSUMER_GROUP), any())).thenReturn(false);

        consumer.onRenewalEligible(record("event-1", PAYLOAD));

        verifyNoInteractions(continuations);
    }

    @Test
    @DisplayName("a republish with a new event id and the same dedupe key does not author again")
    void stableDedupeKeySkipsARepublish() {
        String payload = PAYLOAD.replace("}", ",\"dedupeKey\":\"sub-r3:2029-12-01\"}");
        consumer.onRenewalEligible(record("event-1", payload));
        when(inbox.claimKey(eq(SubscriptionRenewalEligibleEventConsumer.CONSUMER_GROUP), eq("dedupe:sub-r3:2029-12-01")))
                .thenReturn(false);

        consumer.onRenewalEligible(record("event-2", payload));

        verify(continuations).draftRenewal(any(), isNull(), eq("event-1"), eq("sub-r3:2029-12-01"));
        verify(continuations, never()).draftRenewal(any(), any(), eq("event-2"), any());
    }

    @Test
    @DisplayName("while continuation authoring is off the event is not claimed and no draft is opened")
    void disabledContinuationGateAcksWithoutClaiming() {
        ramp.setEnabled(false);

        consumer.onRenewalEligible(record("event-1", PAYLOAD));

        verify(inbox, never()).claim(any(), any());
        verifyNoInteractions(continuations);
    }

    @Test
    @DisplayName("an unreadable payload is not retried and is not claimed")
    void unreadablePayloadIsNotRetryable() {
        assertThatThrownBy(() -> consumer.onRenewalEligible(record("event-1", "not-json")))
                .isInstanceOf(NonRetryableEventException.class);
        verify(inbox, never()).claim(any(), any());
        verifyNoInteractions(continuations);
    }

    @Test
    @DisplayName("a record for a different registry event type is not retried")
    void wrongEventTypeIsNotRetryable() {
        ConsumerRecord<String, String> record = record("event-1", PAYLOAD);
        record.headers().remove(EventHeaders.EVENT_TYPE);
        record.headers().add(EventHeaders.EVENT_TYPE, "QuoteAccepted".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> consumer.onRenewalEligible(record)).isInstanceOf(NonRetryableEventException.class);
        verify(inbox, never()).claim(any(), any());
    }

    private static ConsumerRecord<String, String> record(String eventId, String payload) {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(KafkaTopics.SUBSCRIPTION_RENEWAL_ELIGIBLE, 0, 3L,
                "SUB-R3", payload);
        record.headers().add(EventHeaders.EVENT_ID, eventId.getBytes(StandardCharsets.UTF_8));
        record.headers().add(EventHeaders.EVENT_TYPE,
                EventContractRegistry.SUBSCRIPTION_RENEWAL_ELIGIBLE_V1.eventType().getBytes(StandardCharsets.UTF_8));
        record.headers().add(EventHeaders.EVENT_VERSION, "1".getBytes(StandardCharsets.UTF_8));
        return record;
    }
}
