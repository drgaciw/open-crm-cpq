package com.opencrm.contract.scheduler;

import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.KafkaTopics;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.SubscriptionRenewalEligibleEvent;
import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.SubscriptionStatus;
import com.opencrm.contract.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RenewalSchedulerTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    @InjectMocks
    private RenewalScheduler renewalScheduler;

    @Test
    void publishesSubscriptionRenewalEligibleV1ForActiveSubscriptions() {
        Subscription subscription = Subscription.builder()
                .id(7L)
                .subscriptionNumber("SUB-100")
                .renewalDate(LocalDate.parse("2026-05-20"))
                .planName("Enterprise")
                .usageBand("high")
                .status(SubscriptionStatus.ACTIVE)
                .build();

        when(subscriptionRepository.findRenewalEligible(LocalDate.now())).thenReturn(List.of(subscription));

        renewalScheduler.checkRenewalEligibility();

        ArgumentCaptor<SubscriptionRenewalEligibleEvent> captor =
                ArgumentCaptor.forClass(SubscriptionRenewalEligibleEvent.class);
        verify(outboxEventPublisher).publish(
                eq("Subscription"),
                eq("7"),
                eq(KafkaTopics.SUBSCRIPTION_RENEWAL_ELIGIBLE),
                captor.capture()
        );

        SubscriptionRenewalEligibleEvent event = captor.getValue();
        assertThat(event.getEventType()).isEqualTo(EventContractRegistry.SUBSCRIPTION_RENEWAL_ELIGIBLE_V1.eventType());
        assertThat(event.getEventVersion()).isEqualTo(1);
        assertThat(event.getSubscriptionId()).isEqualTo("7");
        assertThat(event.getCurrentPlan()).isEqualTo("Enterprise");
        assertThat(event.toPublishedLanguageV1().getSchemaVersion()).isEqualTo(1);
    }
}
