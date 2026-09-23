package com.opencrm.crm.event;

import com.opencrm.common.event.KafkaInbox;
import com.opencrm.common.event.dto.QuoteAcceptedCrmMessage;
import com.opencrm.crm.deal.Deal;
import com.opencrm.crm.deal.DealRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Applies quote-accepted side effects to CRM deals inside a transaction.
 *
 * <p>Called by both quote-accepted listeners during the ADR-018 D8 dual run (Redis and Kafka). Deduplication happens
 * here, in the deal transaction: the quote's key is claimed in {@code processed_events} only when the deal exists,
 * and the claim commits or rolls back with the deal update. A missing deal leaves no claim, so a later redelivery
 * can still apply; a second arrival of the same acceptance, on either channel, is skipped.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuoteAcceptedDealProcessor {

    private final DealRepository dealRepository;
    private final KafkaInbox inbox;

    /**
     * Marks the deal WON from quote acceptance fields, once per quote.
     *
     * @return {@link QuoteAcceptedOutcome#APPLIED} if this call updated the deal, {@link QuoteAcceptedOutcome#DUPLICATE}
     *         if the acceptance was already applied, {@link QuoteAcceptedOutcome#DEAL_NOT_FOUND} if the deal is missing
     *         (nothing claimed)
     */
    @Transactional
    public QuoteAcceptedOutcome applyWinFromQuote(QuoteAcceptedCrmMessage event, String dealExternalId) {
        Optional<Deal> deal = dealRepository.findByUuid(dealExternalId);
        if (deal.isEmpty()) {
            log.warn("Deal with UUID {} not found for quote {}; nothing claimed, a redelivery can still apply it",
                    dealExternalId, safeQuoteLabel(event));
            return QuoteAcceptedOutcome.DEAL_NOT_FOUND;
        }
        String key = QuoteAcceptedIdempotency.dedupeKey(event, dealExternalId);
        if (!inbox.claimKey(QuoteAcceptedIdempotency.CLAIM_NAMESPACE, key)) {
            log.info("Quote acceptance {} already applied to deal {}; skipping", key, dealExternalId);
            return QuoteAcceptedOutcome.DUPLICATE;
        }
        applyToDeal(deal.get(), event);
        return QuoteAcceptedOutcome.APPLIED;
    }

    private void applyToDeal(Deal deal, QuoteAcceptedCrmMessage event) {
        deal.markWon();
        if (event.getAcceptedAt() != null) {
            deal.setWonAt(event.getAcceptedAt());
        }
        if (event.getTotalAmount() != null) {
            deal.setAmount(event.getTotalAmount());
        }
        if (event.getCurrency() != null && !event.getCurrency().isBlank()) {
            deal.setCurrency(event.getCurrency());
        }
        dealRepository.save(deal);
        String amountLog = event.getTotalAmount() != null ? event.getTotalAmount().toPlainString() : "n/a";
        log.info("Updated deal {} to WON from quote {} (amount={})",
                deal.getUuid(),
                safeQuoteLabel(event),
                amountLog);
    }

    private static String safeQuoteLabel(QuoteAcceptedCrmMessage event) {
        if (event.getQuoteNumber() != null && !event.getQuoteNumber().isBlank()) {
            return event.getQuoteNumber();
        }
        if (event.getQuoteUuid() != null && !event.getQuoteUuid().isBlank()) {
            return event.getQuoteUuid();
        }
        return "(unknown)";
    }
}
