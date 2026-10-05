package com.opencrm.quote.quote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.dto.ApiResponse;
import com.opencrm.common.dto.ConfigurationValidationResult;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.quote.client.PricedPeriodView;
import com.opencrm.quote.client.PricingExecuteView;
import com.opencrm.quote.client.PricingLineResultView;
import com.opencrm.quote.client.PricingServiceClient;
import com.opencrm.quote.client.ProductServiceClient;
import com.opencrm.quote.event.QuoteEventPublisher;
import com.opencrm.quote.lineitem.QuoteLineItem;
import com.opencrm.quote.lineitem.QuoteLineItemRepository;
import com.opencrm.quote.ramp.QuoteRampStep;
import com.opencrm.quote.ramp.QuoteRampStepRepository;
import com.opencrm.quote.ramp.RampPricingProperties;
import com.opencrm.quote.ramp.RampReasons;
import com.opencrm.quote.ramp.RampScheduleException;
import com.opencrm.quote.ramp.RampScheduleGuard;
import com.opencrm.quote.ramp.RampScheduleValidator;
import com.opencrm.security.CurrentActor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Quote ramp stale, clone, and calculate")
class QuoteServiceRampTest {

    @Mock
    private QuoteRepository quoteRepository;
    @Mock
    private QuoteMapper quoteMapper;
    @Mock
    private QuoteNumberGenerator quoteNumberGenerator;
    @Mock
    private QuoteEventPublisher eventPublisher;
    @Mock
    private QuoteLineItemRepository quoteLineItemRepository;
    @Mock
    private PricingServiceClient pricingServiceClient;
    @Mock
    private ProductServiceClient productServiceClient;
    @Mock
    private OutboxEventPublisher outboxEventPublisher;
    @Mock
    private QuoteRampStepRepository quoteRampStepRepository;

    private QuoteService quoteService;

    @BeforeEach
    void setUp() {
        RampScheduleGuard guard = new RampScheduleGuard(
                quoteLineItemRepository,
                quoteRampStepRepository,
                new RampScheduleValidator(),
                new RampPricingProperties());
        quoteService = new QuoteService(
                quoteRepository,
                quoteMapper,
                quoteNumberGenerator,
                eventPublisher,
                quoteLineItemRepository,
                pricingServiceClient,
                productServiceClient,
                new ObjectMapper(),
                outboxEventPublisher,
                new CurrentActor("service:quote-service", false),
                guard);
    }

    @Test
    @DisplayName("a term change marks the schedule stale and submit reports RAMP_TERM_MISMATCH")
    void termChangeMarksStaleAndSubmitReportsMismatch() {
        Quote quote = quote(QuoteStatus.DRAFT, 36);
        QuoteLineItem line = lineWithStep(quote, 36);
        quote.getLineItems().add(line);
        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(quote));
        when(quoteLineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(1L)).thenReturn(List.of(line));
        when(quoteRepository.save(quote)).thenReturn(quote);
        when(quoteMapper.toDto(quote)).thenAnswer(inv -> QuoteDto.builder()
                .rampScheduleStale(quote.isRampScheduleStale())
                .build());
        doAnswer(inv -> {
            quote.setDurationInMonths(24);
            return null;
        }).when(quoteMapper).updateEntity(any(QuoteUpdateRequest.class), any(Quote.class));

        QuoteDto updated = quoteService.update(1L, QuoteUpdateRequest.builder().durationInMonths(24).build());

        assertThat(updated.isRampScheduleStale()).isTrue();
        assertThatThrownBy(() -> quoteService.submitForReview(1L))
                .isInstanceOf(RampScheduleException.class)
                .satisfies(ex -> {
                    RampScheduleException ramp = (RampScheduleException) ex;
                    assertThat(ramp.getErrorCode()).isEqualTo(RampReasons.SCHEDULE_INVALID);
                    assertThat(ramp.getReasons()).containsExactly(RampReasons.TERM_MISMATCH);
                    assertThat(ramp.getStatus().value()).isEqualTo(422);
                });
        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.DRAFT);
        verify(productServiceClient, never()).validateConfiguration(any());
    }

    @Test
    @DisplayName("currency and region changes mark a covered schedule stale")
    void currencyAndRegionMarkStale() {
        Quote currencyQuote = quote(QuoteStatus.DRAFT, 12);
        QuoteLineItem currencyLine = lineWithStep(currencyQuote, 12);
        stubContextChange(currencyQuote, currencyLine, () -> currencyQuote.setCurrency("EUR"));

        quoteService.update(1L, QuoteUpdateRequest.builder().currency("EUR").build());

        assertThat(currencyQuote.isRampScheduleStale()).isTrue();

        Quote regionQuote = quote(QuoteStatus.DRAFT, 12);
        QuoteLineItem regionLine = lineWithStep(regionQuote, 12);
        stubContextChange(regionQuote, regionLine, () -> regionQuote.setRegion("EU"));

        quoteService.update(1L, QuoteUpdateRequest.builder().region("EU").build());

        assertThat(regionQuote.isRampScheduleStale()).isTrue();
    }

    @Test
    @DisplayName("a name change, and a term change with no steps, leave the quote current")
    void unchangedContextAndEmptyScheduleStayCurrent() {
        Quote named = quote(QuoteStatus.DRAFT, 12);
        named.setId(2L);
        QuoteLineItem namedLine = lineWithStep(named, 12);
        when(quoteRepository.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(named));
        when(quoteRepository.save(named)).thenReturn(named);
        when(quoteMapper.toDto(named)).thenReturn(QuoteDto.builder().build());
        doAnswer(inv -> {
            named.setName("Renamed");
            return null;
        }).when(quoteMapper).updateEntity(any(QuoteUpdateRequest.class), any(Quote.class));

        quoteService.update(2L, QuoteUpdateRequest.builder().name("Renamed").build());

        assertThat(named.isRampScheduleStale()).isFalse();
        verify(quoteLineItemRepository, never()).findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(2L);

        Quote bare = quote(QuoteStatus.DRAFT, 36);
        QuoteLineItem flat = QuoteLineItem.builder()
                .quote(bare)
                .productExternalId("10")
                .lineItemType("SUBSCRIPTION")
                .quantity(1)
                .listPrice(new BigDecimal("40.00"))
                .rampSteps(new ArrayList<>())
                .build();
        flat.setId(8L);
        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(bare));
        when(quoteLineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(1L)).thenReturn(List.of(flat));
        when(quoteRepository.save(bare)).thenReturn(bare);
        when(quoteMapper.toDto(bare)).thenReturn(QuoteDto.builder().build());
        doAnswer(inv -> {
            bare.setDurationInMonths(24);
            return null;
        }).when(quoteMapper).updateEntity(any(QuoteUpdateRequest.class), any(Quote.class));

        quoteService.update(1L, QuoteUpdateRequest.builder().durationInMonths(24).build());

        assertThat(bare.isRampScheduleStale()).isFalse();
    }

    @Test
    @DisplayName("submit and accept of a still-valid stale schedule return RAMP_SCHEDULE_STALE")
    void submitAndAcceptRefuseStaleButValidSchedule() {
        Quote draft = quote(QuoteStatus.DRAFT, 12);
        draft.setRampScheduleStale(true);
        QuoteLineItem line = lineWithStep(draft, 12);
        draft.getLineItems().add(line);
        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(draft));
        when(quoteLineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(1L)).thenReturn(List.of(line));

        assertThatThrownBy(() -> quoteService.submitForReview(1L))
                .isInstanceOf(RampScheduleException.class)
                .satisfies(ex -> {
                    RampScheduleException ramp = (RampScheduleException) ex;
                    assertThat(ramp.getErrorCode()).isEqualTo(RampReasons.SCHEDULE_STALE);
                    assertThat(ramp.getStatus().value()).isEqualTo(422);
                });
        assertThat(draft.getStatus()).isEqualTo(QuoteStatus.DRAFT);
        verify(quoteRepository, never()).save(any());

        Quote sent = quote(QuoteStatus.SENT, 12);
        sent.setRampScheduleStale(true);
        QuoteLineItem sentLine = lineWithStep(sent, 12);
        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(sent));
        when(quoteLineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(1L)).thenReturn(List.of(sentLine));

        assertThatThrownBy(() -> quoteService.accept(1L))
                .isInstanceOf(RampScheduleException.class)
                .satisfies(ex -> assertThat(((RampScheduleException) ex).getErrorCode())
                        .isEqualTo(RampReasons.SCHEDULE_STALE));
        assertThat(sent.getStatus()).isEqualTo(QuoteStatus.SENT);
        verify(eventPublisher, never()).publishQuoteAccepted(any());
    }

    @Test
    @DisplayName("accept of a stale term mismatch reports RAMP_TERM_MISMATCH")
    void acceptReportsTermMismatch() {
        Quote sent = quote(QuoteStatus.SENT, 24);
        sent.setRampScheduleStale(true);
        QuoteLineItem line = lineWithStep(sent, 36);
        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(sent));
        when(quoteLineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(1L)).thenReturn(List.of(line));

        assertThatThrownBy(() -> quoteService.accept(1L))
                .isInstanceOf(RampScheduleException.class)
                .satisfies(ex -> {
                    RampScheduleException ramp = (RampScheduleException) ex;
                    assertThat(ramp.getErrorCode()).isEqualTo(RampReasons.SCHEDULE_INVALID);
                    assertThat(ramp.getReasons()).containsExactly(RampReasons.TERM_MISMATCH);
                });
        verify(quoteRepository, never()).save(any());
    }

    @Test
    @DisplayName("submit clears stale when no steps remain")
    void submitClearsStaleWhenStepsAreGone() {
        Quote quote = quote(QuoteStatus.DRAFT, 12);
        quote.setRampScheduleStale(true);
        QuoteLineItem line = QuoteLineItem.builder()
                .quote(quote)
                .productExternalId("10")
                .lineItemType("SUBSCRIPTION")
                .quantity(1)
                .listPrice(new BigDecimal("40.00"))
                .rampSteps(new ArrayList<>())
                .build();
        line.setId(9L);
        quote.getLineItems().add(line);
        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(quote));
        when(quoteLineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(1L)).thenReturn(List.of(line));
        when(productServiceClient.validateConfiguration(List.of(10L))).thenReturn(ConfigurationValidationResult.ok());
        when(quoteRepository.save(quote)).thenReturn(quote);
        when(quoteMapper.toDto(quote)).thenAnswer(inv -> QuoteDto.builder()
                .status(quote.getStatus())
                .rampScheduleStale(quote.isRampScheduleStale())
                .build());

        QuoteDto dto = quoteService.submitForReview(1L);

        assertThat(quote.isRampScheduleStale()).isFalse();
        assertThat(dto.isRampScheduleStale()).isFalse();
        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.IN_REVIEW);
    }

    @Test
    @DisplayName("clone copies steps, escalator columns, and the stale flag")
    void cloneCopiesSchedule() {
        Quote original = quote(QuoteStatus.SENT, 12);
        original.setRampScheduleStale(true);
        original.setExitArr(new BigDecimal("999.00"));
        QuoteLineItem source = lineWithStep(original, 12);
        source.setRampEscalatorMode("AMOUNT");
        source.setRampEscalatorStartPrice(new BigDecimal("40.0000"));
        source.setRampEscalatorAmount(new BigDecimal("5.0000"));
        source.setRampEscalatorEveryMonths(12);
        source.setPricingExplanationCode("LIST");
        source.setPricingWaterfallJson("{\"skip\":true}");
        QuoteRampStep deleted = step(2, 13, 12, new BigDecimal("45.0000"));
        deleted.softDelete();
        source.getRampSteps().add(deleted);

        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(original));
        when(quoteNumberGenerator.generate()).thenReturn("Q-COPY");
        when(quoteRepository.save(any(Quote.class))).thenAnswer(inv -> {
            Quote saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(99L);
            }
            return saved;
        });
        when(quoteLineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(1L)).thenReturn(List.of(source));
        AtomicLong nextId = new AtomicLong(1000L);
        when(quoteLineItemRepository.saveAll(any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            List<QuoteLineItem> items = inv.getArgument(0);
            for (QuoteLineItem item : items) {
                item.setId(nextId.getAndIncrement());
            }
            return items;
        });
        when(quoteMapper.toDto(any(Quote.class))).thenReturn(QuoteDto.builder().id(99L).build());

        quoteService.clone(1L);

        ArgumentCaptor<Quote> quoteCaptor = ArgumentCaptor.forClass(Quote.class);
        verify(quoteRepository, org.mockito.Mockito.atLeastOnce()).save(quoteCaptor.capture());
        Quote cloned = quoteCaptor.getAllValues().getFirst();
        assertThat(cloned.isRampScheduleStale()).isTrue();
        assertThat(cloned.getExitArr()).isNull();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<QuoteLineItem>> linesCaptor = ArgumentCaptor.forClass(List.class);
        verify(quoteLineItemRepository).saveAll(linesCaptor.capture());
        QuoteLineItem copy = linesCaptor.getValue().getFirst();
        assertThat(copy.getRampSteps()).isNotSameAs(source.getRampSteps());
        assertThat(copy.getRampSteps()).hasSize(1);
        assertThat(copy.getRampSteps().getFirst()).isNotSameAs(source.getRampSteps().getFirst());
        assertThat(copy.getRampSteps().getFirst().getUnitPrice()).isEqualByComparingTo("40.0000");
        assertThat(copy.getRampSteps().getFirst().getSequence()).isEqualTo(1);
        assertThat(copy.getRampSteps().getFirst().getLineItem()).isSameAs(copy);
        assertThat(copy.getRampEscalatorMode()).isEqualTo("AMOUNT");
        assertThat(copy.getRampEscalatorStartPrice()).isEqualByComparingTo("40.0000");
        assertThat(copy.getRampEscalatorAmount()).isEqualByComparingTo("5.0000");
        assertThat(copy.getRampEscalatorEveryMonths()).isEqualTo(12);
        assertThat(copy.getPricingExplanationCode()).isNull();
        assertThat(copy.getPricingWaterfallJson()).isNull();
    }

    @Test
    @DisplayName("calculate with a schedule and no period results returns 422 and persists nothing")
    void calculateWithScheduleAndNoPeriodsPersistsNothing() {
        assertUnpricedScheduleRejected(null);
    }

    @Test
    @DisplayName("calculate with a schedule and an empty period list returns 422 and persists nothing")
    void calculateWithScheduleAndEmptyPeriodsPersistsNothing() {
        assertUnpricedScheduleRejected(List.of());
    }

    private void assertUnpricedScheduleRejected(List<PricedPeriodView> periods) {
        Quote quote = quote(QuoteStatus.DRAFT, 12);
        QuoteLineItem line = lineWithStep(quote, 12);
        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(quote));
        when(quoteLineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(1L)).thenReturn(List.of(line));

        PricingLineResultView.PricingLineResultViewBuilder result = PricingLineResultView.builder()
                .productExternalId("10")
                .lineItemType("SUBSCRIPTION")
                .listPrice(new BigDecimal("40.00"))
                .finalPrice(new BigDecimal("40.00"));
        if (periods != null) {
            result.pricedPeriods(periods);
        }
        when(pricingServiceClient.executePricing(anyMap()))
                .thenReturn(ApiResponse.success(PricingExecuteView.builder()
                        .success(true)
                        .lineResults(List.of(result.build()))
                        .build()));

        assertThatThrownBy(() -> quoteService.calculate(1L))
                .isInstanceOf(RampScheduleException.class)
                .satisfies(ex -> {
                    RampScheduleException ramp = (RampScheduleException) ex;
                    assertThat(ramp.getErrorCode()).isEqualTo(RampReasons.NOT_SUPPORTED);
                    assertThat(ramp.getStatus().value()).isEqualTo(422);
                });

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> contextCaptor = ArgumentCaptor.forClass(Map.class);
        verify(pricingServiceClient).executePricing(contextCaptor.capture());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sentLines =
                (List<Map<String, Object>>) contextCaptor.getValue().get("lineItems");
        assertThat(sentLines.getFirst().get("billingFrequency")).isEqualTo("ANNUAL");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> schedule =
                (List<Map<String, Object>>) sentLines.getFirst().get("schedule");
        assertThat(schedule).hasSize(1);
        assertThat(schedule.getFirst()).containsEntry("priceBasis", "ABSOLUTE");
        assertThat(quote.getTcv()).isNull();
        assertThat(quote.getAcv()).isNull();
        assertThat(quote.getArr()).isNull();
        assertThat(quote.getExitArr()).isNull();
        assertThat(quote.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(line.getListPrice()).isEqualByComparingTo("40.00");
        verify(quoteRepository, never()).save(any());
        verify(quoteLineItemRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("flag-off 422 with no success body is RAMP_NOT_SUPPORTED and persists nothing")
    void calculateFlagOffIsNotSupported() {
        Quote quote = quote(QuoteStatus.DRAFT, 36);
        QuoteLineItem line = lineWithStep(quote, 36);
        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(quote));
        when(quoteLineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(1L)).thenReturn(List.of(line));
        when(pricingServiceClient.executePricing(anyMap())).thenReturn(ApiResponse.error(
                "A ramp schedule cannot be priced while opencrm.pricing.ramp.enabled is false",
                RampReasons.NOT_SUPPORTED,
                "trace-off"));

        assertThatThrownBy(() -> quoteService.calculate(1L))
                .isInstanceOf(RampScheduleException.class)
                .satisfies(ex -> {
                    RampScheduleException ramp = (RampScheduleException) ex;
                    assertThat(ramp.getErrorCode()).isEqualTo(RampReasons.NOT_SUPPORTED);
                    assertThat(ramp.getStatus().value()).isEqualTo(422);
                });

        assertThat(quote.getTcv()).isNull();
        assertThat(quote.getExitArr()).isNull();
        verify(quoteRepository, never()).save(any());
        verify(quoteLineItemRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("one-time frequency stays 422 RAMP_SCHEDULE_INVALID and is not relabeled")
    void calculateOneTimeFrequencyStaysScheduleInvalid() {
        assertNotAllowed("ONE_TIME", "SUBSCRIPTION");
    }

    @Test
    @DisplayName("a blank frequency stays 422 RAMP_SCHEDULE_INVALID")
    void calculateBlankFrequencyStaysScheduleInvalid() {
        assertNotAllowed("  ", "SUBSCRIPTION");
    }

    @Test
    @DisplayName("a missing frequency stays 422 RAMP_SCHEDULE_INVALID")
    void calculateMissingFrequencyStaysScheduleInvalid() {
        assertNotAllowed(null, "SUBSCRIPTION");
    }

    @Test
    @DisplayName("perpetual frequency stays 422 RAMP_SCHEDULE_INVALID")
    void calculatePerpetualFrequencyStaysScheduleInvalid() {
        assertNotAllowed("PERPETUAL", "SUBSCRIPTION");
    }

    @Test
    @DisplayName("a DISCOUNT line stays 422 RAMP_SCHEDULE_INVALID")
    void calculateDiscountLineStaysScheduleInvalid() {
        assertNotAllowed("MONTHLY", "DISCOUNT");
    }

    @Test
    @DisplayName("a FEE line stays 422 RAMP_SCHEDULE_INVALID")
    void calculateFeeLineStaysScheduleInvalid() {
        assertNotAllowed("MONTHLY", "FEE");
    }

    @Test
    @DisplayName("a TAX line stays 422 RAMP_SCHEDULE_INVALID")
    void calculateTaxLineStaysScheduleInvalid() {
        assertNotAllowed("MONTHLY", "TAX");
    }

    private void assertNotAllowed(String billingFrequency, String lineItemType) {
        Quote quote = quote(QuoteStatus.DRAFT, 36);
        QuoteLineItem line = lineWithStep(quote, 36);
        line.setBillingFrequency(billingFrequency);
        line.setLineItemType(lineItemType);
        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(quote));
        when(quoteLineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(1L)).thenReturn(List.of(line));
        when(pricingServiceClient.executePricing(anyMap())).thenReturn(ApiResponse.error(
                RampReasons.NOT_ALLOWED_FOR_LINE_TYPE,
                RampReasons.SCHEDULE_INVALID,
                "trace-type"));

        assertThatThrownBy(() -> quoteService.calculate(1L))
                .isInstanceOf(RampScheduleException.class)
                .satisfies(ex -> {
                    RampScheduleException ramp = (RampScheduleException) ex;
                    assertThat(ramp.getErrorCode()).isEqualTo(RampReasons.SCHEDULE_INVALID);
                    assertThat(ramp.getReasons()).containsExactly(RampReasons.NOT_ALLOWED_FOR_LINE_TYPE);
                    assertThat(ramp.getStatus().value()).isEqualTo(422);
                });

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> contextCaptor = ArgumentCaptor.forClass(Map.class);
        verify(pricingServiceClient).executePricing(contextCaptor.capture());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sentLines =
                (List<Map<String, Object>>) contextCaptor.getValue().get("lineItems");
        assertThat(sentLines.getFirst()).containsKey("billingFrequency");
        assertThat(sentLines.getFirst().get("billingFrequency")).isEqualTo(billingFrequency);
        assertThat(sentLines.getFirst().get("schedule")).isInstanceOf(List.class);
        assertThat(sentLines.getFirst()).doesNotContainKeys("steps", "escalator");
        verify(quoteRepository, never()).save(any());
        verify(quoteLineItemRepository, never()).saveAll(any());
    }

    private void stubContextChange(Quote quote, QuoteLineItem line, Runnable mutation) {
        when(quoteRepository.findByIdAndDeletedFalse(quote.getId())).thenReturn(Optional.of(quote));
        when(quoteLineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(quote.getId()))
                .thenReturn(List.of(line));
        when(quoteRepository.save(quote)).thenReturn(quote);
        when(quoteMapper.toDto(quote)).thenReturn(QuoteDto.builder().build());
        doAnswer(inv -> {
            mutation.run();
            return null;
        }).when(quoteMapper).updateEntity(any(QuoteUpdateRequest.class), any(Quote.class));
    }

    private static Quote quote(QuoteStatus status, int duration) {
        Quote quote = Quote.builder()
                .quoteNumber("Q-1")
                .name("Acme")
                .customerExternalId("CUST-1")
                .status(status)
                .currency("USD")
                .region("NA")
                .durationInMonths(duration)
                .lineItems(new ArrayList<>())
                .build();
        quote.setId(1L);
        return quote;
    }

    private static QuoteLineItem lineWithStep(Quote quote, int months) {
        QuoteLineItem line = QuoteLineItem.builder()
                .quote(quote)
                .productExternalId("10")
                .lineItemType("SUBSCRIPTION")
                .billingFrequency("ANNUAL")
                .quantity(1)
                .listPrice(new BigDecimal("40.00"))
                .unitPrice(new BigDecimal("40.00"))
                .rampSteps(new ArrayList<>())
                .build();
        line.setId(5L);
        QuoteRampStep row = step(1, 1, months, new BigDecimal("40.0000"));
        row.setLineItem(line);
        line.getRampSteps().add(row);
        return line;
    }

    private static QuoteRampStep step(int sequence, int startMonth, int months, BigDecimal unitPrice) {
        return QuoteRampStep.builder()
                .sequence(sequence)
                .startMonth(startMonth)
                .months(months)
                .quantity(1)
                .priceBasis("ABSOLUTE")
                .unitPrice(unitPrice)
                .build();
    }
}
