package com.opencrm.quote.quote;

import com.opencrm.common.event.dto.QuoteAcceptedEvent;
import com.opencrm.quote.client.SubscriptionScheduleView;
import com.opencrm.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Continuation rates come exclusively from the validated accepted subscription schedule. */
public final class QuoteContinuationPolicy {
    private QuoteContinuationPolicy() {}
    public record Period(int sequence, int startMonth, int months, int quantity, BigDecimal unitPrice) {}
    public record Plan(LocalDate startDate, LocalDate endDate, int termMonths, List<Period> periods,
                       QuoteAcceptedEvent.ProrationSnapshot initialProration, BigDecimal tcv) {}
    public static Plan plan(QuoteContinuationRequest request, SubscriptionScheduleView schedule) {
        if (request == null || request.getOperation() == null || request.getQuantity() == null || request.getQuantity() < 1
                || request.getObservedRevision() == null || request.getObservedRevision() < 0
                || request.getTargetSubscriptionNumber() == null || request.getTargetSubscriptionNumber().isBlank()
                || request.getCustomerExternalId() == null || request.getCustomerExternalId().isBlank()
                || request.getCurrency() == null || request.getCurrency().isBlank()
                || request.getSourceSubscriptionLineUuid() == null || request.getSourceSubscriptionLineUuid().isBlank())
            throw invalid("Complete typed subscription change identities and a positive quantity are required");
        if (schedule == null || !Objects.equals(request.getTargetSubscriptionNumber(), schedule.getSubscriptionNumber())
                || !Objects.equals(request.getCustomerExternalId(), schedule.getCustomerId())
                || !Objects.equals(request.getCurrency(), schedule.getCurrencyCode())
                || !Objects.equals(request.getObservedRevision(), schedule.getCommercialRevision())
                || !"ACTIVE".equals(schedule.getStatus()) || schedule.getStartDate() == null || schedule.getEndDate() == null
                || schedule.getEndDate().isBefore(schedule.getStartDate()))
            throw invalid("Target subscription customer, currency, status or observed revision has changed");
        SubscriptionScheduleView.Line line = source(request, schedule);
        if (line.getProductType() == null || line.getLineItemType() == null || line.getBillingFrequency() == null
                || !AcceptedRampSnapshots.recurring(com.opencrm.quote.lineitem.QuoteLineItem.builder().lineItemType(line.getLineItemType())
                        .productType(line.getProductType()).billingFrequency(line.getBillingFrequency()).build()))
            throw invalid("The selected source has insufficient fixed recurring unit rate classification; recapture accepted pricing first");
        if (!"ACCEPTED_PRICED".equals(line.getProvenance()) || line.getProductExternalId() == null || line.getProductExternalId().isBlank()
                || line.getPeriods() == null || line.getPeriods().isEmpty())
            throw invalid("The selected subscription line has no trusted unit rate; recapture accepted pricing first");
        List<SubscriptionScheduleView.Period> rates = line.getPeriods().stream().filter(p -> p != null && !p.isRetired())
                .sorted(Comparator.comparing(SubscriptionScheduleView.Period::getStartDate, Comparator.nullsFirst(Comparator.naturalOrder()))).toList();
        for (var rate : rates) {
            if (rate.getStartDate() == null || rate.getEndDate() == null || rate.getEndDate().isBefore(rate.getStartDate())
                    || rate.getQuantity() == null || rate.getQuantity() < 0 || rate.getNetUnitPrice() == null || rate.getNetUnitPrice().signum() < 0
                    || rate.getNetMonthly() == null || money(rate.getNetUnitPrice().multiply(BigDecimal.valueOf(rate.getQuantity())))
                    .compareTo(rate.getNetMonthly()) != 0)
                throw invalid("The selected subscription line has incomplete unit rate facts; recapture accepted pricing first");
        }
        if (rates.isEmpty()) throw invalid("The selected subscription line has no current unit rate coverage");
        for (int i = 1; i < rates.size(); i++) {
            if (!rates.get(i).getStartDate().equals(rates.get(i-1).getEndDate().plusDays(1)))
                throw invalid("The selected source has overlapping or incomplete active rate coverage");
        }
        var last = rates.getLast();
        if (!last.getEndDate().equals(schedule.getEndDate()) || last.getQuantity() < 1)
            throw invalid("The selected source has no trusted co-termed exit unit rate");
        boolean renewal = request.getOperation() == QuoteContinuationRequest.Operation.RENEWAL
                || request.getOperation() == QuoteContinuationRequest.Operation.EXTENSION;
        if (renewal) {
            LocalDate start = schedule.getEndDate().plusDays(1);
            if (request.getEffectiveDate() != null && !request.getEffectiveDate().equals(start)) throw invalid("Renewal must start after the target's inclusive end date");
            int months = request.getTermMonths() == null ? 12 : request.getTermMonths();
            if (months < 1 || months > 1200) throw invalid("Renewal term must be between 1 and 1200 whole months");
            BigDecimal uplift = request.getUpliftPercent() == null ? BigDecimal.ZERO : request.getUpliftPercent();
            if (uplift.signum() < 0 || uplift.compareTo(new BigDecimal("100")) > 0
                    || (request.getOperation() == QuoteContinuationRequest.Operation.EXTENSION && uplift.signum() != 0))
                throw invalid("Extension preserves the exit rate; renewal uplift must be between zero and 100 percent");
            BigDecimal unit = money(last.getNetUnitPrice().multiply(BigDecimal.ONE.add(uplift.movePointLeft(2))));
            return new Plan(start,start.plusMonths(months).minusDays(1),months,
                    List.of(new Period(1,1,months,request.getQuantity(),unit)),null,
                    money(unit.multiply(BigDecimal.valueOf(request.getQuantity())).multiply(BigDecimal.valueOf(months))));
        }
        if (request.getTermMonths() != null || (request.getUpliftPercent() != null && request.getUpliftPercent().signum() != 0))
            throw invalid("Amendments and expansions retain the target term and accepted unit rates");
        LocalDate effective = request.getEffectiveDate();
        if (effective == null || effective.isBefore(schedule.getStartDate()) || effective.isAfter(schedule.getEndDate()))
            throw invalid("Effective date must lie within the target subscription term");
        LocalDate anchor = line.getBillingAnchorDate() == null ? schedule.getStartDate() : line.getBillingAnchorDate();
        if (effective.isBefore(anchor)) throw invalid("Effective date precedes the selected source billing term");
        long total = ChronoUnit.MONTHS.between(java.time.YearMonth.from(anchor),java.time.YearMonth.from(schedule.getEndDate().plusDays(1)));
        if (total < 1 || total > 1200 || !anchor.plusMonths(total).minusDays(1).equals(schedule.getEndDate()))
            throw invalid("Source billing anchor and co-term boundary are incompatible");
        int offset = 0;
        while (offset + 1 < total && !anchor.plusMonths(offset + 1).isAfter(effective)) offset++;
        LocalDate billingStart = anchor.plusMonths(offset), nextBoundary = anchor.plusMonths(offset + 1);
        var active = covering(rates,effective,effective);
        if (active.getQuantity() < 1) throw invalid("Effective interval has no active unit rate quantity");
        QuoteAcceptedEvent.ProrationSnapshot partial = null;
        int firstFull = offset;
        if (!effective.equals(billingStart)) {
            LocalDate end = nextBoundary.minusDays(1);
            var rate = covering(rates,effective,end);
            BigDecimal monthly = money(rate.getNetUnitPrice().multiply(BigDecimal.valueOf(request.getQuantity())));
            BigDecimal amount = monthly.multiply(BigDecimal.valueOf(ChronoUnit.DAYS.between(effective,end)+1))
                    .divide(BigDecimal.valueOf(ChronoUnit.DAYS.between(billingStart,end)+1),2,RoundingMode.HALF_UP);
            partial = QuoteAcceptedEvent.ProrationSnapshot.builder().startDate(effective.toString()).endDate(end.toString())
                    .billingPeriodStart(billingStart.toString()).billingPeriodEnd(end.toString()).quantity(request.getQuantity())
                    .netUnitPrice(rate.getNetUnitPrice()).netMonthly(monthly).periodAmount(amount).build();
            firstFull++;
        }
        List<Period> periods = new ArrayList<>();
        BigDecimal tcv = partial == null ? BigDecimal.ZERO : partial.getPeriodAmount();
        for (int month = firstFull; month < total; month++) {
            var rate = covering(rates,anchor.plusMonths(month),anchor.plusMonths(month+1).minusDays(1));
            if (rate.getQuantity() < 1) throw invalid("Future interval has no active unit rate quantity");
            tcv = tcv.add(money(rate.getNetUnitPrice().multiply(BigDecimal.valueOf(request.getQuantity()))));
            if (!periods.isEmpty() && periods.getLast().unitPrice().compareTo(rate.getNetUnitPrice()) == 0) {
                Period previous = periods.removeLast();
                periods.add(new Period(previous.sequence(),previous.startMonth(),previous.months()+1,previous.quantity(),previous.unitPrice()));
            } else periods.add(new Period(periods.size()+1,month-firstFull+1,1,request.getQuantity(),rate.getNetUnitPrice()));
        }
        // A final partial uses the existing positive nominal billing term, never a zero-month ramp.
        int termMonths = periods.isEmpty() ? 1 : (int)total-firstFull;
        LocalDate start = periods.isEmpty() ? effective : anchor.plusMonths(firstFull);
        return new Plan(start,schedule.getEndDate(),termMonths,List.copyOf(periods),partial,money(tcv));
    }
    static SubscriptionScheduleView.Line source(QuoteContinuationRequest request,SubscriptionScheduleView schedule) {
        List<SubscriptionScheduleView.Line> matches = schedule.getLines() == null ? List.of() : schedule.getLines().stream()
                .filter(Objects::nonNull).filter(l -> Objects.equals(request.getSourceSubscriptionLineUuid(),l.getAcceptedLineUuid())).toList();
        if (matches.size() != 1) throw invalid("Select exactly one accepted source line belonging to the target subscription");
        return matches.getFirst();
    }
    private static SubscriptionScheduleView.Period covering(List<SubscriptionScheduleView.Period> rates,LocalDate start,LocalDate end) {
        List<SubscriptionScheduleView.Period> matches = rates.stream().filter(p -> !start.isBefore(p.getStartDate()) && !end.isAfter(p.getEndDate())).toList();
        if (matches.size() != 1) throw invalid("Source rate coverage does not contain the requested billing interval");
        return matches.getFirst();
    }
    private static BigDecimal money(BigDecimal value) { return value.setScale(2,RoundingMode.HALF_UP); }
    private static BusinessException invalid(String message) { return new BusinessException(message,HttpStatus.CONFLICT,"SUBSCRIPTION_CONTINUATION_INVALID"); }
}
