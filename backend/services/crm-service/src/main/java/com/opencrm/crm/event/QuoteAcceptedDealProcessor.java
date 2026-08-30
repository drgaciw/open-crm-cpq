package com.opencrm.crm.event;

import com.opencrm.common.event.dto.QuoteAcceptedCrmMessage;
import com.opencrm.crm.deal.Deal;
import com.opencrm.crm.deal.DealRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies quote-accepted side effects to CRM deals inside a transaction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuoteAcceptedDealProcessor {

    private final DealRepository dealRepository;

    /**
     * Marks the deal WON from quote acceptance fields.
     *
     * @return {@code true} if the deal was found and updated; {@code false} if missing (no-op).
     *         Callers must not retain the Redis idempotency key when this returns {@code false}.
     */
    @Transactional
    public boolean applyWinFromQuote(QuoteAcceptedCrmMessage event, String dealExternalId) {
        return dealRepository.findByUuid(dealExternalId).map(deal -> {
            applyToDeal(deal, event);
            return true;
        }).orElseGet(() -> {
            log.warn("Deal with UUID {} not found for QuoteAcceptedCrmMessage; not retaining idempotency key",
                    dealExternalId);
            return false;
        });
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
