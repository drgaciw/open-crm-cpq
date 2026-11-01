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
import java.util.List;
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
        service = new RampScheduleQueryService(quoteRepository, properties, new RampEscalatorExpander(),
                org.mockito.Mockito.mock(com.opencrm.quote.lineitem.QuoteLineItemService.class));
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
        verify(quoteRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("a term the step length does not divide previews a short final period and persists nothing")
    void indivisibleTermPersistsNothing() {
        properties.setEnabled(true);
        when(quoteRepository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(quote()));

        RampScheduleDto result = service.expand(7L, request(14, 1));
        assertThat(result.getSteps()).extracting(RampStepDto::getMonths).containsExactly(12, 2);
        assertThat(result.getSteps()).extracting(RampStepDto::getStartMonth).containsExactly(1, 13);

        verify(quoteRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("AC-532-6: flag off refuses extend and saves nothing")
    void extendFlagOffRefuses() {
        when(quoteRepository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(quote()));

        assertThatThrownBy(() -> service.extend(7L, extendRequest(60)))
                .isInstanceOf(RampScheduleException.class)
                .satisfies(ex -> {
                    RampScheduleException ramp = (RampScheduleException) ex;
                    assertThat(ramp.getErrorCode()).isEqualTo(RampReasons.NOT_SUPPORTED);
                    assertThat(ramp.getStatus().value()).isEqualTo(422);
                });

        verify(quoteRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(quoteRepository, never()).flush();
        verify(quoteRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("AC-532-6: a missing quote is a 404 and extend writes nothing")
    void extendMissingQuote() {
        properties.setEnabled(true);
        when(quoteRepository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.extend(7L, extendRequest(60)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(quoteRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(quoteRepository, never()).flush();
    }

    @Test
    @DisplayName("AC-532-3: flag on extends the preview and does not save or flush")
    void extendAppendsWithoutSaving() {
        properties.setEnabled(true);
        when(quoteRepository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(quote()));

        RampScheduleDto first = service.extend(7L, extendRequest(60));
        RampScheduleDto second = service.extend(7L, extendRequest(60));

        assertThat(first.getSteps()).extracting(step -> step.getUnitPrice().toPlainString())
                .containsExactly("42.8000", "45.7960", "49.0017", "52.4318", "56.1020");
        assertThat(second.getSteps()).extracting(step -> step.getUnitPrice().toPlainString())
                .containsExactly("42.8000", "45.7960", "49.0017", "52.4318", "56.1020");
        assertThat(first.getSteps()).extracting(RampStepDto::getStartMonth).containsExactly(1, 13, 25, 37, 49);
        verify(quoteRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(quoteRepository, never()).flush();
        verify(quoteRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("AC-532-5: total periods above the cap is RAMP_TOO_MANY_PERIODS and saves nothing")
    void extendCapApplies() {
        properties.setEnabled(true);
        properties.setMaxPeriods(4);
        when(quoteRepository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(quote()));

        assertThatThrownBy(() -> service.extend(7L, extendRequest(60)))
                .isInstanceOf(RampScheduleException.class)
                .satisfies(ex -> assertThat(((RampScheduleException) ex).getReasons())
                        .containsExactly(RampReasons.TOO_MANY_PERIODS));

        verify(quoteRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(quoteRepository, never()).flush();
    }

    @Test
    @DisplayName("AC-532-5: a rejected extend writes nothing")
    void extendRejectionWritesNothing() {
        properties.setEnabled(true);
        when(quoteRepository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(quote()));
        RampExtendRequest request = extendRequest(36);

        assertThatThrownBy(() -> service.extend(7L, request))
                .isInstanceOf(RampScheduleException.class)
                .satisfies(ex -> assertThat(((RampScheduleException) ex).getReasons())
                        .containsExactly(RampReasons.TERM_MISMATCH));

        verify(quoteRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(quoteRepository, never()).flush();
        verify(quoteRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
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

    private static RampExtendRequest extendRequest(int duration) {
        return RampExtendRequest.builder()
                .durationInMonths(duration)
                .steps(List.of(
                        step(1, 1, "42.8000"),
                        step(2, 13, "45.7960"),
                        step(3, 25, "49.0017")))
                .escalator(RampEscalatorDto.builder()
                        .mode(RampEscalatorMode.PERCENT)
                        .startPrice(new BigDecimal("42.80"))
                        .amount(new BigDecimal("7"))
                        .everyMonths(12)
                        .build())
                .build();
    }

    private static RampStepDto step(int sequence, int startMonth, String price) {
        return RampStepDto.builder()
                .sequence(sequence)
                .startMonth(startMonth)
                .months(12)
                .quantity(2)
                .priceBasis(RampPriceBasis.ABSOLUTE)
                .unitPrice(new BigDecimal(price))
                .build();
    }
}
