package com.opencrm.crm.event;

import com.opencrm.common.event.KafkaInbox;
import com.opencrm.common.event.dto.QuoteAcceptedCrmMessage;
import com.opencrm.crm.deal.Deal;
import com.opencrm.crm.deal.DealRepository;
import com.opencrm.crm.deal.DealStage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuoteAcceptedDealProcessorTest {

    @Mock
    private DealRepository dealRepository;

    @Mock
    private KafkaInbox inbox;

    @InjectMocks
    private QuoteAcceptedDealProcessor processor;

    private Deal deal;

    @BeforeEach
    void setUp() {
        deal = Deal.builder()
                .name("Opportunity")
                .uuid("550e8400-e29b-41d4-a716-446655440000")
                .stage(DealStage.PROPOSAL)
                .build();
    }

    private QuoteAcceptedCrmMessage message(Instant acceptedAt) {
        return QuoteAcceptedCrmMessage.builder()
                .dealExternalId(deal.getUuid())
                .totalAmount(new BigDecimal("15000.00"))
                .currency("EUR")
                .acceptedAt(acceptedAt)
                .quoteNumber("Q-1")
                .quoteUuid("quote-uuid-1")
                .build();
    }

    @Test
    void applyWinFromQuote_marksWonAndSetsAmountAndCurrency() {
        when(dealRepository.findByUuid(deal.getUuid())).thenReturn(Optional.of(deal));
        when(inbox.claimKey(QuoteAcceptedIdempotency.CLAIM_NAMESPACE, "quote-uuid-1")).thenReturn(true);
        Instant acceptedAt = Instant.parse("2025-05-02T12:00:00Z");

        QuoteAcceptedOutcome outcome = processor.applyWinFromQuote(message(acceptedAt), deal.getUuid());

        assertThat(outcome).isEqualTo(QuoteAcceptedOutcome.APPLIED);
        ArgumentCaptor<Deal> captor = ArgumentCaptor.forClass(Deal.class);
        verify(dealRepository).save(captor.capture());
        Deal saved = captor.getValue();
        assertThat(saved.getStage()).isEqualTo(DealStage.WON);
        assertThat(saved.getAmount()).isEqualByComparingTo("15000.00");
        assertThat(saved.getCurrency()).isEqualTo("EUR");
        assertThat(saved.getWonAt()).isEqualTo(acceptedAt);
    }

    @Test
    @DisplayName("ADR-018 D8: a second arrival of the same acceptance, on either channel, does not re-apply")
    void applyWinFromQuote_skipsAlreadyClaimedQuote() {
        when(dealRepository.findByUuid(deal.getUuid())).thenReturn(Optional.of(deal));
        when(inbox.claimKey(QuoteAcceptedIdempotency.CLAIM_NAMESPACE, "quote-uuid-1")).thenReturn(false);

        QuoteAcceptedOutcome outcome = processor.applyWinFromQuote(message(Instant.now()), deal.getUuid());

        assertThat(outcome).isEqualTo(QuoteAcceptedOutcome.DUPLICATE);
        verify(dealRepository, never()).save(any());
        assertThat(deal.getStage()).isEqualTo(DealStage.PROPOSAL);
    }

    @Test
    @DisplayName("a missing deal claims nothing, so a later redelivery can still apply")
    void applyWinFromQuote_returnsFalseWhenDealMissing() {
        when(dealRepository.findByUuid("missing")).thenReturn(Optional.empty());

        QuoteAcceptedOutcome outcome = processor.applyWinFromQuote(
                QuoteAcceptedCrmMessage.builder().quoteNumber("Q-9").build(), "missing");

        assertThat(outcome).isEqualTo(QuoteAcceptedOutcome.DEAL_NOT_FOUND);
        verify(dealRepository, never()).save(any());
        verify(inbox, never()).claimKey(anyString(), anyString());
    }
}
