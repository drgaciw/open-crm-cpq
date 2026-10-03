package com.opencrm.quote.ramp;

import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.quote.quote.Quote;
import com.opencrm.quote.quote.QuoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Ramp schedule expand preview")
class RampScheduleQueryServiceTest {

    @Mock
    private QuoteRepository quoteRepository;

    private RampPricingProperties properties;
    private RampScheduleQueryService service;

    @BeforeEach
    void setUp() {
        properties = new RampPricingProperties();
        service = new RampScheduleQueryService(quoteRepository, properties, new RampEscalatorExpander());
    }

    @Test
    @DisplayName("flag off returns 422 RAMP_NOT_SUPPORTED and saves nothing")
    void flagOffRefuses() {
        when(quoteRepository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(quote()));

        assertThatThrownBy(() -> service.expand(7L, request(36, 1)))
                .isInstanceOf(RampScheduleException.class)
                .satisfies(ex -> {
                    RampScheduleException ramp = (RampScheduleException) ex;
                    assertThat(ramp.getErrorCode()).isEqualTo(RampReasons.NOT_SUPPORTED);
                    assertThat(ramp.getStatus().value()).isEqualTo(422);
                });

        verify(quoteRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("flag on expands 5 percent and persists nothing")
    void flagOnExpandsWithoutSaving() {
        properties.setEnabled(true);
        when(quoteRepository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(quote()));

        RampScheduleDto preview = service.expand(7L, request(36, 2));

        assertThat(preview.getSteps()).extracting(step -> step.getUnitPrice().toPlainString())
                .containsExactly("40.0000", "42.0000", "44.1000");
        assertThat(preview.getSteps()).allSatisfy(step -> assertThat(step.getQuantity()).isEqualTo(2));
        assertThat(preview.getEscalator().getMode()).isEqualTo(RampEscalatorMode.PERCENT);
        verify(quoteRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("a missing quote is a 404")
    void missingQuote() {
        properties.setEnabled(true);
        when(quoteRepository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.expand(7L, request(12, 1)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("more than 36 expanded periods reports RAMP_TOO_MANY_PERIODS")
    void capAppliesToExpansion() {
        properties.setEnabled(true);
        when(quoteRepository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(quote()));
        RampExpandRequest request = request(37, 1);
        request.getEscalator().setEveryMonths(1);

        assertThatThrownBy(() -> service.expand(7L, request))
                .isInstanceOf(RampScheduleException.class)
                .satisfies(ex -> assertThat(((RampScheduleException) ex).getReasons())
                        .containsExactly(RampReasons.TOO_MANY_PERIODS));
    }

    private static Quote quote() {
        Quote quote = Quote.builder().quoteNumber("Q-7").build();
        quote.setId(7L);
        return quote;
    }

    private static RampExpandRequest request(int duration, int quantity) {
        return RampExpandRequest.builder()
                .durationInMonths(duration)
                .quantity(quantity)
                .escalator(RampEscalatorDto.builder()
                        .mode(RampEscalatorMode.PERCENT)
                        .startPrice(new BigDecimal("40"))
                        .amount(new BigDecimal("5"))
                        .everyMonths(12)
                        .build())
                .build();
    }
}
