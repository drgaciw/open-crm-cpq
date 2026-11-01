package com.opencrm.quote.ramp;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.quote.lineitem.*;
import com.opencrm.quote.quote.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RampExtensionSaveTest {
    final QuoteRepository quotes = mock(QuoteRepository.class);
    final QuoteLineItemRepository lines = mock(QuoteLineItemRepository.class);
    final RampScheduleGuard guard = mock(RampScheduleGuard.class);
    final RampPricingProperties properties = new RampPricingProperties();
    Quote quote;
    QuoteLineItem line;
    RampScheduleQueryService service;

    @BeforeEach void setup() {
        properties.setEnabled(true);
        quote = Quote.builder().quoteNumber("Q-7").durationInMonths(12).version(3L).build();
        quote.setId(7L);
        line = QuoteLineItem.builder().quote(quote).productExternalId("seats").quantity(2)
                .billingFrequency("MONTHLY").lineItemType("SAAS_SEAT_NAMED")
                .listPrice(new BigDecimal("40")).notes("preserve me").build();
        line.setId(9L);
        when(quotes.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(quote));
        var expander = new RampEscalatorExpander();
        service = new RampScheduleQueryService(quotes, properties, expander,
                new QuoteLineItemService(lines, quotes,
                        new RampScheduleAttacher(properties, new RampScheduleValidator(), expander), guard));
    }

    @Test void savesTermAndScheduleTogetherAndReturnsFlushedVersion() {
        when(lines.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(line));
        when(lines.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(7L)).thenReturn(List.of(line));
        when(quotes.saveAndFlush(quote)).thenAnswer(call -> { quote.setVersion(4L); return quote; });
        RampScheduleDto saved = service.extend(7L, request());
        assertThat(quote.getDurationInMonths()).isEqualTo(24);
        assertThat(line.getRampSteps()).extracting(QuoteRampStep::getStartMonth).containsExactly(1, 13);
        assertThat(line.getNotes()).isEqualTo("preserve me");
        assertThat(saved.getDurationInMonths()).isEqualTo(24);
        assertThat(saved.getQuoteVersion()).isEqualTo(4L);
        assertThat(quote.isRampScheduleStale()).isTrue();
        verify(guard).invalidateOnEdit(eq(quote), any());
        verify(quotes).saveAndFlush(quote);
    }

    @Test void staleVersionRefusesBeforeMutation() {
        var request = request(); request.setExpectedQuoteVersion(2L);
        assertThatThrownBy(() -> service.extend(7L, request)).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("QUOTE_VERSION_CONFLICT");
        assertThat(quote.getDurationInMonths()).isEqualTo(12);
        verify(lines, never()).save(any());
    }

    @Test void anotherLineCanCatchUpToAlreadyExtendedQuoteTerm() {
        quote.setDurationInMonths(24);
        quote.setRampScheduleStale(true);
        savesTermAndScheduleTogetherAndReturnsFlushedVersion();
        assertThat(quote.getUpdatedAt()).isNotNull();
    }

    @Test void refusesLineFromAnotherQuoteBeforeMutation() {
        line.setQuote(Quote.builder().id(99L).build());
        when(lines.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(line));
        assertThatThrownBy(() -> service.extend(7L, request())).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("LINE_ITEM_QUOTE_MISMATCH");
        assertThat(quote.getDurationInMonths()).isEqualTo(12);
    }

    @Test void acceptedQuoteCannotExtend() {
        quote.setStatus(QuoteStatus.ACCEPTED);
        assertThatThrownBy(() -> service.extend(7L, request())).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("QUOTE_NOT_EDITABLE");
        assertThat(quote.getDurationInMonths()).isEqualTo(12);
    }

    @Test void halfPairCannotSilentlyPreview() {
        var request = request(); request.setLineItemId(null);
        assertThatThrownBy(() -> service.extend(7L, request)).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("VALIDATION_ERROR");
    }

    @Test void concurrentFlushFailureIsAConflict() {
        when(lines.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(line));
        when(lines.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(7L)).thenReturn(List.of(line));
        when(quotes.saveAndFlush(quote)).thenThrow(new org.springframework.dao.OptimisticLockingFailureException("raced"));
        assertThatThrownBy(() -> service.extend(7L, request())).isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus().value()).isEqualTo(409))
                .extracting("errorCode").isEqualTo("QUOTE_VERSION_CONFLICT");
    }

    @Test void resultCarriesServerStatusAfterApprovalInvalidation() {
        quote.setStatus(QuoteStatus.PENDING_APPROVAL);
        quote.setPricingWaterfallJson("{\"pricingFingerprint\":\"bound\"}");
        doAnswer(call -> { quote.setStatus(QuoteStatus.DRAFT); return null; })
                .when(guard).invalidateOnEdit(eq(quote), any());
        when(lines.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(line));
        when(lines.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(7L)).thenReturn(List.of(line));
        when(quotes.saveAndFlush(quote)).thenReturn(quote);
        assertThat(service.extend(7L, request()).getQuoteStatus()).isEqualTo(QuoteStatus.DRAFT);
    }

    @Test void concurrentAutoFlushDuringInvalidationIsAConflict() {
        when(lines.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(line));
        when(lines.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(7L)).thenReturn(List.of(line));
        doThrow(new org.springframework.dao.OptimisticLockingFailureException("auto flush raced"))
                .when(guard).invalidateOnEdit(eq(quote), any());
        assertThatThrownBy(() -> service.extend(7L, request())).isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus().value()).isEqualTo(409))
                .extracting("errorCode").isEqualTo("QUOTE_VERSION_CONFLICT");
        verify(quotes, never()).saveAndFlush(any());
    }

    @Test void anotherShorterLineStillBlocksTheExtendedQuote() {
        when(lines.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(line));
        var other = QuoteLineItem.builder().quote(quote).billingFrequency("MONTHLY")
                .lineItemType("SAAS_SEAT_NAMED").build();
        other.getRampSteps().add(QuoteRampStep.builder().sequence(1).startMonth(1).months(12).quantity(2)
                .priceBasis("ABSOLUTE").unitPrice(new BigDecimal("40")).build());
        when(lines.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(7L)).thenReturn(List.of(line, other));
        when(quotes.saveAndFlush(quote)).thenReturn(quote);
        service.extend(7L, request());
        var realGuard = new RampScheduleGuard(lines, mock(QuoteRampStepRepository.class),
                new RampScheduleValidator(), properties, mock(com.opencrm.quote.client.ApprovalRoutingClient.class),
                mock(com.opencrm.common.event.OutboxEventPublisher.class));
        assertThatThrownBy(() -> realGuard.refuseIfStale(quote)).isInstanceOf(RampScheduleException.class)
                .satisfies(ex -> assertThat(((RampScheduleException) ex).getReasons()).contains(RampReasons.TERM_MISMATCH));
    }

    static RampExtendRequest request() {
        return RampExtendRequest.builder().lineItemId(9L).expectedQuoteVersion(3L).durationInMonths(24)
                .steps(List.of(RampStepDto.builder().sequence(1).startMonth(1).months(12).quantity(2)
                        .priceBasis(RampPriceBasis.ABSOLUTE).unitPrice(new BigDecimal("40")).build()))
                .escalator(RampEscalatorDto.builder().startPrice(new BigDecimal("40")).mode(RampEscalatorMode.PERCENT)
                        .amount(new BigDecimal("5")).everyMonths(12).build()).build();
    }
}
