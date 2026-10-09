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
 * Successful repricing persists the new snapshot and clears staleness.
 */
@Component
@RequiredArgsConstructor
public class RampScheduleGuard {

    private final QuoteLineItemRepository lineItemRepository;
    private final QuoteRampStepRepository rampStepRepository;
    private final RampScheduleValidator validator;
    private final RampPricingProperties properties;
    private final com.opencrm.quote.client.ApprovalRoutingClient approvalRoutingClient;
    private final com.opencrm.common.event.OutboxEventPublisher outboxEventPublisher;

    /** Durably queues canonical retirement on edit; local stale state blocks decisions while delivery catches up. */
    public void invalidateOnEdit(Quote quote, String previousAuthoringFingerprint) {
        List<QuoteLineItem> lines = lineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(quote.getId());
        if (RampPricingSnapshot.fingerprint(quote) == null
                || previousAuthoringFingerprint.equals(RampPricingSnapshot.authoringFingerprint(quote, lines))) return;
        quote.setRampScheduleStale(true);
        if (quote.getStatus() == com.opencrm.quote.quote.QuoteStatus.PENDING_APPROVAL) quote.setStatus(com.opencrm.quote.quote.QuoteStatus.DRAFT);
        com.opencrm.common.event.dto.DiscountApprovalRequiredEvent retired = new com.opencrm.common.event.dto.DiscountApprovalRequiredEvent(
                String.valueOf(quote.getId()), lines.isEmpty() ? null : String.valueOf(lines.getFirst().getId()),
                java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, quote.getCurrency(),
                RampPricingSnapshot.fingerprint(quote, lines), 1, "DEAL_DESK", "Ramp snapshot invalidated by commercial edit", false);
        publishBinding(retired);
    }

    /** The one quote-owned producer for pending bindings and their retirement (ADR-018 D9). */
    public void publishBinding(com.opencrm.common.event.dto.DiscountApprovalRequiredEvent event) {
        outboxEventPublisher.publish("Quote", event.getQuoteId(),
                com.opencrm.common.event.EventContractRegistry.RAMP_APPROVAL_BINDING_CHANGED_V1, event);
    }

    /** Canonical approval-routing is mandatory for current ramp approval decisions; no legacy fallback. */
    public void requireApproved(Quote quote) {
        refuseIfStale(quote);
        String fingerprint = RampPricingSnapshot.fingerprint(quote);
        List<QuoteLineItem> lines = lineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(quote.getId());
        if ((lines.stream().anyMatch(RampScheduleViews::hasSteps) || fingerprint != null)
                && (fingerprint == null || !fingerprint.equals(RampPricingSnapshot.fingerprint(quote, lines)))) {
            throw RampScheduleException.stale();
        }
        boolean policyRequired = RampPricingSnapshot.governance(quote) != null;
        if (fingerprint == null && !policyRequired) return;
        List<com.opencrm.quote.client.RoutingApprovalDto> approvals = approvalRoutingClient.getByQuoteId(quote.getId());
        List<com.opencrm.quote.client.RoutingApprovalDto> current = approvals.stream()
                .filter(request -> java.util.Objects.equals(fingerprint, request.getPricingFingerprint()))
                .filter(request -> !"SUPERSEDED".equals(request.getStatus()) && !"CANCELLED".equals(request.getStatus()))
                .toList();
        if ((policyRequired && (fingerprint == null || current.isEmpty()))
                || current.stream().anyMatch(request -> !"APPROVED".equals(request.getStatus()))) {
            throw new com.opencrm.common.exception.BusinessException("The current ramp requires approved governance",
                    org.springframework.http.HttpStatus.CONFLICT, "RAMP_APPROVAL_REQUIRED");
        }
    }

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
            if (RampPricingSnapshot.fingerprint(quote) != null) throw RampScheduleException.stale();
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
