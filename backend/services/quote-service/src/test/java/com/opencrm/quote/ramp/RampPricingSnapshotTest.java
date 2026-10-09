package com.opencrm.quote.ramp;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.quote.client.*;
import com.opencrm.quote.lineitem.*;
import com.opencrm.quote.quote.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class RampPricingSnapshotTest {
    @Test void ac06SecondPeriodChangeChangesFingerprintAlthoughWorstDiscountIsSame() {
        Quote quote = quote(); QuoteLineItem line = line(quote);
        String initial = RampPricingSnapshot.fingerprint(quote, List.of(line));
        line.getRampSteps().get(1).setUnitPrice(new BigDecimal("40"));
        assertThat(RampPricingSnapshot.fingerprint(quote, List.of(line))).isNotEqualTo(initial);
    }
    @Test void identicalNormalizedValuesReplaySameFingerprint() {
        Quote quote = quote(); QuoteLineItem line = line(quote);
        String initial = RampPricingSnapshot.fingerprint(quote, List.of(line));
        line.getRampSteps().getFirst().setUnitPrice(new BigDecimal("34.0000"));
        quote.setTcv(new BigDecimal("77400.0000"));
        assertThat(RampPricingSnapshot.fingerprint(quote, List.of(line))).isEqualTo(initial);
    }
    @Test void datesTaxesAndBillingFrequencyAreBound() {
        Quote quote = quote(); QuoteLineItem line = line(quote);
        String initial = RampPricingSnapshot.fingerprint(quote, List.of(line));
        quote.setStartDate(LocalDate.of(2027, 2, 1));
        assertThat(RampPricingSnapshot.fingerprint(quote, List.of(line))).isNotEqualTo(initial);
        line.setBillingFrequency("MONTHLY"); quote.setSubscriptionType("RENEWAL");
        assertThat(RampPricingSnapshot.fingerprint(quote, List.of(line))).isNotEqualTo(initial);
        quote.setStartDate(null); quote.setTotalTax(BigDecimal.ONE);
        assertThat(RampPricingSnapshot.fingerprint(quote, List.of(line))).isNotEqualTo(initial);
        quote.setTotalTax(BigDecimal.ZERO); line.setBillingFrequency("ANNUAL");
        assertThat(RampPricingSnapshot.fingerprint(quote, List.of(line))).isNotEqualTo(initial);
    }
    @Test void sameMoneyDifferentGovernancePolicyHasDifferentBinding() {
        Quote quote = quote(); QuoteLineItem line = line(quote);
        line.setPricingWaterfallJson("{\"rampPolicyContext\":\"policy-v1\",\"rampGovernance\":{\"approvalTier\":\"SALES_VP\",\"governingPeriod\":1}}");
        String original = RampPricingSnapshot.fingerprint(quote, List.of(line));
        line.setPricingWaterfallJson("{\"rampPolicyContext\":\"policy-v2\"}");
        assertThat(RampPricingSnapshot.fingerprint(quote, List.of(line))).isNotEqualTo(original);
    }
    @Test void commercialEditsInvalidateBeforeRepricingButIdenticalSaveDoesNot() {
        Quote quote = quote(); QuoteLineItem line = line(quote);
        quote.setStatus(QuoteStatus.PENDING_APPROVAL);
        quote.setPricingWaterfallJson("{\"pricingFingerprint\":\"" + RampPricingSnapshot.fingerprint(quote, List.of(line)) + "\"}");
        QuoteLineItemRepository lines = mock(QuoteLineItemRepository.class);
        var outbox = mock(com.opencrm.common.event.OutboxEventPublisher.class);
        when(lines.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(1L)).thenReturn(List.of(line));
        RampScheduleGuard guard = new RampScheduleGuard(lines, mock(QuoteRampStepRepository.class),
                new RampScheduleValidator(), new RampPricingProperties(), mock(ApprovalRoutingClient.class), outbox);
        String previousAuthoring = RampPricingSnapshot.authoringFingerprint(quote, List.of(line));
        line.getRampSteps().getFirst().setUnitPrice(new BigDecimal("34.0000"));
        guard.invalidateOnEdit(quote, previousAuthoring);
        verifyNoInteractions(outbox);
        line.getRampSteps().get(1).setUnitPrice(new BigDecimal("40"));
        guard.invalidateOnEdit(quote, previousAuthoring);
        var capture = org.mockito.ArgumentCaptor.forClass(com.opencrm.common.event.dto.DiscountApprovalRequiredEvent.class);
        verify(outbox).publish(eq("Quote"), eq("1"), eq(com.opencrm.common.event.EventContractRegistry.RAMP_APPROVAL_BINDING_CHANGED_V1), capture.capture());
        assertThat(capture.getValue().getApprovalRequired()).isFalse();
        assertThat(quote.isRampScheduleStale()).isTrue();
        assertThat(quote.getStatus()).isEqualTo(QuoteStatus.DRAFT);
    }
    @Test void persistedPeriodsRoundTripAndStaleHidesThem() {
        Quote quote = quote(); QuoteLineItem line = line(quote);
        line.setPricingWaterfallJson("{\"pricedPeriods\":[{\"sequence\":1,\"netUnitPrice\":34,\"periodAmount\":20400}]}");
        assertThat(RampPricingSnapshot.pricedPeriods(line)).hasSize(1);
        assertThat(RampPricingSnapshot.pricedPeriods(line).getFirst().getPeriodAmount()).isEqualByComparingTo("20400");
        quote.setRampScheduleStale(true);
        assertThat(RampPricingSnapshot.pricedPeriods(line)).isNull();
    }
    @Test void corruptAndNonobjectSnapshotsFailClosed() {
        for (String json : List.of("null", "[]", "{bad", "{\"pricedPeriods\":\"wrong\"}")) {
            QuoteLineItem line = line(quote()); line.setPricingWaterfallJson(json);
            assertThatThrownBy(() -> RampPricingSnapshot.pricedPeriods(line)).isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).getErrorCode()).isEqualTo("RAMP_PRICING_SNAPSHOT_INVALID");
        }
    }
    @Test void ac17StaleTermRefusesUntilRevalidatedAndPriced() {
        Quote quote = quote(); QuoteLineItem line = line(quote());
        QuoteLineItemRepository lines = mock(QuoteLineItemRepository.class);
        when(lines.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(1L)).thenReturn(List.of(line));
        RampScheduleGuard guard = new RampScheduleGuard(lines, mock(QuoteRampStepRepository.class),
                new RampScheduleValidator(), new RampPricingProperties(), mock(ApprovalRoutingClient.class), mock(com.opencrm.common.event.OutboxEventPublisher.class));
        quote.setDurationInMonths(24); quote.setRampScheduleStale(true);
        assertThatThrownBy(() -> guard.refuseIfStale(quote)).isInstanceOf(RampScheduleException.class)
                .hasMessageContaining("RAMP_TERM_MISMATCH");
        quote.setDurationInMonths(36);
        assertThatThrownBy(() -> guard.refuseIfStale(quote)).isInstanceOf(RampScheduleException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode()).isEqualTo("RAMP_SCHEDULE_STALE");
    }
    @Test void currentPendingAndRejectedCannotAcceptAndApprovedCan() {
        Quote quote = quote(); QuoteLineItem line = line(quote);
        QuoteLineItemRepository lines = mock(QuoteLineItemRepository.class);
        ApprovalRoutingClient routing = mock(ApprovalRoutingClient.class);
        when(lines.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(1L)).thenReturn(List.of(line));
        String fingerprint = RampPricingSnapshot.fingerprint(quote, List.of(line));
        quote.setPricingWaterfallJson("{\"pricingFingerprint\":\"" + fingerprint
                + "\",\"rampGovernance\":{\"governingPeriod\":1,\"discountPercent\":32}}");
        RampScheduleGuard guard = new RampScheduleGuard(lines, mock(QuoteRampStepRepository.class),
                new RampScheduleValidator(), new RampPricingProperties(), routing, mock(com.opencrm.common.event.OutboxEventPublisher.class));
        for (String state : List.of("PENDING", "REJECTED")) {
            when(routing.getByQuoteId(1L)).thenReturn(List.of(RoutingApprovalDto.builder().status(state).pricingFingerprint(fingerprint).build()));
            assertThatThrownBy(() -> guard.requireApproved(quote)).isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).getErrorCode()).isEqualTo("RAMP_APPROVAL_REQUIRED");
        }
        when(routing.getByQuoteId(1L)).thenReturn(List.of(RoutingApprovalDto.builder().status("APPROVED").pricingFingerprint(fingerprint).build()));
        guard.requireApproved(quote);
        quote.setTotalTax(BigDecimal.ONE);
        assertThatThrownBy(() -> guard.requireApproved(quote)).isInstanceOf(RampScheduleException.class);
    }
    private Quote quote() {
        Quote quote = Quote.builder().currency("USD").durationInMonths(36).status(QuoteStatus.DRAFT)
                .tcv(new BigDecimal("77400")).acv(new BigDecimal("25800")).totalTax(BigDecimal.ZERO).build();
        quote.setId(1L); return quote;
    }
    private QuoteLineItem line(Quote quote) {
        QuoteLineItem line = QuoteLineItem.builder().quote(quote).quantity(50).billingFrequency("MONTHLY")
                .listPrice(new BigDecimal("50")).productExternalId("P-1").lineItemType("SUBSCRIPTION").build();
        line.setId(10L); line.setRampSteps(new ArrayList<>());
        String[] rates = {"34", "45", "50"};
        for (int i=0; i<3; i++) line.getRampSteps().add(QuoteRampStep.builder().lineItem(line).sequence(i+1)
                .startMonth(i*12+1).months(12).quantity(50).priceBasis("ABSOLUTE").unitPrice(new BigDecimal(rates[i])).build());
        return line;
    }
}
