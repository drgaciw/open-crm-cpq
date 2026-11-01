package com.opencrm.quote.lineitem;

import com.opencrm.quote.quote.Quote;
import com.opencrm.quote.quote.QuoteRepository;
import com.opencrm.quote.quote.QuoteStatus;
import com.opencrm.quote.ramp.QuoteRampStep;
import com.opencrm.quote.ramp.RampEscalatorDto;
import com.opencrm.quote.ramp.RampEscalatorExpander;
import com.opencrm.quote.ramp.RampEscalatorMode;
import com.opencrm.quote.ramp.RampPriceBasis;
import com.opencrm.quote.ramp.RampPricingProperties;
import com.opencrm.quote.ramp.RampReasons;
import com.opencrm.quote.ramp.RampScheduleAttacher;
import com.opencrm.quote.ramp.RampScheduleDto;
import com.opencrm.quote.ramp.RampScheduleException;
import com.opencrm.quote.ramp.RampScheduleValidator;
import com.opencrm.quote.ramp.RampStepDto;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Quote line ramp authoring")
class QuoteLineItemServiceRampTest {

    @Mock
    private QuoteLineItemRepository lineItemRepository;
    @Mock
    private QuoteRepository quoteRepository;

    private RampPricingProperties properties;
    private QuoteLineItemService service;
    private com.opencrm.common.event.OutboxEventPublisher outbox;

    @BeforeEach
    void setUp() {
        properties = new RampPricingProperties();
        RampScheduleAttacher attacher = new RampScheduleAttacher(
                properties,
                new RampScheduleValidator(),
                new RampEscalatorExpander());
        service = new QuoteLineItemService(lineItemRepository, quoteRepository, attacher, org.mockito.Mockito.mock(com.opencrm.quote.ramp.RampScheduleGuard.class));
    }

    @Test
    @DisplayName("flag off rejects a schedule with 422 and persists nothing")
    void flagOffPersistsNothing() {
        Quote quote = quote(36);
        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(quote));

        assertThatThrownBy(() -> service.addLineItem(1L, request(scheduleWith(step(1, 1, 36, 2, "40"), null))))
                .isInstanceOf(RampScheduleException.class)
                .satisfies(ex -> {
                    RampScheduleException ramp = (RampScheduleException) ex;
                    assertThat(ramp.getErrorCode()).isEqualTo(RampReasons.NOT_SUPPORTED);
                    assertThat(ramp.getStatus().value()).isEqualTo(422);
                });

        verify(lineItemRepository, never()).save(any());
        verify(quoteRepository, never()).save(any());
    }

    @Test
    @DisplayName("flag off still accepts a line with no schedule")
    void flagOffAllowsFlatLine() {
        Quote quote = quote(12);
        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(quote));
        stubSave();

        QuoteLineItemDto dto = service.addLineItem(1L, request(null));

        assertThat(dto.getSchedule()).isNull();
        assertThat(saved().getRampSteps()).isEmpty();
    }

    @Test
    @DisplayName("flag on stores explicit steps at scale 4 and keeps escalator columns for display")
    void flagOnStoresExplicitStepsAndEscalator() {
        properties.setEnabled(true);
        Quote quote = quote(36);
        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(quote));
        stubSave();

        RampEscalatorDto escalator = escalator();
        QuoteLineItemDto dto = service.addLineItem(1L, request(scheduleWith(
                step(1, 1, 36, 2, "40.1"),
                escalator)));

        QuoteLineItem saved = saved();
        assertThat(saved.getRampSteps()).hasSize(1);
        QuoteRampStep row = saved.getRampSteps().getFirst();
        assertThat(row.getLineItem()).isSameAs(saved);
        assertThat(row.getQuantity()).isEqualTo(2);
        assertThat(row.getUnitPrice()).isEqualByComparingTo("40.1000");
        assertThat(row.getUnitPrice().scale()).isEqualTo(4);
        assertThat(row.getPriceBasis()).isEqualTo("ABSOLUTE");
        assertThat(saved.getRampEscalatorMode()).isEqualTo("PERCENT");
        assertThat(saved.getRampEscalatorStartPrice()).isEqualByComparingTo("40.0000");
        assertThat(saved.getRampEscalatorAmount()).isEqualByComparingTo("5.0000");
        assertThat(saved.getRampEscalatorEveryMonths()).isEqualTo(12);
        assertThat(dto.getSchedule().getSteps()).hasSize(1);
        assertThat(dto.getSchedule().getEscalator().getMode()).isEqualTo(RampEscalatorMode.PERCENT);
    }

    @Test
    @DisplayName("discountFraction 0.05 is stored at scale 4 and 5 is not stored")
    void discountFractionIsAFraction() {
        properties.setEnabled(true);
        Quote quote = quote(12);
        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(quote));
        stubSave();

        service.addLineItem(1L, request(scheduleWith(discountStep("0.05"), null)));

        QuoteRampStep row = saved().getRampSteps().getFirst();
        assertThat(row.getDiscountFraction()).isEqualByComparingTo("0.0500");
        assertThat(row.getDiscountFraction().scale()).isEqualTo(4);
        assertThat(row.getUnitPrice()).isNull();

        assertThatThrownBy(() -> service.addLineItem(1L, request(scheduleWith(discountStep("5"), null))))
                .isInstanceOf(RampScheduleException.class)
                .satisfies(ex -> assertThat(((RampScheduleException) ex).getReasons())
                        .containsExactly(RampReasons.PRICE_BASIS));
        verify(lineItemRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("flag on expands an escalator and copies the line quantity onto each step")
    void flagOnExpandsEscalator() {
        properties.setEnabled(true);
        Quote quote = quote(36);
        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(quote));
        stubSave();

        service.addLineItem(1L, request(RampScheduleDto.builder().escalator(escalator()).build()));

        QuoteLineItem saved = saved();
        assertThat(saved.getRampSteps()).extracting(step -> step.getUnitPrice().toPlainString())
                .containsExactly("40.0000", "42.0000", "44.1000");
        assertThat(saved.getRampSteps()).allSatisfy(step -> assertThat(step.getQuantity()).isEqualTo(3));
        assertThat(saved.getRampEscalatorMode()).isEqualTo("PERCENT");
    }

    @Test
    @DisplayName("a gap is 422 RAMP_SCHEDULE_INVALID and persists nothing")
    void invalidGapPersistsNothing() {
        properties.setEnabled(true);
        Quote quote = quote(36);
        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(quote));
        RampScheduleDto schedule = scheduleWith(List.of(
                step(1, 1, 12, 1, "40"),
                step(2, 14, 23, 1, "42")), null);

        assertThatThrownBy(() -> service.addLineItem(1L, request(schedule)))
                .isInstanceOf(RampScheduleException.class)
                .satisfies(ex -> {
                    RampScheduleException ramp = (RampScheduleException) ex;
                    assertThat(ramp.getErrorCode()).isEqualTo(RampReasons.SCHEDULE_INVALID);
                    assertThat(ramp.getReasons()).containsExactly(RampReasons.GAP);
                });

        verify(lineItemRepository, never()).save(any());
        verify(quoteRepository, never()).save(any());
    }

    @Test
    @DisplayName("DISCOUNT and ONE_TIME lines are refused and persist nothing")
    void rejectedLineTypesPersistNothing() {
        properties.setEnabled(true);
        Quote quote = quote(12);
        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(quote));
        RampScheduleDto schedule = scheduleWith(step(1, 1, 12, 1, "40"), null);

        LineItemCreateRequest discount = request(schedule);
        discount.setLineItemType("DISCOUNT");
        LineItemCreateRequest oneTime = request(schedule);
        oneTime.setBillingFrequency("ONE_TIME");

        assertThatThrownBy(() -> service.addLineItem(1L, discount))
                .isInstanceOf(RampScheduleException.class)
                .satisfies(ex -> assertThat(((RampScheduleException) ex).getReasons())
                        .containsExactly(RampReasons.NOT_ALLOWED_FOR_LINE_TYPE));
        assertThatThrownBy(() -> service.addLineItem(1L, oneTime))
                .isInstanceOf(RampScheduleException.class)
                .satisfies(ex -> assertThat(((RampScheduleException) ex).getReasons())
                        .containsExactly(RampReasons.NOT_ALLOWED_FOR_LINE_TYPE));

        verify(lineItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("an escalator that does not divide the term persists a contiguous short final period")
    void indivisibleEscalatorPersistsNothing() {
        properties.setEnabled(true);
        Quote quote = quote(14);
        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(quote));

        stubSave();
        QuoteLineItemDto dto = service.addLineItem(
                1L,
                request(RampScheduleDto.builder().escalator(escalator()).build()));
        QuoteLineItem persisted = saved();
        assertThat(persisted.getRampSteps()).extracting(QuoteRampStep::getStartMonth).containsExactly(1, 13);
        assertThat(persisted.getRampSteps()).extracting(QuoteRampStep::getMonths).containsExactly(12, 2);
        assertThat(persisted.getRampSteps()).extracting(row -> row.getUnitPrice().toPlainString())
                .containsExactly("40.0000", "42.0000");
        assertThat(persisted.getRampSteps().stream().mapToInt(QuoteRampStep::getMonths).sum()).isEqualTo(14);
        assertThat(dto.getSchedule().getSteps()).extracting(RampStepDto::getMonths).containsExactly(12, 2);
    }

    @Test
    @DisplayName("a null schedule on update clears stored steps")
    void nullScheduleClearsSteps() {
        properties.setEnabled(true);
        Quote quote = quote(12);
        QuoteLineItem existing = existing(quote);
        QuoteRampStep row = QuoteRampStep.builder()
                .lineItem(existing)
                .sequence(1)
                .startMonth(1)
                .months(12)
                .quantity(1)
                .priceBasis("ABSOLUTE")
                .unitPrice(new BigDecimal("40.0000"))
                .build();
        existing.getRampSteps().add(row);
        existing.setRampEscalatorMode("PERCENT");
        existing.setRampEscalatorStartPrice(new BigDecimal("40.0000"));
        existing.setRampEscalatorAmount(new BigDecimal("5.0000"));
        existing.setRampEscalatorEveryMonths(12);
        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(quote));
        when(lineItemRepository.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(existing));
        stubSave();

        QuoteLineItemDto dto = service.updateLineItem(1L, 5L, request(null));

        assertThat(existing.getRampSteps()).isEmpty();
        assertThat(existing.getRampEscalatorMode()).isNull();
        assertThat(existing.getRampEscalatorStartPrice()).isNull();
        assertThat(dto.getSchedule()).isNull();
    }

    @Test
    void fullUpdateIdenticalRampPreservesPricesMetricsGovernanceAndPendingBinding() {
        Quote quote = quote(36);
        QuoteLineItem line = boundRampUpdateFixture(quote);
        String lineSnapshot = line.getPricingWaterfallJson();
        String quoteSnapshot = quote.getPricingWaterfallJson();
        LineItemCreateRequest update = request(com.opencrm.quote.ramp.RampScheduleViews.toDto(line));
        update.setQuantity(50); update.setListPrice(new BigDecimal("50.0000")); update.setBillingFrequency("MONTHLY");
        update.setNotes("Updated authoring note only");

        QuoteLineItemDto saved = service.updateLineItem(1L, 5L, update);

        assertThat(line.getPricingWaterfallJson()).isEqualTo(lineSnapshot);
        assertThat(saved.getPricedPeriods()).hasSize(3);
        assertThat(line.getUnitPrice()).isEqualByComparingTo("34");
        assertThat(line.getNetPrice()).isEqualByComparingTo("34");
        assertThat(line.getExtendedAmount()).isEqualByComparingTo("1700");
        assertThat(line.getDiscountRate()).isEqualByComparingTo("0.32");
        assertThat(line.getDiscountAmount()).isEqualByComparingTo("800");
        assertThat(line.getPricingExplanationCode()).isEqualTo("RAMP_DISCOUNT_THRESHOLD");
        assertThat(line.getNotes()).isEqualTo("Updated authoring note only");
        assertThat(quote.getPricingWaterfallJson()).isEqualTo(quoteSnapshot);
        assertThat(quote.getTotalAmount()).isEqualByComparingTo("1700");
        assertThat(quote.getTotalDiscount()).isEqualByComparingTo("800");
        assertThat(quote.getTcv()).isEqualByComparingTo("77400");
        assertThat(quote.getAcv()).isEqualByComparingTo("25800");
        assertThat(quote.getArr()).isEqualByComparingTo("20400");
        assertThat(quote.getExitArr()).isEqualByComparingTo("30000");
        assertThat(quote.isRampScheduleStale()).isFalse();
        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.PENDING_APPROVAL);
        assertThat(quote.getApprovalState()).isEqualTo(com.opencrm.quote.quote.ApprovalState.PENDING);
        org.mockito.Mockito.verifyNoInteractions(outbox);
        verify(quoteRepository, never()).save(any());
    }

    @Test
    void fullUpdateChangedRampInvalidatesSnapshotAndQueuesRetirement() {
        Quote quote = quote(36);
        QuoteLineItem line = boundRampUpdateFixture(quote);
        LineItemCreateRequest update = request(com.opencrm.quote.ramp.RampScheduleViews.toDto(line));
        update.setQuantity(50); update.setListPrice(new BigDecimal("50")); update.setBillingFrequency("MONTHLY");
        update.getSchedule().getSteps().get(1).setUnitPrice(new BigDecimal("40"));
        when(quoteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        QuoteLineItemDto saved = service.updateLineItem(1L, 5L, update);

        assertThat(line.getPricingWaterfallJson()).isNull();
        assertThat(saved.getPricedPeriods()).isNull();
        assertThat(quote.isRampScheduleStale()).isTrue();
        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.DRAFT);
        var event = ArgumentCaptor.forClass(com.opencrm.common.event.dto.DiscountApprovalRequiredEvent.class);
        verify(outbox).publish(org.mockito.ArgumentMatchers.eq("Quote"), org.mockito.ArgumentMatchers.eq("1"),
                org.mockito.ArgumentMatchers.eq(com.opencrm.common.event.EventContractRegistry.RAMP_APPROVAL_BINDING_CHANGED_V1), event.capture());
        assertThat(event.getValue().getApprovalRequired()).isFalse();
    }

    private QuoteLineItem boundRampUpdateFixture(Quote quote) {
        properties.setEnabled(true);
        QuoteLineItem line = existing(quote);
        line.setQuantity(50); line.setListPrice(new BigDecimal("50")); line.setBillingFrequency("MONTHLY");
        line.setNetPrice(new BigDecimal("34")); line.setUnitPrice(new BigDecimal("34"));
        line.setDiscountRate(new BigDecimal("0.32")); line.setPricingExplanationCode("RAMP_DISCOUNT_THRESHOLD");
        line.setExtendedAmount(new BigDecimal("1700")); line.setDiscountAmount(new BigDecimal("800"));
        for (int i = 0; i < 3; i++) line.getRampSteps().add(QuoteRampStep.builder().lineItem(line)
                .sequence(i + 1).startMonth(i * 12 + 1).months(12).quantity(50).priceBasis("ABSOLUTE")
                .unitPrice(new BigDecimal(new String[]{"34", "45", "50"}[i])).build());
        line.setPricingWaterfallJson("{\"pricedPeriods\":[{\"sequence\":1,\"netUnitPrice\":34},{\"sequence\":2,\"netUnitPrice\":45},{\"sequence\":3,\"netUnitPrice\":50}],\"rampPolicyContext\":\"policy-v1\"}");
        quote.setStatus(QuoteStatus.PENDING_APPROVAL);
        quote.setApprovalState(com.opencrm.quote.quote.ApprovalState.PENDING);
        quote.setSubtotal(new BigDecimal("2500")); quote.setTotalDiscount(new BigDecimal("800")); quote.setTotalAmount(new BigDecimal("1700"));
        quote.setTcv(new BigDecimal("77400")); quote.setAcv(new BigDecimal("25800"));
        quote.setArr(new BigDecimal("20400")); quote.setExitArr(new BigDecimal("30000"));
        quote.setPricingWaterfallJson("{\"pricingFingerprint\":\"" + com.opencrm.quote.ramp.RampPricingSnapshot.fingerprint(quote, List.of(line))
                + "\",\"rampGovernance\":{\"governingPeriod\":1,\"discountPercent\":32}}");
        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(quote));
        when(lineItemRepository.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(line));
        when(lineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(1L)).thenReturn(List.of(line));
        when(lineItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        outbox = org.mockito.Mockito.mock(com.opencrm.common.event.OutboxEventPublisher.class);
        var guard = new com.opencrm.quote.ramp.RampScheduleGuard(lineItemRepository,
                org.mockito.Mockito.mock(com.opencrm.quote.ramp.QuoteRampStepRepository.class),
                new RampScheduleValidator(), properties, org.mockito.Mockito.mock(com.opencrm.quote.client.ApprovalRoutingClient.class), outbox);
        service = new QuoteLineItemService(lineItemRepository, quoteRepository,
                new RampScheduleAttacher(properties, new RampScheduleValidator(), new RampEscalatorExpander()), guard);
        return line;
    }

    private void stubSave() {
        when(lineItemRepository.save(any(QuoteLineItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(lineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(1L)).thenReturn(List.of());
        when(quoteRepository.save(any(Quote.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private QuoteLineItem saved() {
        ArgumentCaptor<QuoteLineItem> captor = ArgumentCaptor.forClass(QuoteLineItem.class);
        verify(lineItemRepository).save(captor.capture());
        return captor.getValue();
    }

    private static Quote quote(int duration) {
        Quote quote = Quote.builder()
                .quoteNumber("Q-1")
                .name("Acme")
                .customerExternalId("CUST-1")
                .status(QuoteStatus.DRAFT)
                .durationInMonths(duration)
                .currency("USD")
                .region("NA")
                .build();
        quote.setId(1L);
        return quote;
    }

    private static QuoteLineItem existing(Quote quote) {
        QuoteLineItem item = QuoteLineItem.builder()
                .quote(quote)
                .productExternalId("10")
                .productName("Seat")
                .lineItemType("SUBSCRIPTION")
                .quantity(1)
                .listPrice(new BigDecimal("40.00"))
                .billingFrequency("ANNUAL")
                .rampSteps(new ArrayList<>())
                .build();
        item.setId(5L);
        return item;
    }

    private static LineItemCreateRequest request(RampScheduleDto schedule) {
        return LineItemCreateRequest.builder()
                .productExternalId("10")
                .productName("Seat")
                .lineItemType("SUBSCRIPTION")
                .quantity(3)
                .listPrice(new BigDecimal("40.00"))
                .billingFrequency("ANNUAL")
                .schedule(schedule)
                .build();
    }

    private static RampScheduleDto scheduleWith(RampStepDto step, RampEscalatorDto escalator) {
        return scheduleWith(step == null ? null : List.of(step), escalator);
    }

    private static RampScheduleDto scheduleWith(List<RampStepDto> steps, RampEscalatorDto escalator) {
        return RampScheduleDto.builder().steps(steps).escalator(escalator).build();
    }

    private static RampStepDto discountStep(String discountFraction) {
        return RampStepDto.builder()
                .sequence(1)
                .startMonth(1)
                .months(12)
                .quantity(2)
                .priceBasis(RampPriceBasis.DISCOUNT_FRACTION)
                .discountFraction(new BigDecimal(discountFraction))
                .build();
    }

    private static RampStepDto step(int sequence, int start, int months, int quantity, String price) {
        return RampStepDto.builder()
                .sequence(sequence)
                .startMonth(start)
                .months(months)
                .quantity(quantity)
                .priceBasis(RampPriceBasis.ABSOLUTE)
                .unitPrice(new BigDecimal(price))
                .build();
    }

    private static RampEscalatorDto escalator() {
        return RampEscalatorDto.builder()
                .mode(RampEscalatorMode.PERCENT)
                .startPrice(new BigDecimal("40"))
                .amount(new BigDecimal("5"))
                .everyMonths(12)
                .build();
    }
}
