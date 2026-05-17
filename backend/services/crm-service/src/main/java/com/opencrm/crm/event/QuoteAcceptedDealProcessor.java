package com.opencrm.crm.event;

import com.opencrm.common.event.dto.QuoteAcceptedCrmMessage;
import com.opencrm.crm.deal.Deal;
import com.opencrm.crm.deal.DealRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies quote-accepted opportunity state side effects to CRM deals inside a transaction.
 * Quote/contract services remain the source of truth for accepted commercial values.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuoteAcceptedDealProcessor {

    private final DealRepository dealRepository;

    @Transactional
    public void applyWinFromQuote(QuoteAcceptedCrmMessage event, String dealExternalId) {
        dealRepository.findByUuid(dealExternalId).ifPresentOrElse(
                deal -> applyToDeal(deal, event),
                () -> log.warn("Deal with UUID {} not found for QuoteAcceptedCrmMessage", dealExternalId)
        );
    }

    private void applyToDeal(Deal deal, QuoteAcceptedCrmMessage event) {
        deal.markWon();
        if (event.getAcceptedAt() != null) {
            deal.setWonAt(event.getAcceptedAt());
        }
        dealRepository.save(deal);
        log.info("Updated deal {} to WON from quote {}",
                deal.getUuid(),
                safeQuoteLabel(event));
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
