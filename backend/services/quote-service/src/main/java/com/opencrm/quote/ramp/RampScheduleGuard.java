package com.opencrm.quote.ramp;

import com.opencrm.quote.lineitem.QuoteLineItem;
import com.opencrm.quote.lineitem.QuoteLineItemRepository;
import com.opencrm.quote.quote.Quote;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Term, currency, or region changes mark a quote stale when any line has steps.
 * Submit and accept are refused until the schedule is valid for the new term and re-priced.
 * Calculate stays fail-closed in this slice, so a successful re-price is left to R1-WIRE.
 */
@Component
@RequiredArgsConstructor
public class RampScheduleGuard {

    private final QuoteLineItemRepository lineItemRepository;
    private final QuoteRampStepRepository rampStepRepository;
    private final RampScheduleValidator validator;
    private final RampPricingProperties properties;

    public void markIfContextChanged(Quote quote, int previousDuration, String previousCurrency, String previousRegion) {
        boolean changed = quote.getDurationInMonths() != previousDuration
                || !Objects.equals(quote.getCurrency(), previousCurrency)
                || !Objects.equals(quote.getRegion(), previousRegion);
        if (!changed) {
            return;
        }
        if (hasAnySteps(quote.getId())) {
            quote.setRampScheduleStale(true);
        }
    }

    /**
     * No-op when the quote is not stale. A term that no longer matches the stored
     * periods throws {@code RAMP_SCHEDULE_INVALID} with {@code RAMP_TERM_MISMATCH}.
     * A still-valid schedule throws {@code RAMP_SCHEDULE_STALE} until it is re-priced.
     */
    public void refuseIfStale(Quote quote) {
        if (!quote.isRampScheduleStale()) {
            return;
        }
        List<QuoteLineItem> lines = lineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(quote.getId());
        boolean any = false;
        for (QuoteLineItem line : lines) {
            List<QuoteRampStep> steps = activeSteps(line);
            if (steps.isEmpty()) {
                continue;
            }
            any = true;
            validator.validate(
                    steps.stream().map(RampScheduleViews::toDto).toList(),
                    quote.getDurationInMonths(),
                    line.getBillingFrequency(),
                    line.getLineItemType(),
                    properties.getMaxPeriods());
        }
        if (!any) {
            quote.setRampScheduleStale(false);
            return;
        }
        throw RampScheduleException.stale();
    }

    private boolean hasAnySteps(Long quoteId) {
        if (quoteId == null) {
            return false;
        }
        List<QuoteLineItem> lines = lineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(quoteId);
        for (QuoteLineItem line : lines) {
            if (!activeSteps(line).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private List<QuoteRampStep> activeSteps(QuoteLineItem line) {
        List<QuoteRampStep> onLine = RampScheduleViews.activeSteps(line);
        if (!onLine.isEmpty()) {
            return onLine;
        }
        if (line.getId() == null) {
            return List.of();
        }
        return rampStepRepository.findByLineItemIdAndDeletedFalseOrderBySequenceAsc(line.getId())
                .stream()
                .filter(step -> !step.isDeleted())
                .toList();
    }
}
