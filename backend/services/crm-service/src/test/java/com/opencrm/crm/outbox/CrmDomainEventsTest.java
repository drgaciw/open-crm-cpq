package com.opencrm.crm.outbox;

import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.DealClosedEvent;
import com.opencrm.crm.deal.Deal;
import com.opencrm.crm.deal.DealStage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CrmDomainEventsTest {
    @Test
    void closedEventUsesTheRecordedWonTime() {
        OutboxEventPublisher publisher = mock(OutboxEventPublisher.class);
        Instant acceptedAt = Instant.parse("2025-05-02T12:00:00Z");
        Deal deal = Deal.builder().uuid("deal-1").stage(DealStage.WON).wonAt(acceptedAt).build();

        new CrmDomainEvents(publisher).dealTransition(deal, DealStage.PROPOSAL);

        ArgumentCaptor<DealClosedEvent> payload = ArgumentCaptor.forClass(DealClosedEvent.class);
        verify(publisher).publish(eq("Deal"), eq("deal-1"), eq(EventContractRegistry.DEAL_CLOSED_V1), payload.capture());
        assertThat(payload.getValue().getClosedAt()).isEqualTo(acceptedAt);
    }

    @Test
    void closedEventUsesTheRecordedLostTime() {
        OutboxEventPublisher publisher = mock(OutboxEventPublisher.class);
        Instant lostAt = Instant.parse("2025-05-03T12:00:00Z");
        Deal deal = Deal.builder().uuid("deal-2").stage(DealStage.LOST).lostAt(lostAt).build();

        new CrmDomainEvents(publisher).dealTransition(deal, DealStage.PROPOSAL);

        ArgumentCaptor<DealClosedEvent> payload = ArgumentCaptor.forClass(DealClosedEvent.class);
        verify(publisher).publish(eq("Deal"), eq("deal-2"), eq(EventContractRegistry.DEAL_CLOSED_V1), payload.capture());
        assertThat(payload.getValue().getClosedAt()).isEqualTo(lostAt);
    }
}
