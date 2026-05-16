package com.opencrm.quote.quote;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.quote.lineitem.QuoteLineItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuoteAggregateInvariantsTest {

    @Test
    void acceptanceReadyWhenLinesContainedAndTotalsReconcile() {
        Quote quote = quoteWithTotals();
        QuoteLineItem line = lineFor(quote, new BigDecimal("1000.00"));

        assertThatCode(() -> QuoteAggregateInvariants.assertAcceptanceReady(quote, List.of(line)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAcceptedQuoteMutation() {
        Quote quote = quoteWithTotals();
        quote.setStatus(QuoteStatus.ACCEPTED);

        assertThatThrownBy(() -> QuoteAggregateInvariants.assertNotImmutable(quote))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("immutable");
    }

    @Test
    void rejectsLineFromDifferentQuote() {
        Quote quote = quoteWithTotals();
        Quote other = Quote.builder().id(99L).build();
        QuoteLineItem foreignLine = lineFor(other, new BigDecimal("1000.00"));

        assertThatThrownBy(() -> QuoteAggregateInvariants.assertLineContainment(quote, List.of(foreignLine)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("does not belong");
    }

    @Test
    void rejectsHeaderTotalMismatch() {
        Quote quote = quoteWithTotals();
        quote.setTotalAmount(new BigDecimal("9999.00"));
        QuoteLineItem line = lineFor(quote, new BigDecimal("1000.00"));

        assertThatThrownBy(() -> QuoteAggregateInvariants.assertTotalReconciliation(quote, List.of(line)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("header amounts");
    }

    private static Quote quoteWithTotals() {
        return Quote.builder()
                .id(1L)
                .quoteNumber("Q-1")
                .status(QuoteStatus.SENT)
                .currency("USD")
                .subtotal(new BigDecimal("1000.00"))
                .totalDiscount(new BigDecimal("100.00"))
                .totalTax(new BigDecimal("90.00"))
                .totalAmount(new BigDecimal("990.00"))
                .build();
    }

    private static QuoteLineItem lineFor(Quote quote, BigDecimal extendedAmount) {
        return QuoteLineItem.builder()
                .quote(quote)
                .productExternalId("prod-1")
                .listPrice(extendedAmount)
                .unitPrice(extendedAmount)
                .extendedAmount(extendedAmount)
                .quantity(1)
                .build();
    }
}
