package com.opencrm.contract.scheduler;

import com.opencrm.contract.repository.SubscriptionRatePeriodRepository;
import com.opencrm.contract.service.SubscriptionRateApplyService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class SubscriptionRampJobTest {
    @Test void boundedBatchContinuesAfterOneFailureAndMetricClearsAfterCatchup() {
        var rates = mock(SubscriptionRatePeriodRepository.class);
        var apply = mock(SubscriptionRateApplyService.class);
        var meters = new SimpleMeterRegistry();
        var clock = Clock.fixed(Instant.parse("2028-01-05T12:00:00Z"), ZoneOffset.UTC);
        var job = new SubscriptionRampJob(rates, apply, meters, clock, 2);
        when(rates.findDueSubscriptionIds(LocalDate.of(2028,1,5), PageRequest.of(0,2))).thenReturn(List.of(41L,42L));
        when(rates.findOldestDueDate(LocalDate.of(2028,1,5))).thenReturn(LocalDate.of(2028,1,1)).thenReturn(null);
        when(apply.applyDue(41L)).thenThrow(new IllegalStateException("one bad row"));
        job.tick();
        verify(apply).applyDue(41L);
        verify(apply).applyDue(42L);
        assertThat(meters.get("opencrm.subscription.ramp.overdue.seconds").gauge().value()).isZero();
    }

    @Test void overdueMetricRemainsVisibleWhileWorkIsPending() {
        var rates = mock(SubscriptionRatePeriodRepository.class);
        var meters = new SimpleMeterRegistry();
        var clock = Clock.fixed(Instant.parse("2028-01-05T12:00:00Z"), ZoneOffset.UTC);
        var job = new SubscriptionRampJob(rates, mock(SubscriptionRateApplyService.class), meters, clock, 2);
        when(rates.findDueSubscriptionIds(any(), any())).thenReturn(List.of());
        when(rates.findOldestDueDate(any())).thenReturn(LocalDate.of(2028,1,1));
        job.tick();
        assertThat(meters.get("opencrm.subscription.ramp.overdue.seconds").gauge().value()).isEqualTo(388800);
    }
}
