package com.opencrm.contract.service;

import com.opencrm.contract.dto.RampScheduleDto;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.SubscriptionRatePeriod;
import com.opencrm.contract.repository.SubscriptionRepository;
import com.opencrm.contract.repository.SubscriptionRatePeriodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RampScheduleService {
    private final SubscriptionRepository subscriptions;
    private final SubscriptionRatePeriodRepository periods;

    public RampScheduleDto byNumber(String number, LocalDate asOf) {
        Subscription subscription = subscriptions.findBySubscriptionNumberAndDeletedFalse(number)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", number));
        return map(subscription, asOf == null ? LocalDate.now(java.time.Clock.systemUTC()) : asOf);
    }
    public RampScheduleDto byId(Long id, LocalDate asOf) {
        Subscription subscription = subscriptions.findById(id).filter(value -> !value.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", id));
        return map(subscription, asOf == null ? LocalDate.now(java.time.Clock.systemUTC()) : asOf);
    }

    private RampScheduleDto map(Subscription subscription, LocalDate asOf) {
        List<SubscriptionRatePeriod> stored = periods.findBySubscriptionIdAndDeletedFalseOrderByStartDateAscAcceptedLineUuidAscSequenceAsc(subscription.getId());
        Map<String, List<SubscriptionRatePeriod>> grouped = new LinkedHashMap<>();
        stored.forEach(period -> grouped.computeIfAbsent(period.getAcceptedLineUuid(), ignored -> new ArrayList<>()).add(period));
        List<RampScheduleDto.Line> lines = new ArrayList<>();
        BigDecimal activeMrr = BigDecimal.ZERO;
        BigDecimal activeArr = BigDecimal.ZERO;
        BigDecimal exitMrr = BigDecimal.ZERO;
        BigDecimal exitArr = BigDecimal.ZERO;
        for (var entry : grouped.entrySet()) {
            List<SubscriptionRatePeriod> ordered = entry.getValue().stream().sorted(Comparator.comparing(SubscriptionRatePeriod::getStartDate)
                    .thenComparing(SubscriptionRatePeriod::getSequence)).toList();
            var first = ordered.getFirst();
            var visible = ordered.stream().map(period -> new RampScheduleDto.Period(period.getSequence(), period.getStartMonth(),
                    period.getMonths(), period.getStartDate(), period.getEffectiveEndDate(), period.getEndDate(),
                    period.getQuantity(), period.getNetUnitPrice(), period.getNetMonthly(), period.getPeriodAmount(),
                    period.getGrossPeriodAmount(), period.getMigrationCredit(), period.isApplied(), period.isRetired())).toList();
            lines.add(new RampScheduleDto.Line(entry.getKey(), first.getProductExternalId(), first.getProductName(),
                    first.getProvenance(), first.getProductType(), first.getLineItemType(), first.getBillingFrequency(),
                    first.getBillingAnchorDate() == null ? subscription.getStartDate() : first.getBillingAnchorDate(), visible));
            for (var period : ordered) {
                if (covers(period, asOf)) {
                    activeMrr = activeMrr.add(zero(period.getNetMonthly()));
                    activeArr = activeArr.add(zero(period.getAnnualRunRate()));
                }
                if (covers(period, subscription.getEndDate())) {
                    exitMrr = exitMrr.add(zero(period.getNetMonthly()));
                    exitArr = exitArr.add(zero(period.getAnnualRunRate()));
                }
            }
        }
        return new RampScheduleDto(subscription.getSubscriptionNumber(), subscription.getCustomerId(), subscription.getCurrencyCode(),
                subscription.getStatus(), subscription.getStartDate(), subscription.getEndDate(), subscription.getCommercialRevision(),
                List.copyOf(lines), activeMrr, activeArr, exitMrr, exitArr);
    }
    static boolean covers(SubscriptionRatePeriod period, LocalDate date) {
        return !period.isDeleted() && !period.isRetired() && !date.isBefore(period.getStartDate()) && !date.isAfter(period.getEffectiveEndDate());
    }
    private static BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}
