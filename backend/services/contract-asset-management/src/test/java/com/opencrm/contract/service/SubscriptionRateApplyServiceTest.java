package com.opencrm.contract.service;

import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.RampAppliedEvent;
import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.SubscriptionRatePeriod;
import com.opencrm.contract.domain.SubscriptionStatus;
import com.opencrm.contract.repository.SubscriptionRepository;
import com.opencrm.contract.repository.SubscriptionRatePeriodRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubscriptionRateApplyServiceTest {
    @Mock SubscriptionRepository subscriptions;
    @Mock SubscriptionRatePeriodRepository rates;
    @Mock OutboxEventPublisher outbox;
    SubscriptionRateApplyService service;
    Subscription subscription;
    SubscriptionRatePeriod due;

    @BeforeEach void setUp() {
        service = new SubscriptionRateApplyService(subscriptions, rates, outbox,
                Clock.fixed(Instant.parse("2028-01-05T12:00:00Z"), ZoneOffset.UTC));
        subscription = Subscription.builder().id(42L).subscriptionNumber("SUB-RAMP").status(SubscriptionStatus.ACTIVE)
                .mrr(new BigDecimal("2000")).arr(new BigDecimal("24000")).currencyCode("USD").build();
        due = SubscriptionRatePeriod.builder().subscriptionId(42L).acceptedLineUuid("line-1").sequence(2)
                .startDate(LocalDate.of(2028,1,1)).endDate(LocalDate.of(2028,12,31)).effectiveEndDate(LocalDate.of(2028,12,31))
                .quantity(new BigDecimal("50")).netUnitPrice(new BigDecimal("45")).netMonthly(new BigDecimal("2250"))
                .annualRunRate(new BigDecimal("27000")).currencyCode("USD").build();
        when(subscriptions.findByIdForUpdate(42L)).thenReturn(Optional.of(subscription));
        when(rates.findBySubscriptionIdAndDeletedFalseOrderByStartDateAscAcceptedLineUuidAscSequenceAsc(42L)).thenReturn(List.of(due));
    }

    @Test void downtimeCatchupPublishesBoundaryOnceAndQueuesChangedFinancialRate() {
        assertThat(service.applyDue(42L)).isEqualTo(1);
        assertThat(service.applyDue(42L)).isZero();
        assertThat(due.isApplied()).isTrue();
        assertThat(subscription.getMrr()).isEqualByComparingTo("2250");
        assertThat(subscription.getArr()).isEqualByComparingTo("27000");
        assertThat(subscription.getErpSyncStatus()).isEqualTo("QUEUED");
        ArgumentCaptor<RampAppliedEvent> event = ArgumentCaptor.forClass(RampAppliedEvent.class);
        verify(outbox).publish(eq("Subscription"), eq("SUB-RAMP"), eq(EventContractRegistry.RAMP_APPLIED_V1), event.capture());
        assertThat(event.getValue().getEffectiveDate()).isEqualTo(LocalDate.of(2028,1,1));
        assertThat(event.getValue().getNetMonthly()).isEqualByComparingTo("2250");
        assertThat(event.getValue().getArr()).isEqualByComparingTo("27000");
    }

    @ParameterizedTest @EnumSource(value = SubscriptionStatus.class, names = {"PENDING", "SUSPENDED", "CANCELLED", "EXPIRED"})
    void inactiveStatesNeverApply(SubscriptionStatus status) {
        subscription.setStatus(status);
        assertThat(service.applyDue(42L)).isZero();
        assertThat(due.isApplied()).isFalse();
        verifyNoInteractions(outbox);
    }

    @Test void twoLinesAtSequenceOnePublishIndependentlyAndSumRates() {
        due.setSequence(1);
        var second = SubscriptionRatePeriod.builder().subscriptionId(42L).acceptedLineUuid("line-2").sequence(1)
                .startDate(due.getStartDate()).endDate(due.getEndDate()).effectiveEndDate(due.getEffectiveEndDate())
                .netMonthly(new BigDecimal("100")).annualRunRate(new BigDecimal("1200")).currencyCode("USD").build();
        when(rates.findBySubscriptionIdAndDeletedFalseOrderByStartDateAscAcceptedLineUuidAscSequenceAsc(42L)).thenReturn(List.of(due, second));
        assertThat(service.applyDue(42L)).isEqualTo(2);
        assertThat(subscription.getMrr()).isEqualByComparingTo("2350");
        verify(outbox, times(2)).publish(anyString(), anyString(), eq(EventContractRegistry.RAMP_APPLIED_V1), any(RampAppliedEvent.class));
    }

    @Test void retiredOrBackfilledAppliedRatesNeverRepublish() {
        due.setRetired(true);
        assertThat(service.applyDue(42L)).isZero();
        due.setRetired(false);
        due.setApplied(true);
        assertThat(service.applyDue(42L)).isZero();
        verifyNoInteractions(outbox);
    }
}
