package com.opencrm.crm.event;

import com.opencrm.common.event.dto.QuoteAcceptedCrmMessage;
import com.opencrm.crm.deal.Deal;
import com.opencrm.crm.deal.DealRepository;
import com.opencrm.crm.deal.DealStage;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuoteAcceptedDealProcessorTest {

    @Mock
    private DealRepository dealRepository;

    @InjectMocks
    private QuoteAcceptedDealProcessor processor;

    private Deal deal;

    @BeforeEach
    void setUp() {
        deal = Deal.builder()
                .name("Opportunity")
                .uuid("550e8400-e29b-41d4-a716-446655440000")
                .stage(DealStage.PROPOSAL)
                .amount(new BigDecimal("12000.00"))
                .currency("USD")
                .build();
    }

    @Test
    void applyWinFromQuote_marksWonAndPreservesCrmCommercialFields() {
        when(dealRepository.findByUuid(deal.getUuid())).thenReturn(Optional.of(deal));

        Instant acceptedAt = Instant.parse("2025-05-02T12:00:00Z");
        QuoteAcceptedCrmMessage msg = QuoteAcceptedCrmMessage.builder()
                .dealExternalId(deal.getUuid())
                .totalAmount(new BigDecimal("15000.00"))
                .currency("EUR")
                .acceptedAt(acceptedAt)
                .quoteNumber("Q-1")
                .build();

        processor.applyWinFromQuote(msg, deal.getUuid());

        ArgumentCaptor<Deal> captor = ArgumentCaptor.forClass(Deal.class);
        verify(dealRepository).save(captor.capture());
        Deal saved = captor.getValue();
        assertThat(saved.getStage()).isEqualTo(DealStage.WON);
        assertThat(saved.getAmount()).isEqualByComparingTo("12000.00");
        assertThat(saved.getCurrency()).isEqualTo("USD");
        assertThat(saved.getWonAt()).isEqualTo(acceptedAt);
    }

    @Test
    void applyWinFromQuote_noOpWhenDealMissing() {
        when(dealRepository.findByUuid("missing")).thenReturn(Optional.empty());

        processor.applyWinFromQuote(QuoteAcceptedCrmMessage.builder().quoteNumber("Q-9").build(), "missing");

        verify(dealRepository).findByUuid("missing");
        verify(dealRepository, never()).save(any());
    }
}
