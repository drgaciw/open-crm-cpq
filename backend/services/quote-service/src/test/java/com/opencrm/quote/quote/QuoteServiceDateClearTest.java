package com.opencrm.quote.quote;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.quote.client.PricingServiceClient;
import com.opencrm.quote.client.ProductServiceClient;
import com.opencrm.quote.event.QuoteEventPublisher;
import com.opencrm.quote.lineitem.QuoteLineItem;
import com.opencrm.quote.lineitem.QuoteLineItemRepository;
import com.opencrm.quote.ramp.QuoteRampStep;
import com.opencrm.security.CurrentActor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuoteServiceDateClearTest {
    private static final long QUOTE_ID = 7L;
    private static final LocalDate START = LocalDate.of(2030, 1, 1);
    private static final LocalDate END = LocalDate.of(2030, 12, 31);

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private final QuoteRepository quotes = mock(QuoteRepository.class);
    private final QuoteLineItemRepository lines = mock(QuoteLineItemRepository.class);
    private final OutboxEventPublisher outbox = mock(OutboxEventPublisher.class);
    private final QuoteService service = new QuoteService(
            quotes, Mappers.getMapper(QuoteMapper.class), mock(QuoteNumberGenerator.class),
            mock(QuoteEventPublisher.class), lines, mock(PricingServiceClient.class),
            mock(ProductServiceClient.class), json, outbox, new CurrentActor("service:quote-service", false),
            RampScheduleTestSupport.guard(lines, outbox));

    @ParameterizedTest
    @CsvSource({"DRAFT,true,false", "DRAFT,false,true", "REJECTED,true,true"})
    void clearsOnlyTheDatesRequestedOnAnOrdinaryQuote(QuoteStatus status, boolean clearStart, boolean clearEnd)
            throws Exception {
        Quote quote = quote(status);
        QuoteDto update = update(quote, """
                {"clearStartDate":%s,"clearEndDate":%s}
                """.formatted(clearStart, clearEnd));

        assertThat(update.getStartDate()).isEqualTo(clearStart ? null : START);
        assertThat(update.getEndDate()).isEqualTo(clearEnd ? null : END);
        assertThat(update.getValidUntil()).isEqualTo(END);
        assertThat(quote.getStatus()).isEqualTo(status);
        verify(quotes).save(quote);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"startDate\":null,\"endDate\":null}",
            "{\"clearStartDate\":false,\"clearEndDate\":null}"})
    void omissionAndNullKeepTheLegacyDates(String request) throws Exception {
        QuoteDto update = update(quote(QuoteStatus.DRAFT), request);

        assertThat(update.getStartDate()).isEqualTo(START);
        assertThat(update.getEndDate()).isEqualTo(END);
    }

    @Test
    void legacyDateReplacementStillWorks() throws Exception {
        QuoteDto update = update(quote(QuoteStatus.DRAFT), "{\"startDate\":\"2030-03-01\"}");

        assertThat(update.getStartDate()).isEqualTo(LocalDate.of(2030, 3, 1));
        assertThat(update.getEndDate()).isEqualTo(END);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"startDate\":\"2030-03-01\",\"clearStartDate\":true}",
            "{\"endDate\":\"2030-09-01\",\"clearEndDate\":true}"})
    void rejectsValuedAndClearForTheSameDate(String request) throws Exception {
        assertRejected(quote(QuoteStatus.DRAFT), request, "VALIDATION_ERROR");
    }

    @Test
    void rejectsClearOnANewQuoteWithATarget() throws Exception {
        Quote quote = quote(QuoteStatus.DRAFT);
        quote.setTargetSubscriptionNumber("SUB-OWNED");
        assertRejected(quote, "{\"clearStartDate\":true}", "SUBSCRIPTION_CONTINUATION_INVALID");
    }

    @Test
    void rejectsAddingATargetAndClearingADateInTheSameRequest() throws Exception {
        assertRejected(quote(QuoteStatus.DRAFT),
                "{\"clearEndDate\":true,\"targetSubscriptionNumber\":\"SUB-OWNED\"}",
                "SUBSCRIPTION_CONTINUATION_INVALID");
    }

    @Test
    void rejectsClearOnATypedContinuation() throws Exception {
        Quote quote = quote(QuoteStatus.DRAFT);
        quote.setContinuationOperation(QuoteContinuationRequest.Operation.RENEWAL);
        assertRejected(quote, "{\"clearEndDate\":true}", "SUBSCRIPTION_CONTINUATION_INVALID");
    }

    @Test
    void rejectsClearOnAQuotePendingRampApproval() throws Exception {
        Quote quote = quote(QuoteStatus.PENDING_APPROVAL);
        quote.setPricingWaterfallJson("{\"pricingFingerprint\":\"old-fingerprint\"}");
        assertRejected(quote, "{\"clearStartDate\":true}", "QUOTE_NOT_EDITABLE");
    }

    @Test
    void clearingADateOnAPricedRampMarksItStaleAndRetiresTheBinding() throws Exception {
        Quote quote = quote(QuoteStatus.DRAFT);
        quote.setPricingWaterfallJson("{\"pricingFingerprint\":\"old-fingerprint\"}");
        QuoteLineItem line = QuoteLineItem.builder().id(11L).quote(quote).productExternalId("10")
                .lineItemType("SUBSCRIPTION").billingFrequency("MONTHLY").quantity(1)
                .listPrice(BigDecimal.TEN).build();
        line.getRampSteps().add(QuoteRampStep.builder().lineItem(line).sequence(1).startMonth(1)
                .months(12).quantity(1).priceBasis("ABSOLUTE").unitPrice(BigDecimal.TEN).build());
        quote.getLineItems().add(line);
        when(lines.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(QUOTE_ID)).thenReturn(List.of(line));

        QuoteDto updated = update(quote, "{\"clearStartDate\":true}");

        assertThat(updated.getStartDate()).isNull();
        assertThat(updated.isRampScheduleStale()).isTrue();
        verify(outbox).publish(eq("Quote"), eq(Long.toString(QUOTE_ID)),
                eq(EventContractRegistry.RAMP_APPROVAL_BINDING_CHANGED_V1), any());
    }

    private Quote quote(QuoteStatus status) {
        Quote quote = Quote.builder().id(QUOTE_ID).quoteNumber("Q-DATE-CLEAR").status(status)
                .startDate(START).endDate(END).validUntil(END).build();
        when(quotes.findByIdAndDeletedFalse(QUOTE_ID)).thenReturn(Optional.of(quote));
        return quote;
    }

    private QuoteDto update(Quote quote, String request) throws Exception {
        when(quotes.save(quote)).thenReturn(quote);
        return service.update(QUOTE_ID, json.readValue(request, QuoteUpdateRequest.class));
    }

    private void assertRejected(Quote quote, String request, String code) throws Exception {
        QuoteUpdateRequest update = json.readValue(request, QuoteUpdateRequest.class);
        assertThatThrownBy(() -> service.update(QUOTE_ID, update))
                .isInstanceOfSatisfying(BusinessException.class, error -> assertThat(error.getErrorCode()).isEqualTo(code));
        assertThat(quote.getStartDate()).isEqualTo(START);
        assertThat(quote.getEndDate()).isEqualTo(END);
        verify(quotes, never()).save(any());
    }
}
