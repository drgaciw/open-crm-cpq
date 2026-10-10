package com.opencrm.quote.quote;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.quote.client.SubscriptionScheduleView;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@Tag("unit")
class QuoteContinuationPolicyTest {
    @Test void month19ExpansionUsesRemainingSixThenTwelveMonths() {
        var plan = QuoteContinuationPolicy.plan(request(QuoteContinuationRequest.Operation.EXPANSION, LocalDate.of(2028,7,1),20), schedule());
        assertThat(plan.termMonths()).isEqualTo(18);
        assertThat(plan.periods()).extracting(QuoteContinuationPolicy.Period::months).containsExactly(6,12);
        assertThat(plan.periods()).extracting(QuoteContinuationPolicy.Period::unitPrice).containsExactly(new BigDecimal("45"),new BigDecimal("50"));
        assertThat(plan.tcv()).isEqualByComparingTo("17400");
    }
    @Test void amendmentQuantityIsReplacementTotalRatherThanDelta() {
        assertThat(QuoteContinuationPolicy.plan(request(QuoteContinuationRequest.Operation.AMENDMENT,LocalDate.of(2028,7,1),70),schedule()).tcv()).isEqualByComparingTo("60900");
    }
    @Test void renewalUsesExitRateAndEightPercentUplift() {
        var request=request(QuoteContinuationRequest.Operation.RENEWAL,LocalDate.of(2030,1,1),50);
        request.setUpliftPercent(new BigDecimal("8")); request.setTermMonths(12);
        var plan=QuoteContinuationPolicy.plan(request,schedule());
        assertThat(plan.periods().getFirst().unitPrice()).isEqualByComparingTo("54");
        assertThat(plan.tcv()).isEqualByComparingTo("32400");
    }
    @Test void extensionKeepsExitRate() {
        var request=request(QuoteContinuationRequest.Operation.EXTENSION,LocalDate.of(2030,1,1),50); request.setTermMonths(12);
        assertThat(QuoteContinuationPolicy.plan(request,schedule()).periods().getFirst().unitPrice()).isEqualByComparingTo("50");
    }
    @Test void missingLegacyUnitRateIsActionable() {
        var target=schedule();target.getLines().getFirst().getPeriods().getFirst().setNetUnitPrice(null);
        assertThatThrownBy(() -> QuoteContinuationPolicy.plan(request(QuoteContinuationRequest.Operation.EXPANSION,LocalDate.of(2027,1,1),20),target))
                .isInstanceOf(BusinessException.class).hasMessageContaining("unit rate");
    }
    @Test void midMonthUsesInclusiveActualDaysAndKeepsFollowingMonths() {
        var plan=QuoteContinuationPolicy.plan(request(QuoteContinuationRequest.Operation.EXPANSION,LocalDate.of(2028,7,15),20),schedule());
        assertThat(plan.initialProration().getPeriodAmount()).isEqualByComparingTo("493.55");
        assertThat(plan.initialProration().getEndDate()).isEqualTo("2028-07-31");
        assertThat(plan.startDate()).isEqualTo(LocalDate.of(2028,8,1));
        assertThat(plan.termMonths()).isEqualTo(17);
        assertThat(plan.tcv()).isEqualByComparingTo("16993.55");
    }
    @Test void finalPartialHasNominalTermOneAndNoZeroMonthStep() {
        var plan=QuoteContinuationPolicy.plan(request(QuoteContinuationRequest.Operation.EXPANSION,LocalDate.of(2029,12,15),20),schedule());
        assertThat(plan.periods()).isEmpty();assertThat(plan.termMonths()).isEqualTo(1);
        assertThat(plan.startDate()).isEqualTo(LocalDate.of(2029,12,15));
        assertThat(plan.endDate()).isEqualTo(LocalDate.of(2029,12,31));
        assertThat(plan.tcv()).isEqualByComparingTo("548.39");
    }
    @Test void jan31AnchorSurvivesLeapFebruaryClipping() {
        var target=schedule();target.setStartDate(LocalDate.of(2028,1,31));target.setEndDate(LocalDate.of(2028,4,29));
        target.getLines().getFirst().setPeriods(List.of(SubscriptionScheduleView.Period.builder().sequence(1).startMonth(1).months(3).quantity(50)
                .startDate(target.getStartDate()).endDate(target.getEndDate()).netUnitPrice(new BigDecimal("40")).netMonthly(new BigDecimal("2000")).periodAmount(new BigDecimal("6000")).build()));
        var plan=QuoteContinuationPolicy.plan(request(QuoteContinuationRequest.Operation.EXPANSION,LocalDate.of(2028,2,29),20),target);
        assertThat(plan.startDate()).isEqualTo(LocalDate.of(2028,2,29));assertThat(plan.endDate()).isEqualTo(LocalDate.of(2028,4,29));
        assertThat(plan.initialProration()).isNull();assertThat(plan.termMonths()).isEqualTo(2);
        var partial=QuoteContinuationPolicy.plan(request(QuoteContinuationRequest.Operation.EXPANSION,LocalDate.of(2028,3,15),20),target);
        assertThat(partial.initialProration().getBillingPeriodStart()).isEqualTo("2028-02-29");
        assertThat(partial.initialProration().getBillingPeriodEnd()).isEqualTo("2028-03-30");
        assertThat(partial.initialProration().getPeriodAmount()).isEqualByComparingTo("412.90");
        assertThat(partial.startDate()).isEqualTo(LocalDate.of(2028,3,31));
    }
    @Test void rejectsForeignCurrencyStaleRevisionAndOutOfTermDates() {
        var request=request(QuoteContinuationRequest.Operation.EXPANSION,LocalDate.of(2028,7,1),20);
        request.setCurrency("EUR"); assertThatThrownBy(() -> QuoteContinuationPolicy.plan(request,schedule())).isInstanceOf(BusinessException.class);
        request.setCurrency("USD");request.setObservedRevision(6L);assertThatThrownBy(() -> QuoteContinuationPolicy.plan(request,schedule())).isInstanceOf(BusinessException.class);
        request.setObservedRevision(7L);request.setEffectiveDate(LocalDate.of(2026,12,31));assertThatThrownBy(() -> QuoteContinuationPolicy.plan(request,schedule())).isInstanceOf(BusinessException.class);
        request.setEffectiveDate(LocalDate.of(2030,1,1));assertThatThrownBy(() -> QuoteContinuationPolicy.plan(request,schedule())).isInstanceOf(BusinessException.class);
    }
    @Test void chainedClippedRenewalUsesItsPersistedLineBillingAnchor() {
        var target=schedule();target.setStartDate(LocalDate.of(2028,1,31));target.setEndDate(LocalDate.of(2029,4,29));
        var line=target.getLines().getFirst();line.setBillingAnchorDate(LocalDate.of(2028,4,30));
        line.setPeriods(List.of(SubscriptionScheduleView.Period.builder().sequence(1).startMonth(1).months(12).quantity(50)
                .startDate(LocalDate.of(2028,4,30)).endDate(target.getEndDate()).netUnitPrice(new BigDecimal("50"))
                .netMonthly(new BigDecimal("2500")).periodAmount(new BigDecimal("30000")).build()));
        var plan=QuoteContinuationPolicy.plan(request(QuoteContinuationRequest.Operation.EXPANSION,LocalDate.of(2028,6,15),20),target);
        assertThat(plan.initialProration().getBillingPeriodStart()).isEqualTo("2028-05-30");
        assertThat(plan.initialProration().getBillingPeriodEnd()).isEqualTo("2028-06-29");
        assertThat(plan.initialProration().getPeriodAmount()).isEqualByComparingTo("483.87");
        assertThat(plan.startDate()).isEqualTo(LocalDate.of(2028,6,30));
        assertThat(plan.endDate()).isEqualTo(LocalDate.of(2029,4,29));
    }
    private QuoteContinuationRequest request(QuoteContinuationRequest.Operation operation,LocalDate effective,int quantity) {
        return QuoteContinuationRequest.builder().operation(operation).targetSubscriptionNumber("SUB-R3").sourceSubscriptionLineUuid("source-line")
                .observedRevision(7L).customerExternalId("synthetic-r3-customer").currency("USD").effectiveDate(effective).quantity(quantity).build();
    }
    private SubscriptionScheduleView schedule() {
        var line=SubscriptionScheduleView.Line.builder().acceptedLineUuid("source-line").productExternalId("product-r3").provenance("ACCEPTED_PRICED")
                .productType("SAAS_SEAT_NAMED").lineItemType("SAAS_SEAT_NAMED").billingFrequency("MONTHLY").periods(new java.util.ArrayList<>()).build();
        for(int i=0;i<3;i++) line.getPeriods().add(SubscriptionScheduleView.Period.builder().sequence(i+1).startMonth(i*12+1).months(12).quantity(50)
                .startDate(LocalDate.of(2027+i,1,1)).endDate(LocalDate.of(2027+i,12,31)).netUnitPrice(BigDecimal.valueOf(40+i*5))
                .netMonthly(BigDecimal.valueOf(2000+i*250)).periodAmount(BigDecimal.valueOf(24000+i*3000)).build());
        return SubscriptionScheduleView.builder().subscriptionNumber("SUB-R3").customerId("synthetic-r3-customer").currencyCode("USD").status("ACTIVE")
                .startDate(LocalDate.of(2027,1,1)).endDate(LocalDate.of(2029,12,31)).commercialRevision(7L).lines(List.of(line)).build();
    }
}
