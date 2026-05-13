package com.opencrm.quote.quote;

import com.opencrm.quote.lineitem.QuoteLineItem;
import jakarta.persistence.Version;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuoteAggregateInvariantsTest {

    @Test
    void addLineItemContainsLineAndReconcilesTotals() {
        Quote quote = draftQuote();
        QuoteLineItem line = lineItem("prod-1", 2, "10.00", "1.50", "18.50");

        quote.addLineItem(line);

        assertThat(line.getQuote()).isSameAs(quote);
        assertThat(quote.getLineItems()).containsExactly(line);
        assertThat(quote.getSubtotal()).isEqualByComparingTo("20.00");
        assertThat(quote.getTotalDiscount()).isEqualByComparingTo("1.50");
        assertThat(quote.getTotalAmount()).isEqualByComparingTo("18.50");
    }

    @Test
    void removeLineItemRejectsLineOwnedByAnotherQuote() {
        Quote quote = draftQuote();
        Quote otherQuote = draftQuote();
        QuoteLineItem foreignLine = lineItem("prod-1", 1, "10.00", "0.00", "10.00");
        otherQuote.addLineItem(foreignLine);

        assertThatThrownBy(() -> quote.removeLineItem(foreignLine))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong");
    }

    @Test
    void acceptedQuoteCannotBeMutated() {
        Quote quote = sentQuote();
        quote.addLineItem(lineItem("prod-1", 1, "10.00", "0.00", "10.00"));

        quote.accept(Instant.parse("2026-05-12T00:00:00Z"));

        assertThatThrownBy(() -> quote.addLineItem(lineItem("prod-2", 1, "10.00", "0.00", "10.00")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("accepted quote");
        assertThatThrownBy(() -> quote.removeLineItem(quote.getLineItems().get(0)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("accepted quote");
        assertThatThrownBy(() -> quote.recalculateTotals())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("accepted quote");
    }

    @Test
    void acceptReconcilesTotalsAndCapturesTimestampOnce() {
        Quote quote = sentQuote();
        quote.setSubtotal(new BigDecimal("999.00"));
        quote.addLineItem(lineItem("prod-1", 3, "8.00", "2.00", "22.00"));

        Instant acceptedAt = Instant.parse("2026-05-12T01:00:00Z");
        quote.accept(acceptedAt);

        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.ACCEPTED);
        assertThat(quote.getAcceptedAt()).isEqualTo(acceptedAt);
        assertThat(quote.getSubtotal()).isEqualByComparingTo("24.00");
        assertThat(quote.getTotalDiscount()).isEqualByComparingTo("2.00");
        assertThat(quote.getTotalAmount()).isEqualByComparingTo("22.00");
    }

    @Test
    void quoteUsesJpaVersionForMonotonicPersistence() throws NoSuchFieldException {
        Field version = Quote.class.getSuperclass().getDeclaredField("version");

        assertThat(version.getAnnotation(Version.class)).isNotNull();
    }

    private Quote draftQuote() {
        return Quote.builder()
                .quoteNumber("Q-1")
                .name("Test quote")
                .customerExternalId("customer-1")
                .status(QuoteStatus.DRAFT)
                .currency("USD")
                .build();
    }

    private Quote sentQuote() {
        Quote quote = draftQuote();
        quote.setStatus(QuoteStatus.SENT);
        return quote;
    }

    private QuoteLineItem lineItem(String productExternalId, int quantity, String listPrice, String discount, String extendedAmount) {
        BigDecimal list = new BigDecimal(listPrice);
        return QuoteLineItem.builder()
                .productExternalId(productExternalId)
                .quantity(quantity)
                .listPrice(list)
                .unitPrice(list)
                .discountAmount(new BigDecimal(discount))
                .extendedAmount(new BigDecimal(extendedAmount))
                .build();
    }
}
