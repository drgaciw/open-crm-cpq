package com.opencrm.quote.approval.spec;

import com.opencrm.quote.approval.testdoubles.InMemoryQuoteStatusStore;
import com.opencrm.quote.approval.testdoubles.NotImplementedQuoteRejectToDraft;
import com.opencrm.quote.approval.testdoubles.QuoteApprovalLink;
import com.opencrm.quote.approval.testdoubles.QuoteRejectToDraft;
import com.opencrm.quote.quote.QuoteStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * DRG-444 red spec: a rejected approval returns the quote to Draft.
 *
 * <p>Drives {@link NotImplementedQuoteRejectToDraft}. It does not call
 * {@code com.opencrm.quote.approval.ApprovalService#rejectRequest}, which sets {@link QuoteStatus#REJECTED}.
 * Feature re-points this spec at that BFF method and keeps the Draft assertion. Not the D1-3 cancel cascade.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DRG-444 quote BFF reject returns the quote to Draft")
class RejectToDraftTest {

    private static final long APPROVAL_ID = 7L;
    private static final long QUOTE_ID = 42L;

    @Mock
    private QuoteApprovalLink links;

    private InMemoryQuoteStatusStore quotes;

    @BeforeEach
    void setUp() {
        quotes = new InMemoryQuoteStatusStore();
        quotes.put(QUOTE_ID, QuoteStatus.PENDING_APPROVAL);
        when(links.quoteIdOf(APPROVAL_ID)).thenReturn(QUOTE_ID);
    }

    @Test
    @DisplayName("reject returns the quote to Draft")
    void rejectReturnsQuoteToDraft() {
        QuoteRejectToDraft reject = new NotImplementedQuoteRejectToDraft(links, quotes);

        QuoteStatus status = reject.reject(APPROVAL_ID, "discount too high");

        assertThat(status).isEqualTo(QuoteStatus.DRAFT);
        assertThat(quotes.statusOf(QUOTE_ID)).isEqualTo(QuoteStatus.DRAFT);
    }
}
