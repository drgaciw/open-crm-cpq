package com.opencrm.contract.service;

import com.opencrm.common.event.NonRetryableEventException;
import com.opencrm.common.event.dto.QuoteAcceptedEvent;
import com.opencrm.contract.domain.AppliedQuoteAcceptance;
import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.SubscriptionRatePeriod;
import com.opencrm.contract.domain.SubscriptionStatus;
import com.opencrm.contract.repository.AppliedQuoteAcceptanceRepository;
import com.opencrm.contract.repository.SubscriptionRatePeriodRepository;
import com.opencrm.contract.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AcceptedQuoteSubscriptionServiceTest {
    @Mock SubscriptionRepository subscriptions;
    @Mock SubscriptionRatePeriodRepository rates;
    @Mock AppliedQuoteAcceptanceRepository ledger;
    @Mock JdbcTemplate jdbc;
    AcceptedQuoteSubscriptionService service;

    @BeforeEach void setUp() {
        service = new AcceptedQuoteSubscriptionService(subscriptions, rates, ledger, new AcceptedRampValidator(),
                AcceptedRampValidatorTest.MAPPER, jdbc);
        when(subscriptions.save(any())).thenAnswer(call -> {
            Subscription subscription = call.getArgument(0);
            if (subscription.getId() == null) subscription.setId(5L);
            return subscription;
        });
    }

    @Test void explicitLegacyFlatAcceptedTermsAndPricingArePreserved() throws Exception {
        var event = AcceptedRampValidatorTest.fixture("ramp-new");
        event.getLineItems().getFirst().setRamp(null);
        event.getLineItems().getFirst().setProductType(null);
        service.apply(event);
        ArgumentCaptor<Subscription> captured = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptions).save(captured.capture());
        assertThat(captured.getValue().getStartDate()).isEqualTo(LocalDate.of(2027,1,1));
        assertThat(captured.getValue().getEndDate()).isEqualTo(LocalDate.of(2029,12,31));
        assertThat(captured.getValue().getArr()).isEqualByComparingTo("24000");
        assertThat(captured.getValue().getPlanName()).isEqualTo(event.getLineItems().getFirst().getProductName());
    }

    @Test void legacyScalarPlanUsesAcceptedProductIdentifierWhenOptionalNameAbsent() throws Exception {
        var event = AcceptedRampValidatorTest.fixture("ramp-new");
        event.getLineItems().getFirst().setRamp(null);
        event.getLineItems().getFirst().setProductType(null);
        event.getLineItems().getFirst().setProductName(null);
        service.apply(event);
        ArgumentCaptor<Subscription> captured = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptions).save(captured.capture());
        assertThat(captured.getValue().getPlanName()).isEqualTo("synthetic-product-201");
    }

    @Test void explicitLegacyScalarLineWithoutNameOrIdentifierCannotInventPlan() throws Exception {
        var event = AcceptedRampValidatorTest.fixture("ramp-new");
        var line = event.getLineItems().getFirst();
        line.setRamp(null);
        line.setProductType(null);
        line.setProductName(null);
        line.setProductExternalId(null);
        assertThatThrownBy(() -> service.apply(event)).isInstanceOf(NonRetryableEventException.class);
        verify(subscriptions, never()).save(any());
    }

    @Test void malformedExplicitLegacyAcceptedTermsCannotBecomeInventedOneYearTerm() throws Exception {
        var event = AcceptedRampValidatorTest.fixture("ramp-new");
        event.getLineItems().getFirst().setRamp(null);
        event.getTerms().setEndDate("not-a-date");
        assertThatThrownBy(() -> service.apply(event)).isInstanceOf(NonRetryableEventException.class);
        verify(subscriptions, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"SAAS_USAGE_METERED", "SAAS_USAGE", "SAAS_CREDITS", "ONPREM_PERPETUAL"})
    void knownUnsupportedFlatProductCannotLaunderThroughHistoricalAggregateFallback(String type) throws Exception {
        var event = AcceptedRampValidatorTest.fixture("ramp-new");
        var line = event.getLineItems().getFirst();
        line.setRamp(null);
        line.setLineItemType("SERVICE");
        line.setProductType(type);
        assertThatThrownBy(() -> service.apply(event)).isInstanceOf(NonRetryableEventException.class)
                .hasMessageContaining("unsupported");
        verify(subscriptions, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"SAAS_FLAT_RATE", "ONPREM_SUBSCRIPTION", "SUPPORT_BASIC", "SUPPORT_PREMIUM", "SUPPORT_ENTERPRISE"})
    void sourceDefinedOldFlatProductsKeepAggregateOnlyCompatibility(String type) throws Exception {
        var event = AcceptedRampValidatorTest.fixture("ramp-new");
        var line = event.getLineItems().getFirst();
        line.setRamp(null);
        line.setLineItemType("SERVICE");
        line.setProductType(type);
        service.apply(event);
        ArgumentCaptor<Iterable<SubscriptionRatePeriod>> captured = ArgumentCaptor.forClass(Iterable.class);
        verify(rates).saveAll(captured.capture());
        assertThat(captured.getValue()).singleElement().satisfies(period -> {
            assertThat(period.getProvenance()).isEqualTo("LEGACY_AGGREGATE");
            assertThat(period.getQuantity()).isNull();
            assertThat(period.getNetUnitPrice()).isNull();
            assertThat(period.getNetMonthly()).isEqualByComparingTo("2000");
        });
    }

    @Test void scalarNewCannotTurnExplicitOneTimeBillingIntoSubscription() throws Exception {
        var event = AcceptedRampValidatorTest.fixture("ramp-new");
        var line = event.getLineItems().getFirst();
        line.setRamp(null);
        line.setBillingFrequency("ONE_TIME");
        assertThatThrownBy(() -> service.apply(event)).isInstanceOf(NonRetryableEventException.class)
                .hasMessageContaining("ONE_TIME");
        verify(subscriptions, never()).save(any());
    }

    @Test void sameQuoteNewTransportMetadataCannotReapply() throws Exception {
        var event = AcceptedRampValidatorTest.fixture("ramp-new");
        AtomicReference<AppliedQuoteAcceptance> saved = new AtomicReference<>();
        when(ledger.findByAcceptedQuoteId(any())).thenAnswer(call -> Optional.ofNullable(saved.get()));
        when(ledger.save(any())).thenAnswer(call -> { saved.set(call.getArgument(0)); return saved.get(); });
        service.apply(event);
        event.setAcceptedAt(event.getAcceptedAt().plusSeconds(60));
        event.setCorrelation(QuoteAcceptedEvent.CorrelationSnapshot.builder().correlationId("new-header").build());
        service.apply(event);
        verify(subscriptions, times(1)).save(any());
        verify(rates, times(1)).saveAll(any());
        event.setCustomerId("changed-customer");
        event.getCustomer().setCustomerId("changed-customer");
        assertThatThrownBy(() -> service.apply(event)).isInstanceOf(NonRetryableEventException.class)
                .hasMessageContaining("changed commercial facts");
    }

    @Test void expansionAddsCoTermedLineToExistingIdentityAndQueuesNewRevision() throws Exception {
        var event = expansion();
        Subscription target = Subscription.builder().id(42L).subscriptionNumber("SUB-OLD").customerId(event.getCustomerId())
                .quoteId("old-quote").planName("Named seat").startDate(LocalDate.of(2027,1,1))
                .endDate(LocalDate.of(2029,12,31)).status(SubscriptionStatus.ACTIVE).currencyCode("USD")
                .commercialRevision(3L).mrr(new BigDecimal("2250")).arr(new BigDecimal("27000")).build();
        when(subscriptions.findByNumberForUpdate("SUB-OLD")).thenReturn(Optional.of(target));
        SubscriptionRatePeriod original = SubscriptionRatePeriod.builder().subscriptionId(42L)
                .acceptedLineUuid("11111111-1111-4111-8111-000000000201").sequence(2)
                .startDate(LocalDate.of(2028,1,1)).endDate(LocalDate.of(2028,12,31)).effectiveEndDate(LocalDate.of(2028,12,31))
                .billingAnchorDate(LocalDate.of(2027,1,1)).quantity(new BigDecimal("50")).netUnitPrice(new BigDecimal("45"))
                .netMonthly(new BigDecimal("2250")).annualRunRate(new BigDecimal("27000")).applied(true).build();
        when(rates.findBySubscriptionIdAndDeletedFalseOrderByStartDateAscAcceptedLineUuidAscSequenceAsc(42L)).thenReturn(List.of(original));
        service.apply(event);
        assertThat(target.getSubscriptionNumber()).isEqualTo("SUB-OLD");
        assertThat(target.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(target.getCommercialRevision()).isEqualTo(4L);
        assertThat(target.getErpSyncStatus()).isEqualTo("QUEUED");
        ArgumentCaptor<Iterable<SubscriptionRatePeriod>> saved = ArgumentCaptor.forClass(Iterable.class);
        verify(rates).saveAll(saved.capture());
        assertThat(saved.getValue()).allSatisfy(period -> assertThat(period.getSubscriptionId()).isEqualTo(42L));
        verify(subscriptions, never()).existsBySubscriptionNumber("SUB-" + event.getQuoteId());
    }

    @Test void amendmentReplacesOnlyMappedFutureCoverageAndKeepsHistory() throws Exception {
        var event = expansion();
        event.getDeal().setDealType("AMENDMENT");
        var line = event.getLineItems().getFirst();
        line.setSourceSubscriptionLineUuid("11111111-1111-4111-8111-000000000201");
        line.setQuantity(70);
        line.setExtendedAmount(new BigDecimal("3150"));
        line.getRamp().get(0).setQuantity(70);
        line.getRamp().get(0).setNetMonthly(new BigDecimal("3150"));
        line.getRamp().get(0).setPeriodAmount(new BigDecimal("18900"));
        line.getRamp().get(1).setQuantity(70);
        line.getRamp().get(1).setNetMonthly(new BigDecimal("3500"));
        line.getRamp().get(1).setPeriodAmount(new BigDecimal("42000"));
        totals(event, "60900", "37800", "42000");
        var target = target();
        var old = original(2, "2028-01-01", "2028-12-31", "2250");
        var future = original(3, "2029-01-01", "2029-12-31", "2500");
        var unaffected = original(1, "2027-01-01", "2029-12-31", "100");
        unaffected.setAcceptedLineUuid("11111111-1111-4111-8111-000000000298");
        when(subscriptions.findByNumberForUpdate("SUB-OLD")).thenReturn(Optional.of(target));
        when(rates.findBySubscriptionIdAndDeletedFalseOrderByStartDateAscAcceptedLineUuidAscSequenceAsc(42L))
                .thenReturn(List.of(old, future, unaffected));
        service.apply(event);
        assertThat(old.getEndDate()).isEqualTo(LocalDate.of(2028,12,31));
        assertThat(old.getEffectiveEndDate()).isEqualTo(LocalDate.of(2028,6,30));
        assertThat(old.isRetired()).isFalse();
        assertThat(future.isRetired()).isTrue();
        assertThat(unaffected.getEffectiveEndDate()).isEqualTo(LocalDate.of(2029,12,31));
        assertThat(target.getCommercialRevision()).isEqualTo(4L);
    }

    @Test void typedContinuationCannotUseMissingProductClassification() throws Exception {
        var event = expansion();
        event.getLineItems().getFirst().setProductType(null);
        when(subscriptions.findByNumberForUpdate("SUB-OLD")).thenReturn(Optional.of(target()));
        when(rates.findBySubscriptionIdAndDeletedFalseOrderByStartDateAscAcceptedLineUuidAscSequenceAsc(42L))
                .thenReturn(List.of(original(2, "2028-01-01", "2029-12-31", "2250")));
        assertThatThrownBy(() -> service.apply(event)).isInstanceOf(NonRetryableEventException.class)
                .hasMessageContaining("classification");
    }

    @Test void expansionSourceMustCoverActualEffectiveDate() throws Exception {
        var event = expansion();
        event.getLineItems().getFirst().setSourceSubscriptionLineUuid("11111111-1111-4111-8111-000000000201");
        when(subscriptions.findByNumberForUpdate("SUB-OLD")).thenReturn(Optional.of(target()));
        when(rates.findBySubscriptionIdAndDeletedFalseOrderByStartDateAscAcceptedLineUuidAscSequenceAsc(42L))
                .thenReturn(List.of(original(3, "2029-01-01", "2029-12-31", "2500")));
        assertThatThrownBy(() -> service.apply(event)).isInstanceOf(NonRetryableEventException.class)
                .hasMessageContaining("effective coverage");
    }

    @Test void legacyAggregateWithoutKnownUnitFactsCannotBeReusedForTypedExpansion() throws Exception {
        var event = expansion();
        var legacy = original(1, "2027-01-01", "2029-12-31", "2250");
        legacy.setProvenance("LEGACY_AGGREGATE");
        legacy.setQuantity(null);
        legacy.setNetUnitPrice(null);
        when(subscriptions.findByNumberForUpdate("SUB-OLD")).thenReturn(Optional.of(target()));
        when(rates.findBySubscriptionIdAndDeletedFalseOrderByStartDateAscAcceptedLineUuidAscSequenceAsc(42L)).thenReturn(List.of(legacy));
        assertThatThrownBy(() -> service.apply(event)).isInstanceOf(NonRetryableEventException.class)
                .hasMessageContaining("known accepted unit and quantity");
        verify(rates, never()).saveAll(any());
    }

    @Test void partialCurrentRateCanDifferFromNextFullPeriodRate() throws Exception {
        var event = partial(false);
        var line = event.getLineItems().getFirst();
        line.getRamp().getFirst().setNetUnitPrice(new BigDecimal("50"));
        line.getRamp().getFirst().setNetMonthly(new BigDecimal("1000"));
        line.getRamp().getFirst().setPeriodAmount(new BigDecimal("2000"));
        totals(event, "2434.48", "10800", "12000");
        var source = original(1, "2028-01-31", "2028-04-29", "2000");
        source.setBillingAnchorDate(LocalDate.of(2028,1,31));
        when(subscriptions.findByNumberForUpdate("SUB-OLD")).thenReturn(Optional.of(partialTarget()));
        when(rates.findBySubscriptionIdAndDeletedFalseOrderByStartDateAscAcceptedLineUuidAscSequenceAsc(42L)).thenReturn(List.of(source));
        service.apply(event);
        ArgumentCaptor<Iterable<SubscriptionRatePeriod>> captured = ArgumentCaptor.forClass(Iterable.class);
        verify(rates).saveAll(captured.capture());
        assertThat(captured.getValue()).extracting(SubscriptionRatePeriod::getNetMonthly)
                .containsExactly(new BigDecimal("900"), new BigDecimal("1000"));
    }

    @Test void mutationOfRenewedLineUsesRenewalAnchorInsteadOfOriginalSubscriptionAnchor() throws Exception {
        var event = partial(false);
        event.getSubscriptionChange().setEffectiveDate("2028-06-15");
        event.getTerms().setStartDate("2028-06-30");
        event.getTerms().setEndDate("2030-04-29");
        event.getTerms().setTermMonths(22);
        var line = event.getLineItems().getFirst();
        line.setSourceSubscriptionLineUuid("11111111-1111-4111-8111-000000000201");
        var partial = line.getInitialProration();
        partial.setStartDate("2028-06-15");
        partial.setEndDate("2028-06-29");
        partial.setBillingPeriodStart("2028-05-30");
        partial.setBillingPeriodEnd("2028-06-29");
        partial.setPeriodAmount(new BigDecimal("435.48"));
        line.getRamp().getFirst().setMonths(22);
        line.getRamp().getFirst().setPeriodAmount(new BigDecimal("19800"));
        totals(event, "20235.48", "10800", "10800");
        var target = target();
        target.setStartDate(LocalDate.of(2028,1,31));
        target.setEndDate(LocalDate.of(2030,4,29));
        var source = original(1, "2028-04-30", "2030-04-29", "2000");
        source.setBillingAnchorDate(LocalDate.of(2028,4,30));
        when(subscriptions.findByNumberForUpdate("SUB-OLD")).thenReturn(Optional.of(target));
        when(rates.findBySubscriptionIdAndDeletedFalseOrderByStartDateAscAcceptedLineUuidAscSequenceAsc(42L)).thenReturn(List.of(source));
        service.apply(event);
        ArgumentCaptor<Iterable<SubscriptionRatePeriod>> captured = ArgumentCaptor.forClass(Iterable.class);
        verify(rates).saveAll(captured.capture());
        assertThat(captured.getValue()).allSatisfy(period -> assertThat(period.getBillingAnchorDate()).isEqualTo(LocalDate.of(2028,4,30)));
    }

    @Test void renewalAppendsAcceptedExitUpliftToSameSubscriptionWithoutReextendingReplay() throws Exception {
        var event = expansion();
        event.getDeal().setDealType("RENEWAL");
        event.getTerms().setStartDate("2030-01-01");
        event.getTerms().setEndDate("2030-12-31");
        event.getTerms().setTermMonths(12);
        var line = event.getLineItems().getFirst();
        line.setSourceSubscriptionLineUuid("11111111-1111-4111-8111-000000000201");
        line.setQuantity(50);
        line.setExtendedAmount(new BigDecimal("2700"));
        line.setRamp(List.of(QuoteAcceptedEvent.RampPeriodSnapshot.builder().sequence(1).startMonth(1).months(12)
                .quantity(50).netUnitPrice(new BigDecimal("54")).netMonthly(new BigDecimal("2700"))
                .periodAmount(new BigDecimal("32400")).build()));
        totals(event, "32400", "32400", "32400");
        var target = target();
        var oldExit = original(3, "2029-01-01", "2029-12-31", "2500");
        when(subscriptions.findByNumberForUpdate("SUB-OLD")).thenReturn(Optional.of(target));
        when(rates.findBySubscriptionIdAndDeletedFalseOrderByStartDateAscAcceptedLineUuidAscSequenceAsc(42L)).thenReturn(List.of(oldExit));
        AtomicReference<AppliedQuoteAcceptance> saved = new AtomicReference<>();
        when(ledger.findByAcceptedQuoteId(any())).thenAnswer(call -> Optional.ofNullable(saved.get()));
        when(ledger.save(any())).thenAnswer(call -> { saved.set(call.getArgument(0)); return saved.get(); });
        service.apply(event);
        service.apply(event);
        assertThat(target.getEndDate()).isEqualTo(LocalDate.of(2030,12,31));
        assertThat(target.getRenewalDate()).isEqualTo(LocalDate.of(2030,12,1));
        assertThat(target.getCommercialRevision()).isEqualTo(4L);
        assertThat(oldExit.getEffectiveEndDate()).isEqualTo(LocalDate.of(2029,12,31));
        verify(subscriptions, times(1)).save(target);
    }

    @Test void leapJanuaryAnchorPartialAndFollowingWholeMonthsStaySeparate() throws Exception {
        var event = partial(false);
        var target = partialTarget();
        var original = original(1, "2028-01-31", "2028-04-29", "2000");
        original.setBillingAnchorDate(LocalDate.of(2028,1,31));
        when(subscriptions.findByNumberForUpdate("SUB-OLD")).thenReturn(Optional.of(target));
        when(rates.findBySubscriptionIdAndDeletedFalseOrderByStartDateAscAcceptedLineUuidAscSequenceAsc(42L)).thenReturn(List.of(original));
        service.apply(event);
        ArgumentCaptor<Iterable<SubscriptionRatePeriod>> saved = ArgumentCaptor.forClass(Iterable.class);
        verify(rates).saveAll(saved.capture());
        var stored = java.util.stream.StreamSupport.stream(saved.getValue().spliterator(), false).toList();
        assertThat(stored).hasSize(2);
        assertThat(stored.get(0).getSequence()).isZero();
        assertThat(stored.get(0).getStartDate()).isEqualTo(LocalDate.of(2028,2,15));
        assertThat(stored.get(0).getEndDate()).isEqualTo(LocalDate.of(2028,2,28));
        assertThat(stored.get(0).getPeriodAmount()).isEqualByComparingTo("434.48");
        assertThat(stored.get(1).getStartDate()).isEqualTo(LocalDate.of(2028,2,29));
        assertThat(stored.get(1).getEndDate()).isEqualTo(LocalDate.of(2028,4,29));
        assertThat(stored.get(1).getBillingAnchorDate()).isEqualTo(LocalDate.of(2028,1,31));
    }

    @Test void finalPartialOnlyHasOneExplicitSequenceZeroAndNoInventedFullPeriod() throws Exception {
        var event = partial(true);
        var target = partialTarget();
        var original = original(1, "2028-01-31", "2028-04-29", "2000");
        original.setBillingAnchorDate(LocalDate.of(2028,1,31));
        when(subscriptions.findByNumberForUpdate("SUB-OLD")).thenReturn(Optional.of(target));
        when(rates.findBySubscriptionIdAndDeletedFalseOrderByStartDateAscAcceptedLineUuidAscSequenceAsc(42L)).thenReturn(List.of(original));
        service.apply(event);
        ArgumentCaptor<Iterable<SubscriptionRatePeriod>> saved = ArgumentCaptor.forClass(Iterable.class);
        verify(rates).saveAll(saved.capture());
        assertThat(saved.getValue()).singleElement().satisfies(period -> {
            assertThat(period.getSequence()).isZero();
            assertThat(period.getMonths()).isNull();
            assertThat(period.getPeriodAmount()).isEqualByComparingTo("450");
            assertThat(period.getEndDate()).isEqualTo(LocalDate.of(2028,4,29));
        });
    }

    private Subscription partialTarget() {
        var target = target();
        target.setStartDate(LocalDate.of(2028,1,31));
        target.setEndDate(LocalDate.of(2028,4,29));
        return target;
    }
    private static QuoteAcceptedEvent partial(boolean finalOnly) throws Exception {
        var event = expansion();
        event.getSubscriptionChange().setEffectiveDate(finalOnly ? "2028-04-15" : "2028-02-15");
        event.getTerms().setStartDate(finalOnly ? "2028-04-15" : "2028-02-29");
        event.getTerms().setEndDate("2028-04-29");
        event.getTerms().setTermMonths(finalOnly ? 1 : 2);
        var line = event.getLineItems().getFirst();
        line.setSourceSubscriptionLineUuid("11111111-1111-4111-8111-000000000201");
        line.setInitialProration(QuoteAcceptedEvent.ProrationSnapshot.builder()
                .startDate(finalOnly ? "2028-04-15" : "2028-02-15").endDate(finalOnly ? "2028-04-29" : "2028-02-28")
                .billingPeriodStart(finalOnly ? "2028-03-31" : "2028-01-31").billingPeriodEnd(finalOnly ? "2028-04-29" : "2028-02-28")
                .quantity(20).netUnitPrice(new BigDecimal("45")).netMonthly(new BigDecimal("900"))
                .periodAmount(new BigDecimal(finalOnly ? "450" : "434.48")).build());
        line.setRamp(finalOnly ? null : List.of(QuoteAcceptedEvent.RampPeriodSnapshot.builder().sequence(1).startMonth(1)
                .months(2).quantity(20).netUnitPrice(new BigDecimal("45")).netMonthly(new BigDecimal("900"))
                .periodAmount(new BigDecimal("1800")).build()));
        totals(event, finalOnly ? "450" : "2234.48", "10800", "10800");
        return event;
    }

    private static void totals(QuoteAcceptedEvent event, String tcv, String arr, String exitArr) {
        event.setTotalAmount(event.getLineItems().stream().map(QuoteAcceptedEvent.LineItemSnapshot::getExtendedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        event.getPricing().setTcv(new BigDecimal(tcv));
        event.getPricing().setAcv(new BigDecimal(tcv).multiply(BigDecimal.valueOf(12))
                .divide(BigDecimal.valueOf(event.getTerms().getTermMonths()), 2, java.math.RoundingMode.HALF_UP));
        event.getPricing().setTotalAmount(event.getTotalAmount());
        event.getPricing().setArr(new BigDecimal(arr));
        event.getPricing().setExitArr(new BigDecimal(exitArr));
    }
    private Subscription target() {
        return Subscription.builder().id(42L).subscriptionNumber("SUB-OLD").customerId("synthetic-r3-customer")
                .quoteId("old-quote").planName("Seat").startDate(LocalDate.of(2027,1,1)).endDate(LocalDate.of(2029,12,31))
                .currencyCode("USD").commercialRevision(3L).status(SubscriptionStatus.ACTIVE)
                .mrr(new BigDecimal("2250")).arr(new BigDecimal("27000")).build();
    }
    private SubscriptionRatePeriod original(int sequence, String start, String end, String monthly) {
        return SubscriptionRatePeriod.builder().subscriptionId(42L).acceptedLineUuid("11111111-1111-4111-8111-000000000201")
                .sequence(sequence).startDate(LocalDate.parse(start)).endDate(LocalDate.parse(end)).effectiveEndDate(LocalDate.parse(end))
                .billingAnchorDate(LocalDate.of(2027,1,1)).quantity(new BigDecimal("50"))
                .netUnitPrice(new BigDecimal(monthly).divide(new BigDecimal("50"))).netMonthly(new BigDecimal(monthly))
                .annualRunRate(new BigDecimal(monthly).multiply(BigDecimal.valueOf(12))).provenance("ACCEPTED_PRICED").applied(true).build();
    }

    static QuoteAcceptedEvent expansion() throws Exception {
        var event = AcceptedRampValidatorTest.fixture("ramp-new");
        event.setQuoteId("11111111-1111-4111-8111-000000000107");
        event.getQuote().setQuoteId(event.getQuoteId());
        event.getDeal().setDealType("EXPANSION");
        event.setSubscriptionChange(QuoteAcceptedEvent.SubscriptionChangeSnapshot.builder()
                .targetSubscriptionNumber("SUB-OLD").observedRevision(3L).build());
        event.getTerms().setStartDate("2028-07-01");
        event.getTerms().setTermMonths(18);
        var line = event.getLineItems().getFirst();
        line.setLineUuid("11111111-1111-4111-8111-000000000207");
        line.setLineItemType("SAAS_SEAT_NAMED");
        line.setQuantity(20);
        line.setExtendedAmount(new BigDecimal("900"));
        line.setRamp(List.of(QuoteAcceptedEvent.RampPeriodSnapshot.builder().sequence(1).startMonth(1).months(6)
                        .quantity(20).netUnitPrice(new BigDecimal("45")).netMonthly(new BigDecimal("900")).periodAmount(new BigDecimal("5400")).build(),
                QuoteAcceptedEvent.RampPeriodSnapshot.builder().sequence(2).startMonth(7).months(12)
                        .quantity(20).netUnitPrice(new BigDecimal("50")).netMonthly(new BigDecimal("1000")).periodAmount(new BigDecimal("12000")).build()));
        event.setTotalAmount(new BigDecimal("900"));
        event.getPricing().setTotalAmount(event.getTotalAmount());
        event.getPricing().setTcv(new BigDecimal("17400"));
        event.getPricing().setAcv(new BigDecimal("11600"));
        event.getPricing().setArr(new BigDecimal("10800"));
        event.getPricing().setExitArr(new BigDecimal("12000"));
        return event;
    }
}
