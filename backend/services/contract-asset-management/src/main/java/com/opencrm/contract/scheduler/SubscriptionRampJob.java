package com.opencrm.contract.scheduler;

import com.opencrm.contract.repository.SubscriptionRatePeriodRepository;
import com.opencrm.contract.service.SubscriptionRateApplyService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.PageRequest;
import lombok.extern.slf4j.Slf4j;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
@ConditionalOnProperty(name = "opencrm.subscription-ramp.enabled", havingValue = "true", matchIfMissing = true)
public class SubscriptionRampJob {
    private final SubscriptionRatePeriodRepository rates;
    private final SubscriptionRateApplyService apply;
    private final Clock clock;
    private final int batchSize;
    private final AtomicLong overdueSeconds;

    @Autowired
    public SubscriptionRampJob(SubscriptionRatePeriodRepository rates, SubscriptionRateApplyService apply,
            MeterRegistry meters, @Value("${opencrm.subscription-ramp.batch-size:20}") int batchSize) {
        this(rates, apply, meters, Clock.systemUTC(), batchSize);
    }
    SubscriptionRampJob(SubscriptionRatePeriodRepository rates, SubscriptionRateApplyService apply,
                        MeterRegistry meters, Clock clock, int batchSize) {
        if (batchSize < 1) throw new IllegalArgumentException("Subscription ramp batch size must be positive");
        this.rates = rates;
        this.apply = apply;
        this.clock = clock;
        this.batchSize = batchSize;
        this.overdueSeconds = meters.gauge("opencrm.subscription.ramp.overdue.seconds", new AtomicLong());
    }
    @Scheduled(fixedDelayString = "${opencrm.subscription-ramp.poll-interval:PT30S}",
            initialDelayString = "${opencrm.subscription-ramp.initial-delay:PT30S}")
    public void tick() {
        LocalDate today = LocalDate.now(clock);
        updateOverdue(today);
        for (Long id : rates.findDueSubscriptionIds(today, PageRequest.of(0, batchSize))) {
            try {
                apply.applyDue(id);
            } catch (Exception error) {
                log.error("Failed to apply subscription ramp for {}", id, error);
            }
        }
        updateOverdue(today);
    }

    private void updateOverdue(LocalDate today) {
        LocalDate oldest = rates.findOldestDueDate(today);
        overdueSeconds.set(oldest == null ? 0 : Math.max(0,
                Duration.between(oldest.atStartOfDay(ZoneOffset.UTC).toInstant(), clock.instant()).getSeconds()));
    }
}
