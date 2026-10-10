package com.opencrm.quote.quote;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.quote.lineitem.QuoteLineItem;
import com.opencrm.quote.ramp.QuoteRampStep;
import com.opencrm.quote.ramp.RampPricingSnapshot;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class QuoteAcceptedRampSnapshotTest {
    @Test
    void emitsPersistedPeriodsAndExitArrRatherThanAuthoredRates() {
        Quote quote = quote();
        QuoteLineItem line = line(quote);
        line.getRampSteps().getFirst().setUnitPrice(new BigDecimal("123"));

        var event = QuoteAcceptedKafkaEventFactory.from(quote, List.of(line));

        assertThat(event.getEventVersion()).isEqualTo(1);
        assertThat(event.getTerms().getTermMonths()).isEqualTo(36);
        assertThat(event.getPricing().getExitArr()).isEqualByComparingTo("30000");
        assertThat(event.getPricing().getTcv()).isEqualByComparingTo("81000");
        assertThat(event.getLineItems().getFirst().getRamp()).hasSize(3);
        assertThat(event.getLineItems().getFirst().getRamp().getFirst().getNetUnitPrice())
                .isEqualByComparingTo("40");
        assertThat(event.getLineItems().getFirst().getRamp().getLast().getPeriodAmount())
                .isEqualByComparingTo("30000");
        line.setQuote(null);
        assertThat(QuoteAcceptedKafkaEventFactory.from(quote,List.of(line)).getLineItems().getFirst().getRamp())
                .usingRecursiveComparison().isEqualTo(event.getLineItems().getFirst().getRamp());
        assertThat(line.getQuote()).isNull();
    }

    @Test
    void scheduledAcceptanceRefusesMissingPersistedPrices() {
        Quote quote = quote();
        QuoteLineItem line = line(quote);
        line.setPricingWaterfallJson(null);
        assertThatThrownBy(() -> QuoteAcceptedKafkaEventFactory.from(quote, List.of(line)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void scheduledAcceptanceRefusesCorruptPeriodAmounts() {
        Quote quote = quote();
        QuoteLineItem line = line(quote);
        line.setPricingWaterfallJson(line.getPricingWaterfallJson().replace("24000", "-24000"));
        assertThatThrownBy(() -> QuoteAcceptedKafkaEventFactory.from(quote, List.of(line)))
                .isInstanceOf(BusinessException.class);
    }

    @Test void refusesFractionalCentStoredMonthlyRatesInsteadOfRoundingThemIntoValidity() {
        Quote quote=quote();QuoteLineItem line=line(quote);
        line.setPricingWaterfallJson(line.getPricingWaterfallJson().replace("\"netMonthly\":2000", "\"netMonthly\":2000.004"));
        assertThatThrownBy(() -> QuoteAcceptedKafkaEventFactory.from(quote,List.of(line))).isInstanceOf(BusinessException.class);
    }

    @Test
    void scheduledAcceptanceRefusesStalePrices() {
        Quote quote = quote();
        quote.setRampScheduleStale(true);
        QuoteLineItem line = line(quote);
        assertThatThrownBy(() -> QuoteAcceptedKafkaEventFactory.from(quote, List.of(line)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void fullPricedPeriodFactsParticipateInApprovalFingerprint() {
        Quote quote = quote();
        QuoteLineItem line = line(quote);
        String initial = RampPricingSnapshot.fingerprint(quote, List.of(line));
        line.setPricingWaterfallJson(line.getPricingWaterfallJson().replace("\"netUnitPrice\":45", "\"netUnitPrice\":46"));
        assertThat(RampPricingSnapshot.fingerprint(quote, List.of(line))).isNotEqualTo(initial);
    }

    @Test
    void historicalIncompleteFlatLineDoesNotInventAUnitRate() {
        Quote quote = quote();
        QuoteLineItem incomplete = QuoteLineItem.builder().quote(quote).quantity(1)
                .lineItemType("SUBSCRIPTION").productExternalId("legacy-product").build();
        assertThat(QuoteAcceptedKafkaEventFactory.from(quote, List.of(incomplete))
                .getLineItems().getFirst().getRamp()).isNull();
    }

    @Test void canonicalObjectOrderAndNumericScaleDoNotChangeBinding() {
        Quote quote=quote();QuoteLineItem line=line(quote);
        String fingerprint=RampPricingSnapshot.fingerprint(quote,List.of(line));
        line.setPricingWaterfallJson(line.getPricingWaterfallJson().replace("\"sequence\":1,\"startMonth\":1", "\"startMonth\":1,\"sequence\":1")
                .replace("\"netUnitPrice\":45", "\"netUnitPrice\":45.0000"));
        assertThat(RampPricingSnapshot.fingerprint(quote,List.of(line))).isEqualTo(fingerprint);
    }

    @Test void bindsTargetRevisionSourceAndTypedMigrationFacts() {
        Quote quote=quote();QuoteLineItem line=line(quote);
        String fingerprint=RampPricingSnapshot.fingerprint(quote,List.of(line));
        quote.setTargetSubscriptionNumber("SUB-R3");assertThat(RampPricingSnapshot.fingerprint(quote,List.of(line))).isNotEqualTo(fingerprint);
        fingerprint=RampPricingSnapshot.fingerprint(quote,List.of(line));quote.setObservedRevision(7L);
        assertThat(RampPricingSnapshot.fingerprint(quote,List.of(line))).isNotEqualTo(fingerprint);
        fingerprint=RampPricingSnapshot.fingerprint(quote,List.of(line));line.setSourceSubscriptionLineUuid("source-r3");
        assertThat(RampPricingSnapshot.fingerprint(quote,List.of(line))).isNotEqualTo(fingerprint);
        fingerprint=RampPricingSnapshot.fingerprint(quote,List.of(line));line.setHybridMigrationJson("{\"pairId\":\"pair-r4\",\"role\":\"DECLINING\",\"startMonth\":1,\"months\":12,\"bookValue\":10000}");
        assertThat(RampPricingSnapshot.fingerprint(quote,List.of(line))).isNotEqualTo(fingerprint);
        fingerprint=RampPricingSnapshot.fingerprint(quote,List.of(line));line.setInitialProrationJson("{\"periodAmount\":1}");
        assertThat(RampPricingSnapshot.fingerprint(quote,List.of(line))).isNotEqualTo(fingerprint);
    }

    @Test void acceptedImmutableStoredViewRemainsReadableWithOlderBinding() {
        Quote quote=quote();quote.setStatus(QuoteStatus.ACCEPTED);
        quote.setPricingWaterfallJson("{\"pricingFingerprint\":\"older-binding\"}");QuoteLineItem line=line(quote);
        assertThat(RampPricingSnapshot.pricedPeriods(line)).hasSize(3);
        assertThat(RampPricingSnapshot.pricedPeriods(line).getFirst().getNetUnitPrice()).isEqualByComparingTo("40");
    }

    @Test void oldIncompleteBindingCannotAuthorizeCurrentPendingOrSentDecision() {
        Quote quote=quote();QuoteLineItem line=line(quote);
        quote.setPricingWaterfallJson("{\"pricingFingerprint\":\"older-binding\"}");
        var repository=org.mockito.Mockito.mock(com.opencrm.quote.lineitem.QuoteLineItemRepository.class);
        org.mockito.Mockito.when(repository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(1L)).thenReturn(List.of(line));
        var routing=org.mockito.Mockito.mock(com.opencrm.quote.client.ApprovalRoutingClient.class);
        var guard=new com.opencrm.quote.ramp.RampScheduleGuard(repository,
                org.mockito.Mockito.mock(com.opencrm.quote.ramp.QuoteRampStepRepository.class),
                org.mockito.Mockito.mock(com.opencrm.quote.ramp.RampScheduleValidator.class),new com.opencrm.quote.ramp.RampPricingProperties(),
                routing,org.mockito.Mockito.mock(com.opencrm.common.event.OutboxEventPublisher.class));
        for(var status:List.of(QuoteStatus.PENDING_APPROVAL,QuoteStatus.SENT)) {
            quote.setStatus(status);
            assertThatThrownBy(() -> guard.requireApproved(quote)).isInstanceOf(com.opencrm.quote.ramp.RampScheduleException.class);
        }
        org.mockito.Mockito.verifyNoInteractions(routing);
    }

    @Test void trustedGenericRecurringFlatUsesOneTypedPeriod() {
        Quote quote=quote();QuoteLineItem line=line(quote);line.getRampSteps().clear();line.setLineItemType("PRODUCT");
        var snapshot=QuoteAcceptedKafkaEventFactory.from(quote,List.of(line)).getLineItems().getFirst();
        assertThat(snapshot.getExtendedAmount()).isEqualByComparingTo("2000");
        assertThat(snapshot.getRamp()).hasSize(1);assertThat(snapshot.getRamp().getFirst().getPeriodAmount()).isEqualByComparingTo("72000");
    }

    @Test void nullProductTypeFlatNewPreservesScalarLegacyWithoutInventingReusableRates() {
        Quote quote=quote();QuoteLineItem line=line(quote);line.getRampSteps().clear();line.setLineItemType("PRODUCT");line.setProductType(null);
        var event=QuoteAcceptedKafkaEventFactory.from(quote,List.of(line));
        assertThat(event.getLineItems().getFirst().getRamp()).isNull();
        assertThat(event.getLineItems().getFirst().getExtendedAmount()).isEqualByComparingTo("2000");
        assertThat(event.getPricing().getTcv()).isEqualByComparingTo("81000");
        assertThat(event.getTerms().getTermMonths()).isEqualTo(36);
        assertThat(event.getTerms().getStartDate()).isEqualTo("2027-01-01");
    }

    @Test void typedMutationCannotFallbackToLegacyAggregateOnlyRate() {
        Quote quote=quote();quote.setDealType(DealType.EXPANSION);quote.setTargetSubscriptionNumber("SUB-R3");
        quote.setObservedRevision(7L);quote.setSubscriptionEffectiveDate(quote.getStartDate());
        QuoteLineItem incomplete=QuoteLineItem.builder().quote(quote).quantity(20).lineItemType("SAAS_SEAT_NAMED")
                .billingFrequency("MONTHLY").productExternalId("legacy-product").build();
        assertThatThrownBy(() -> QuoteAcceptedKafkaEventFactory.from(quote,List.of(incomplete))).isInstanceOf(BusinessException.class);
    }
    @Test void genericServiceLabelCannotTurnUsageOrCreditsIntoAFixedRecurringSchedule() {
        for(String type:List.of("SAAS_USAGE","SAAS_CREDITS")) {
            Quote quote=quote();QuoteLineItem line=line(quote);line.setLineItemType("SERVICE");line.setProductType(type);
            assertThatThrownBy(() -> QuoteAcceptedKafkaEventFactory.from(quote,List.of(line))).isInstanceOf(BusinessException.class);
        }
    }
    @Test void scheduledReusableRatesRequireAuthoritativePricingProductProof() {
        Quote quote=quote();QuoteLineItem line=line(quote);
        line.setPricingWaterfallJson(line.getPricingWaterfallJson().replace("\"authoritativeProductType\":\"SAAS_SEAT_NAMED\",", ""));
        assertThatThrownBy(() -> QuoteAcceptedKafkaEventFactory.from(quote,List.of(line))).isInstanceOf(BusinessException.class);
    }
    @Test void callerFixedLabelCannotOverrideAuthoritativeUsageClassification() {
        Quote quote=quote();QuoteLineItem line=line(quote);
        line.setPricingWaterfallJson(line.getPricingWaterfallJson().replace("\"authoritativeProductType\":\"SAAS_SEAT_NAMED\"", "\"authoritativeProductType\":\"SAAS_USAGE_METERED\""));
        assertThatThrownBy(() -> QuoteAcceptedKafkaEventFactory.from(quote,List.of(line))).isInstanceOf(BusinessException.class);
    }
    @Test void differentAuthoritativeFixedTypeCannotCertifyCallerProductType() {
        Quote quote=quote();QuoteLineItem line=line(quote);
        line.setPricingWaterfallJson(line.getPricingWaterfallJson().replace("\"authoritativeProductType\":\"SAAS_SEAT_NAMED\"", "\"authoritativeProductType\":\"ONPREM_SUPPORT\""));
        assertThatThrownBy(() -> QuoteAcceptedKafkaEventFactory.from(quote,List.of(line))).isInstanceOf(BusinessException.class);
    }
    @Test void flatNewWithoutCatalogProofRetainsScalarsButOmitsReusableRates() {
        Quote quote=quote();QuoteLineItem line=line(quote);line.getRampSteps().clear();
        line.setPricingWaterfallJson(null);
        var event=QuoteAcceptedKafkaEventFactory.from(quote,List.of(line));
        assertThat(event.getLineItems().getFirst().getRamp()).isNull();
        assertThat(event.getLineItems().getFirst().getExtendedAmount()).isEqualByComparingTo("2000");
    }
    @Test void authoritativeProductProofParticipatesInApprovalBinding() {
        Quote quote=quote();QuoteLineItem line=line(quote);
        String previous=RampPricingSnapshot.fingerprint(quote,List.of(line));
        line.setPricingWaterfallJson(line.getPricingWaterfallJson().replace("\"authoritativeProductType\":\"SAAS_SEAT_NAMED\"", "\"authoritativeProductType\":\"ONPREM_SUPPORT\""));
        assertThat(RampPricingSnapshot.fingerprint(quote,List.of(line))).isNotEqualTo(previous);
    }

    private Quote quote() {
        return Quote.builder().id(1L).uuid("quote-r3-snapshot").quoteNumber("Q-R3-SNAPSHOT")
                .customerExternalId("synthetic-r3-customer").currency("USD")
                .durationInMonths(36).startDate(LocalDate.of(2027, 1, 1))
                .endDate(LocalDate.of(2029, 12, 31)).tcv(new BigDecimal("81000"))
                .acv(new BigDecimal("27000")).arr(new BigDecimal("24000"))
                .exitArr(new BigDecimal("30000")).subtotal(new BigDecimal("2000"))
                .totalAmount(new BigDecimal("2000")).build();
    }

    private QuoteLineItem line(Quote quote) {
        QuoteLineItem line = QuoteLineItem.builder().id(2L).uuid("accepted-line-r3")
                .quote(quote).productExternalId("synthetic-product-r3").productType("SAAS_SEAT_NAMED")
                .lineItemType("SAAS_SEAT_NAMED").billingFrequency("MONTHLY").quantity(50)
                .listPrice(new BigDecimal("50")).unitPrice(new BigDecimal("40"))
                .netPrice(new BigDecimal("40")).extendedAmount(new BigDecimal("2000")).build();
        for (int index = 0; index < 3; index++) {
            line.getRampSteps().add(QuoteRampStep.builder().lineItem(line).sequence(index + 1)
                    .startMonth(index * 12 + 1).months(12).quantity(50).priceBasis("ABSOLUTE")
                    .unitPrice(BigDecimal.valueOf(40 + index * 5)).build());
        }
        line.setPricingWaterfallJson("""
                {"authoritativeProductType":"SAAS_SEAT_NAMED","pricedPeriods":[
                  {"sequence":1,"startMonth":1,"months":12,"quantity":50,"netUnitPrice":40,"netMonthly":2000,"periodAmount":24000},
                  {"sequence":2,"startMonth":13,"months":12,"quantity":50,"netUnitPrice":45,"netMonthly":2250,"periodAmount":27000},
                  {"sequence":3,"startMonth":25,"months":12,"quantity":50,"netUnitPrice":50,"netMonthly":2500,"periodAmount":30000}
                ]}
                """);
        return line;
    }
}
