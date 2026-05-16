package com.opencrm.quote.quote;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.quote.lineitem.QuoteLineItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Enforces quote aggregate invariants before acceptance and after line mutations.
 */
final class QuoteAggregateInvariants {

    private static final BigDecimal TOTAL_TOLERANCE = new BigDecimal("0.01");

    private QuoteAggregateInvariants() {
    }

    static void assertAcceptanceReady(Quote quote, List<QuoteLineItem> lineItems) {
        assertNotImmutable(quote);
        assertLineContainment(quote, lineItems);
        assertTotalReconciliation(quote, lineItems);
    }

    static void assertNotImmutable(Quote quote) {
        if (quote.getStatus() == QuoteStatus.ACCEPTED) {
            throw new BusinessException("Accepted quotes are immutable", "QUOTE_IMMUTABLE");
        }
    }

    static void assertLineContainment(Quote quote, List<QuoteLineItem> lineItems) {
        for (QuoteLineItem lineItem : lineItems) {
            if (lineItem.isDeleted()) {
                continue;
            }
            if (lineItem.getQuote() != null && lineItem.getQuote().getId() != null
                    && !lineItem.getQuote().getId().equals(quote.getId())) {
                throw new BusinessException(
                        "Line item does not belong to quote",
                        "QUOTE_LINE_CONTAINMENT_VIOLATION"
                );
            }
        }
    }

    static void assertTotalReconciliation(Quote quote, List<QuoteLineItem> lineItems) {
        BigDecimal lineExtendedTotal = lineItems.stream()
                .filter(line -> !line.isDeleted())
                .map(QuoteAggregateInvariants::lineExtendedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal expectedTotal = quote.getSubtotal()
                .subtract(nullSafe(quote.getTotalDiscount()))
                .add(nullSafe(quote.getTotalTax()));

        if (expectedTotal.subtract(nullSafe(quote.getTotalAmount())).abs().compareTo(TOTAL_TOLERANCE) > 0) {
            throw new BusinessException(
                    "Quote totals are not reconciled with header amounts",
                    "QUOTE_TOTAL_RECONCILIATION_FAILED"
            );
        }

        if (lineItems.stream().anyMatch(line -> !line.isDeleted())
                && lineExtendedTotal.subtract(nullSafe(quote.getSubtotal())).abs().compareTo(TOTAL_TOLERANCE) > 0) {
            throw new BusinessException(
                    "Quote subtotal does not reconcile with line extended amounts",
                    "QUOTE_LINE_TOTAL_RECONCILIATION_FAILED"
            );
        }
    }

    private static BigDecimal lineExtendedAmount(QuoteLineItem lineItem) {
        if (lineItem.getExtendedAmount() != null) {
            return lineItem.getExtendedAmount();
        }
        BigDecimal unitPrice = lineItem.getUnitPrice() != null ? lineItem.getUnitPrice() : lineItem.getListPrice();
        return unitPrice.multiply(BigDecimal.valueOf(lineItem.getQuantity())).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nullSafe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
