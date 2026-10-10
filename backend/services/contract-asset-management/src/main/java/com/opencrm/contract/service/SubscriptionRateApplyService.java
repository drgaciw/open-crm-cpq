package com.opencrm.contract.service;

import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.dto.RampAppliedEvent;
import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.SubscriptionRatePeriod;
import com.opencrm.contract.domain.SubscriptionStatus;
import com.opencrm.contract.domain.ErpSyncStatus;
import com.opencrm.contract.repository.SubscriptionRepository;
import com.opencrm.contract.repository.SubscriptionRatePeriodRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.Comparator;

/** One short transaction owns the due rate marker, financial state and outbox row. */
@Service
public class SubscriptionRateApplyService {
    private final SubscriptionRepository subscriptions;
    private final SubscriptionRatePeriodRepository rates;
    private final OutboxEventPublisher outbox;
    private final Clock clock;

    @Autowired
    public SubscriptionRateApplyService(SubscriptionRepository subscriptions, SubscriptionRatePeriodRepository rates,
                                        OutboxEventPublisher outbox) {
        this(subscriptions, rates, outbox, Clock.systemUTC());
    }
    SubscriptionRateApplyService(SubscriptionRepository subscriptions, SubscriptionRatePeriodRepository rates,
                                 OutboxEventPublisher outbox, Clock clock) {
        this.subscriptions = subscriptions;
        this.rates = rates;
        this.outbox = outbox;
        this.clock = clock;
    }
    @Transactional
    public int applyDue(Long id) {
        return subscriptions.findByIdForUpdate(id).map(this::applyLocked).orElse(0);
    }

    @Transactional
    public int applyLocked(Subscription subscription) {
        if (subscription.isDeleted() || subscription.getStatus() != SubscriptionStatus.ACTIVE) return 0;
        LocalDate today = LocalDate.now(clock);
        var stored = rates.findBySubscriptionIdAndDeletedFalseOrderByStartDateAscAcceptedLineUuidAscSequenceAsc(subscription.getId());
        var due = stored.stream().filter(period -> !period.isDeleted() && !period.isRetired() && !period.isApplied()
                && !period.getStartDate().isAfter(today)).sorted(Comparator.comparing(SubscriptionRatePeriod::getStartDate)
                .thenComparing(SubscriptionRatePeriod::getAcceptedLineUuid).thenComparing(SubscriptionRatePeriod::getSequence)).toList();
        if (due.isEmpty()) return 0;
        for (var period : due) {
            RampAppliedEvent event = RampAppliedEvent.builder().subscriptionNumber(subscription.getSubscriptionNumber())
                    .acceptedLineUuid(period.getAcceptedLineUuid()).sequence(period.getSequence()).effectiveDate(period.getStartDate())
                    .currencyCode(period.getCurrencyCode()).quantity(period.getQuantity()).netUnitPrice(period.getNetUnitPrice())
                    .netMonthly(period.getNetMonthly()).arr(period.getAnnualRunRate()).build();
            outbox.publish("Subscription", subscription.getSubscriptionNumber(), EventContractRegistry.RAMP_APPLIED_V1, event);
            period.setApplied(true);
        }
        BigDecimal mrr = stored.stream().filter(period -> RampScheduleService.covers(period, today))
                .map(SubscriptionRatePeriod::getNetMonthly).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal arr = stored.stream().filter(period -> RampScheduleService.covers(period, today))
                .map(SubscriptionRatePeriod::getAnnualRunRate).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean changed = subscription.getMrr() == null || subscription.getArr() == null
                || subscription.getMrr().compareTo(mrr) != 0 || subscription.getArr().compareTo(arr) != 0;
        subscription.setMrr(mrr);
        subscription.setArr(arr);
        if (changed) {
            subscription.setErpSyncStatus(ErpSyncStatus.QUEUED);
            subscription.setErpSyncClaimToken(null);
            subscription.setErpSyncClaimRevision(null);
        }
        rates.saveAll(due);
        subscriptions.save(subscription);
        return due.size();
    }
}
