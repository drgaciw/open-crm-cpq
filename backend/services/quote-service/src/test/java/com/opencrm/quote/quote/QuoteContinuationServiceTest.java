package com.opencrm.quote.quote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.dto.ApiResponse;
import com.opencrm.common.event.*;
import com.opencrm.common.event.NonRetryableEventException;
import com.opencrm.common.event.dto.QuoteAcceptedEvent;
import com.opencrm.common.event.dto.SubscriptionRenewalEligibleEvent;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.quote.client.*;
import com.opencrm.quote.event.QuoteEventPublisher;
import com.opencrm.quote.lineitem.*;
import com.opencrm.quote.ramp.*;
import com.opencrm.security.CurrentActor;
import org.junit.jupiter.api.*;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;
import java.math.*;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@Tag("unit")
class QuoteContinuationServiceTest {
    private final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    private SubscriptionScheduleClient schedules;
    private SubscriptionScheduleView target;
    private QuoteRepository repository;
    private QuoteLineItemRepository lines;
    private PricingServiceClient pricing;
    private OutboxEventPublisher outbox;
    private JdbcTemplate jdbc;
    private RestClient scheduleHttp;
    private QuoteContinuationService continuations;
    private QuoteService lifecycle;
    private RampPricingProperties properties;
    private Quote saved;
    private final List<Map<String,Object>> requests=new ArrayList<>();
    private String partialTier;
    private boolean changePolicyRate;
    private String partialProof="SAAS_SEAT_NAMED";

    @BeforeEach void setup() {
        schedules=mock(SubscriptionScheduleClient.class);repository=mock(QuoteRepository.class);lines=mock(QuoteLineItemRepository.class);
        pricing=mock(PricingServiceClient.class);outbox=mock(OutboxEventPublisher.class);
        jdbc=mock(JdbcTemplate.class);scheduleHttp=mock(RestClient.class);
        when(jdbc.update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        properties=new RampPricingProperties();properties.setEnabled(true);
        var numbers=mock(QuoteNumberGenerator.class);when(numbers.generate()).thenReturn("Q-NEXT");
        var mapper=Mappers.getMapper(QuoteMapper.class);
        continuations=new QuoteContinuationService(schedules,repository,lines,numbers,mapper,properties,
                new RampScheduleAttacher(properties,new RampScheduleValidator(),new RampEscalatorExpander()),json,
                jdbc,new CurrentActor("service:quote-service",false),scheduleHttp);
        lifecycle=new QuoteService(repository,mapper,numbers,mock(QuoteEventPublisher.class),lines,pricing,mock(ProductServiceClient.class),
                json,outbox,new CurrentActor("service:quote-service",false),mock(RampScheduleGuard.class));
        lifecycle.setContinuationService(continuations);
        target=target();when(schedules.byNumber("SUB-R3")).thenAnswer(i -> target);
        when(repository.save(any())).thenAnswer(i -> {saved=i.getArgument(0);saved.setId(1L);saved.setUuid("synthetic-quote-next");return saved;});
        when(repository.findByIdAndDeletedFalse(1L)).thenAnswer(i -> Optional.ofNullable(saved));
        when(lines.save(any())).thenAnswer(i -> {QuoteLineItem line=i.getArgument(0);line.setId(2L);line.setUuid("synthetic-next-line");return line;});
        when(lines.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(1L)).thenAnswer(i -> saved.getLineItems());
        when(pricing.executePricing(anyMap())).thenAnswer(i -> policy(i.getArgument(0)));
    }
    @Test void namedSeatSourceRetainsActualKindThroughAuthorCalculateAndAccept() {
        var draft=continuations.author(request(LocalDate.of(2028,7,1)));
        assertThat(draft.getDealType()).isEqualTo(DealType.EXPANSION);
        assertThat(draft.getContinuationOperation()).isEqualTo(QuoteContinuationRequest.Operation.EXPANSION);
        assertThat(saved.getContinuationOperation()).isEqualTo(QuoteContinuationRequest.Operation.EXPANSION);
        assertThat(draft.getLineItems().getFirst().getLineItemType()).isEqualTo("SAAS_SEAT_NAMED");
        var priced=lifecycle.calculate(1L);assertThat(priced.getTcv()).isEqualByComparingTo("17400");
        assertThat(priced.getLineItems().getFirst().getSourceSubscriptionLineUuid()).isEqualTo("source-line");
        assertThat(((Map<?,?>)((List<?>)requests.getFirst().get("lineItems")).getFirst()).get("lineItemType")).isEqualTo("SAAS_SEAT_NAMED");
        saved.setStatus(QuoteStatus.SENT);lifecycle.accept(1L);
        var event=ArgumentCaptor.forClass(QuoteAcceptedEvent.class);
        verify(outbox).publish(eq("Quote"),eq("Q-NEXT"),eq(EventContractRegistry.QUOTE_ACCEPTED_V1),event.capture());
        assertThat(event.getValue().getSubscriptionChange().getObservedRevision()).isEqualTo(7L);
        assertThat(event.getValue().getLineItems().getFirst().getRamp()).hasSize(2);
        assertThat(event.getValue().getLineItems().getFirst().getExtendedAmount()).isEqualByComparingTo("900");
        assertThat(event.getValue().getLineItems().getFirst().getSourceSubscriptionLineUuid()).isEqualTo("source-line");
    }
    @Test void nonmonthlyBillingFrequencyAndNamedSeatKindArePreservedInCurrentPolicyRequest() {
        target.getLines().getFirst().setBillingFrequency("ANNUAL");continuations.author(request(LocalDate.of(2028,7,15)));lifecycle.calculate(1L);
        for(var context:requests) {
            var line=(Map<?,?>)((List<?>)context.get("lineItems")).getFirst();
            assertThat(line.get("billingFrequency")).isEqualTo("ANNUAL");assertThat(line.get("lineItemType")).isEqualTo("SAAS_SEAT_NAMED");
        }
    }
    @Test void actualDaysAreSeparateFromRealCurrentPolicyAndFullRamp() {
        continuations.author(request(LocalDate.of(2028,7,15)));var dto=lifecycle.calculate(1L);
        assertThat(requests).hasSize(2);assertThat(requests.getFirst().get("durationInMonths")).isEqualTo(1);
        assertThat(dto.getDurationInMonths()).isEqualTo(17);assertThat(dto.getStartDate()).isEqualTo(LocalDate.of(2028,8,1));
        assertThat(dto.getTcv()).isEqualByComparingTo("16993.55");assertThat(dto.getAcv()).isEqualByComparingTo("11995.45");
        assertThat(dto.getLineItems().getFirst().getInitialProration().getPeriodAmount()).isEqualByComparingTo("493.55");
        assertThat(dto.getLineItems().getFirst().getPricedPeriods()).extracting(PricedPeriodView::getMonths).containsExactly(5,12);
    }
    @Test void strongerPartialGovernanceIsBoundAndDoesNotDisappearWhenFollowingRateIsHigher() {
        partialTier="CFO";continuations.author(request(LocalDate.of(2028,12,15)));var dto=lifecycle.calculate(1L);
        assertThat(dto.getArr()).isEqualByComparingTo("10800");assertThat(dto.getExitArr()).isEqualByComparingTo("12000");
        assertThat(dto.getLineItems().getFirst().getExtendedAmount()).isEqualByComparingTo("900");
        assertThat(dto.getLineItems().getFirst().getPricedPeriods().getFirst().getNetMonthly()).isEqualByComparingTo("1000");
        assertThat(dto.getApprovalTier()).isEqualTo("CFO");assertThat(dto.getRampGovernance().getGoverningPeriod()).isZero();
        assertThat(dto.getRampGovernance().getReasons()).contains("CURRENT_PARTIAL_RULE");
        assertThat(RampPricingSnapshot.fingerprint(saved)).isEqualTo(RampPricingSnapshot.fingerprint(saved,saved.getLineItems()));
    }
    @Test void finalPartialOnlyHasNoInventedMonthlySoldRampAndKeepsPositiveNominalTerm() {
        continuations.author(request(LocalDate.of(2029,12,15)));var dto=lifecycle.calculate(1L);
        assertThat(requests).hasSize(1);assertThat(dto.getDurationInMonths()).isEqualTo(1);
        assertThat(dto.getTcv()).isEqualByComparingTo("548.39");assertThat(dto.getAcv()).isEqualByComparingTo("6580.68");
        var event=QuoteAcceptedKafkaEventFactory.from(saved,saved.getLineItems());
        assertThat(event.getLineItems().getFirst().getRamp()).isNull();
        assertThat(event.getLineItems().getFirst().getInitialProration().getPeriodAmount()).isEqualByComparingTo("548.39");
        assertThat(event.getLineItems().getFirst().getExtendedAmount()).isEqualByComparingTo("1000");
        assertThat(saved.getLineItems().getFirst().getRampSteps()).isEmpty();
    }
    @Test void authoritativeCurrentPolicyChangingInheritedPartialRateFailsActionably() {
        continuations.author(request(LocalDate.of(2028,7,15)));changePolicyRate=true;
        assertThatThrownBy(() -> lifecycle.calculate(1L)).isInstanceOf(BusinessException.class).hasMessageContaining("unit rate");
        assertThat(saved.isRampScheduleStale()).isTrue();assertThat(saved.getTcv()).isNull();
    }
    @Test void targetRevisionIsReloadedAtCalculateAndAtAccept() {
        continuations.author(request(LocalDate.of(2028,7,1)));target.setCommercialRevision(8L);
        assertThatThrownBy(() -> lifecycle.calculate(1L)).isInstanceOf(BusinessException.class);verifyNoInteractions(pricing);
        target.setCommercialRevision(7L);lifecycle.calculate(1L);target.setCommercialRevision(8L);saved.setStatus(QuoteStatus.SENT);
        assertThatThrownBy(() -> lifecycle.accept(1L)).isInstanceOf(BusinessException.class);
        assertThat(saved.getStatus()).isEqualTo(QuoteStatus.SENT);verifyNoInteractions(outbox);
    }
    @Test void tamperedAuthoredFutureRateCannotBecomeTrustedContinuationPricing() {
        continuations.author(request(LocalDate.of(2028,7,1)));saved.getLineItems().getFirst().getRampSteps().getFirst().setUnitPrice(new BigDecimal("1"));
        assertThatThrownBy(() -> lifecycle.calculate(1L)).isInstanceOf(BusinessException.class);verifyNoInteractions(pricing);
    }
    @Test void legacyClassificationAndFlagOffFailBeforeAuthoring() {
        target.getLines().getFirst().setLineItemType(null);
        assertThatThrownBy(() -> continuations.author(request(LocalDate.of(2028,7,1)))).isInstanceOf(BusinessException.class);
        verify(repository,never()).save(any());
        properties.setEnabled(false);
        assertThatThrownBy(() -> continuations.preview(request(LocalDate.of(2028,7,15)))).isInstanceOf(RampScheduleException.class);
    }
    @Test void pricingFlagOffForFinalPartialCannotBeBypassedByLocalArithmetic() {
        continuations.author(request(LocalDate.of(2029,12,15)));
        doReturn(ApiResponse.error("Ramp pricing is disabled","RAMP_NOT_SUPPORTED",null)).when(pricing).executePricing(anyMap());
        assertThatThrownBy(() -> lifecycle.calculate(1L)).isInstanceOf(RampScheduleException.class);
        assertThat(saved.isRampScheduleStale()).isTrue();
    }
    @Test void unverifiedPartialProductClassificationCannotBindContinuationPrices() {
        continuations.author(request(LocalDate.of(2028,7,15)));partialProof=null;
        assertThatThrownBy(() -> lifecycle.calculate(1L)).isInstanceOf(BusinessException.class);
        assertThat(saved.isRampScheduleStale()).isTrue();assertThat(saved.getTcv()).isNull();
    }
    @Test void oneSourceContinuationRejectsAdditionalFeeBeforeAnyPricing() {
        continuations.author(request(LocalDate.of(2028,7,15)));
        saved.getLineItems().add(QuoteLineItem.builder().quote(saved).lineItemType("FEE").quantity(1)
                .productExternalId("synthetic-fee").listPrice(new BigDecimal("100")).extendedAmount(new BigDecimal("100")).build());
        assertThatThrownBy(() -> continuations.validate(saved,saved.getLineItems())).isInstanceOf(BusinessException.class)
                .hasMessageContaining("one validated");
        verifyNoInteractions(pricing);
    }
    @Test void systemRenewalDraftUsesExitRateAndRecordsTheCommercialDecision() {
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        SubscriptionRenewalEligibleEvent event = new SubscriptionRenewalEligibleEvent("SUB-R3", LocalDate.of(2029, 12, 1), "Growth", "HIGH", null);
        QuoteDto draft = continuations.draftRenewal(event, null, "event-1", null);
        assertThat(draft.getDealType()).isEqualTo(DealType.RENEWAL);
        assertThat(draft.getStatus()).isEqualTo(QuoteStatus.DRAFT);
        assertThat(draft.getQuoteNumber()).isEqualTo("Q-NEXT");
        assertThat(draft.getStartDate()).isEqualTo(LocalDate.of(2030, 1, 1));
        assertThat(draft.getEndDate()).isEqualTo(LocalDate.of(2030, 12, 31));
        assertThat(draft.getSubscriptionEffectiveDate()).isEqualTo(LocalDate.of(2030, 1, 1));
        assertThat(draft.getDurationInMonths()).isEqualTo(12);
        assertThat(draft.getLineItems().getFirst().getQuantity()).isEqualTo(50);
        assertThat(draft.getLineItems().getFirst().getUnitPrice()).isEqualByComparingTo("50.00");
        assertThat(saved.getContinuationRequestJson()).contains("RENEWAL");
        assertThat(saved.getContinuationOperation()).isEqualTo(QuoteContinuationRequest.Operation.RENEWAL);
        assertThat(draft.getContinuationOperation()).isEqualTo(QuoteContinuationRequest.Operation.RENEWAL);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), eq(1L), eq("Q-NEXT"), eq(QuoteContinuationService.SYSTEM_RENEWAL_DRAFT),
                eq("service:quote-service"), eq("QuoteContinuationPolicy"), eq("RENEWAL"), eq("RENEWAL_EXIT_RATE"),
                eq("SUB-R3"), eq("event-1"), contains("\"upliftPercent\":\"0\""), contains("\"unitPrice\":\"50.00\""));
        assertThat(sql.getValue()).startsWith("INSERT INTO commercial_decision_audit");
        verifyNoInteractions(outbox);
    }

    @Test void extensionStoresRenewalDealTypeAndExtensionOperation() {
        QuoteContinuationRequest extension = request(LocalDate.of(2030, 1, 1));
        extension.setOperation(QuoteContinuationRequest.Operation.EXTENSION);
        QuoteDto draft = continuations.author(extension);
        assertThat(draft.getDealType()).isEqualTo(DealType.RENEWAL);
        assertThat(draft.getContinuationOperation()).isEqualTo(QuoteContinuationRequest.Operation.EXTENSION);
        assertThat(saved.getDealType()).isEqualTo(DealType.RENEWAL);
        assertThat(saved.getContinuationOperation()).isEqualTo(QuoteContinuationRequest.Operation.EXTENSION);
        assertThat(saved.getContinuationRequestJson()).contains("EXTENSION");
        assertThatCode(() -> continuations.validate(saved, saved.getLineItems())).doesNotThrowAnyException();
        saved.setContinuationOperation(QuoteContinuationRequest.Operation.RENEWAL);
        assertThatThrownBy(() -> continuations.validate(saved, saved.getLineItems()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("commercial identities");
    }

    @Test void rollingUpgradeContinuationIsRepairedAtCalculateAndAccept() {
        QuoteContinuationRequest extension = request(LocalDate.of(2030, 1, 1));
        extension.setOperation(QuoteContinuationRequest.Operation.EXTENSION);
        continuations.author(extension);
        saved.setContinuationOperation(null); // An old writer ran after V1_12's one-time backfill.
        assertThat(lifecycle.calculate(1L).getContinuationOperation())
                .isEqualTo(QuoteContinuationRequest.Operation.EXTENSION);
        assertThat(saved.getContinuationOperation()).isEqualTo(QuoteContinuationRequest.Operation.EXTENSION);
        saved.setContinuationOperation(null);
        saved.setStatus(QuoteStatus.SENT);
        assertThat(lifecycle.accept(1L).getContinuationOperation())
                .isEqualTo(QuoteContinuationRequest.Operation.EXTENSION);
    }

    @Test void legacyNullOperationIsNotRepairedWhenValidationFails() {
        continuations.author(request(LocalDate.of(2028, 7, 1)));
        saved.setContinuationOperation(null);
        target.setCommercialRevision(8L);
        assertThatThrownBy(() -> continuations.validate(saved, saved.getLineItems()))
                .isInstanceOf(BusinessException.class);
        assertThat(saved.getContinuationOperation()).isNull();
        saved.setContinuationRequestJson("unreadable");
        assertThatThrownBy(() -> continuations.validate(saved, saved.getLineItems()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Unreadable");
        assertThat(saved.getContinuationOperation()).isNull();
    }

    @Test void extensionCannotConsumeARenewalEligibilityEvent() {
        for (boolean legacy : List.of(false, true)) {
            QuoteContinuationRequest extension = request(LocalDate.of(2030, 1, 1));
            extension.setOperation(QuoteContinuationRequest.Operation.EXTENSION);
            continuations.author(extension);
            Quote existingExtension = saved;
            if (legacy) existingExtension.setContinuationOperation(null);
            when(repository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(existingExtension)));
            clearInvocations(repository, jdbc);
            var draft = continuations.draftRenewal(renewalEvent(), null, "event-1", null);
            assertThat(draft.getContinuationOperation()).isEqualTo(QuoteContinuationRequest.Operation.RENEWAL);
            assertThat(saved).isNotSameAs(existingExtension);
            verify(repository).save(any(Quote.class));
            verify(jdbc).update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        }
    }

    @Test void legacyRenewalAfterAPageOfExtensionsStillDeduplicates() {
        QuoteContinuationRequest extension = request(LocalDate.of(2030, 1, 1));
        extension.setOperation(QuoteContinuationRequest.Operation.EXTENSION);
        continuations.author(extension);
        Quote oldExtension = saved;
        oldExtension.setContinuationOperation(null);
        QuoteContinuationRequest renewal = request(LocalDate.of(2030, 1, 1));
        renewal.setOperation(QuoteContinuationRequest.Operation.RENEWAL);
        continuations.author(renewal);
        Quote oldRenewal = saved;
        oldRenewal.setContinuationOperation(null);
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenAnswer(invocation -> {
            Pageable page = invocation.getArgument(1);
            return page.getPageNumber() == 0
                    ? new PageImpl<>(Collections.nCopies(50, oldExtension), page, 51)
                    : new PageImpl<>(List.of(oldRenewal), page, 51);
        });
        clearInvocations(repository, jdbc);
        assertThat(continuations.draftRenewal(renewalEvent(), null, "event-1", null).getQuoteNumber())
                .isEqualTo(oldRenewal.getQuoteNumber());
        var pages = ArgumentCaptor.forClass(Pageable.class);
        verify(repository, times(2)).findAll(any(Specification.class), pages.capture());
        assertThat(pages.getAllValues()).extracting(Pageable::getPageNumber).containsExactly(0, 1);
        verify(repository, never()).save(any(Quote.class));
        verifyNoInteractions(jdbc);
    }

    @Test void unidentifiableLegacyQuoteIsNotAssumedToBeARenewal() {
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(
                        Quote.builder().continuationRequestJson("unreadable").build(),
                        Quote.builder().continuationRequestJson("{}").build(),
                        Quote.builder().build())));
        assertThat(continuations.draftRenewal(renewalEvent(), null, "event-1", null).getContinuationOperation())
                .isEqualTo(QuoteContinuationRequest.Operation.RENEWAL);
        verify(repository).save(any(Quote.class));
    }

    private SubscriptionRenewalEligibleEvent renewalEvent() {
        return new SubscriptionRenewalEligibleEvent("SUB-R3", LocalDate.of(2029, 12, 1), "Growth", "HIGH", null);
    }

    @Test void renewalOperationStaysDistinctFromExtension() {
        QuoteContinuationRequest renewal = request(LocalDate.of(2030, 1, 1));
        renewal.setOperation(QuoteContinuationRequest.Operation.RENEWAL);
        continuations.author(renewal);
        assertThat(saved.getDealType()).isEqualTo(DealType.RENEWAL);
        assertThat(saved.getContinuationOperation()).isEqualTo(QuoteContinuationRequest.Operation.RENEWAL);
        assertThat(saved.getContinuationRequestJson()).contains("RENEWAL").doesNotContain("EXTENSION");
    }

    @Test void responseDistinguishesExtensionFromRenewal() {
        QuoteContinuationRequest extension = request(LocalDate.of(2030, 1, 1));
        extension.setOperation(QuoteContinuationRequest.Operation.EXTENSION);
        QuoteDto extensionDraft = continuations.author(extension);
        QuoteContinuationRequest renewal = request(LocalDate.of(2030, 1, 1));
        renewal.setOperation(QuoteContinuationRequest.Operation.RENEWAL);
        QuoteDto renewalDraft = continuations.author(renewal);
        assertThat(extensionDraft.getDealType()).isEqualTo(DealType.RENEWAL);
        assertThat(renewalDraft.getDealType()).isEqualTo(DealType.RENEWAL);
        assertThat(extensionDraft.getContinuationOperation()).isEqualTo(QuoteContinuationRequest.Operation.EXTENSION);
        assertThat(renewalDraft.getContinuationOperation()).isEqualTo(QuoteContinuationRequest.Operation.RENEWAL);
    }

    @Test void secondEligibilityForTheSameWindowDoesNotCreateAnotherQuote() {
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()))
                .thenAnswer(invocation -> new PageImpl<>(List.of(saved)));
        SubscriptionRenewalEligibleEvent event = new SubscriptionRenewalEligibleEvent("SUB-R3", LocalDate.of(2029, 12, 1), "Growth", "HIGH", null);
        continuations.draftRenewal(event, null, "event-1", null);
        QuoteDto again = continuations.draftRenewal(event, null, "event-2", "sub-r3:2029");
        assertThat(again.getQuoteNumber()).isEqualTo("Q-NEXT");
        verify(repository, times(1)).save(any(Quote.class));
        verify(jdbc, times(1)).update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verifyNoInteractions(outbox);
    }

    @Test void ambiguousSourceLineIsNotRetried() {
        target.setLines(List.of(target.getLines().getFirst(), target.getLines().getFirst()));
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        assertThatThrownBy(() -> continuations.draftRenewal(
                new SubscriptionRenewalEligibleEvent("SUB-R3", LocalDate.of(2029, 12, 1), "Growth", "HIGH", null),
                null, "event-1", null))
                .isInstanceOf(NonRetryableEventException.class)
                .hasMessageContaining("exactly one");
        verify(repository, never()).save(any());
        verifyNoInteractions(jdbc);
    }

    @Test void numericSubscriptionIdReadsTheScheduleByPrimaryKey() throws Exception {
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        RestClient.RequestHeadersUriSpec<?> get = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.ResponseSpec response = mock(RestClient.ResponseSpec.class);
        doReturn(get).when(scheduleHttp).get();
        doReturn(get).when(get).uri("/api/v1/subscriptions/{id}/ramp-schedule", "42");
        doReturn(response).when(get).retrieve();
        when(response.body(String.class)).thenReturn(json.writeValueAsString(ApiResponse.success(target)));
        QuoteDto draft = continuations.draftRenewal(
                new SubscriptionRenewalEligibleEvent("42", LocalDate.of(2029, 12, 1), "Growth", "HIGH", null), null, "event-9", null);
        assertThat(draft.getTargetSubscriptionNumber()).isEqualTo("SUB-R3");
        assertThat(draft.getDealType()).isEqualTo(DealType.RENEWAL);
        verify(schedules, atLeastOnce()).byNumber("SUB-R3");
    }

    private QuoteContinuationRequest request(LocalDate effective) {
        return QuoteContinuationRequest.builder().operation(QuoteContinuationRequest.Operation.EXPANSION).targetSubscriptionNumber("SUB-R3")
                .sourceSubscriptionLineUuid("source-line").observedRevision(7L).customerExternalId("synthetic-customer").currency("USD")
                .effectiveDate(effective).quantity(20).build();
    }
    private SubscriptionScheduleView target() {
        var source=SubscriptionScheduleView.Line.builder().acceptedLineUuid("source-line").productExternalId("synthetic-product")
                .productType("SAAS_SEAT_NAMED").lineItemType("SAAS_SEAT_NAMED").billingFrequency("MONTHLY").billingAnchorDate(LocalDate.of(2027,1,1))
                .provenance("ACCEPTED_PRICED").periods(new ArrayList<>()).build();
        for(int i=0;i<3;i++) source.getPeriods().add(SubscriptionScheduleView.Period.builder().sequence(i+1).startMonth(i*12+1).months(12).quantity(50)
                .startDate(LocalDate.of(2027+i,1,1)).endDate(LocalDate.of(2027+i,12,31)).netUnitPrice(BigDecimal.valueOf(40+i*5))
                .netMonthly(BigDecimal.valueOf(2000+i*250)).periodAmount(BigDecimal.valueOf(24000+i*3000)).build());
        return SubscriptionScheduleView.builder().subscriptionNumber("SUB-R3").customerId("synthetic-customer").currencyCode("USD").status("ACTIVE")
                .startDate(LocalDate.of(2027,1,1)).endDate(LocalDate.of(2029,12,31)).commercialRevision(7L).lines(List.of(source)).build();
    }
    @SuppressWarnings("unchecked")
    private ApiResponse<PricingExecuteView> policy(Map<String,Object> context) {
        requests.add(context);
        var requested=((List<Map<String,Object>>)context.get("lineItems")).getFirst();
        int quantity=(int)requested.get("quantity");
        List<PricedPeriodView> periods=new ArrayList<>();
        for(var step:(List<Map<String,Object>>)requested.get("schedule")) {
            BigDecimal unit=new BigDecimal(step.get("unitPrice").toString());if(changePolicyRate)unit=unit.add(BigDecimal.ONE);
            BigDecimal monthly=unit.multiply(BigDecimal.valueOf(quantity));
            periods.add(PricedPeriodView.builder().sequence((int)step.get("sequence")).startMonth((int)step.get("startMonth"))
                    .months((int)step.get("months")).quantity(quantity).netUnitPrice(unit).netMonthly(monthly)
                    .periodAmount(monthly.multiply(BigDecimal.valueOf((int)step.get("months"))))
                    .discountRate(new BigDecimal("50").subtract(unit).max(BigDecimal.ZERO).divide(new BigDecimal("50"),4,RoundingMode.HALF_UP)).build());
        }
        String tier=(int)context.get("durationInMonths")==1 && partialTier!=null ? partialTier : null;
        if(tier==null && periods.stream().anyMatch(p -> p.getDiscountRate().compareTo(new BigDecimal(".10"))>=0))tier="DEAL_DESK";
        Map<String,Object> metadata=new HashMap<>();metadata.put("rampGovernanceEvaluated",true);metadata.put("rampPolicyContext","synthetic-current-policy-"+periods.getFirst().getNetUnitPrice());
        if ((int)context.get("durationInMonths") != 1 || partialProof != null)
            metadata.put("authoritativeProductType",(int)context.get("durationInMonths")==1 ? partialProof : "SAAS_SEAT_NAMED");
        if(tier!=null)metadata.put("rampGovernance",Map.of("approvalTier",tier,"governingPeriod",1,"discountPercent",periods.getFirst().getDiscountRate().multiply(new BigDecimal("100")),
                "stepDown",false,"reasons",List.of((int)context.get("durationInMonths")==1 && partialTier!=null ? "CURRENT_PARTIAL_RULE" : "CURRENT_DISCOUNT_RULE")));
        BigDecimal total=periods.stream().map(PricedPeriodView::getPeriodAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        var line=PricingLineResultView.builder().productExternalId((String)requested.get("productExternalId")).lineItemType((String)requested.get("lineItemType"))
                .quantity(quantity).listPrice(new BigDecimal("50")).unitPrice(periods.getFirst().getNetUnitPrice())
                .finalPrice(periods.getFirst().getNetMonthly()).discountRate(periods.getFirst().getDiscountRate())
                .discountAmount(new BigDecimal("50").multiply(BigDecimal.valueOf(quantity)).subtract(periods.getFirst().getNetMonthly()))
                .pricedPeriods(periods).metadata(metadata).build();
        return ApiResponse.success(PricingExecuteView.builder().success(true).lineResults(List.of(line)).approvalTier(tier).approvalState(tier==null ? "NONE" : "PENDING")
                .financialMetrics(PricingFinancialMetricsView.builder().tcv(total).acv(total.multiply(new BigDecimal("12")).divide(BigDecimal.valueOf((int)context.get("durationInMonths")),2,RoundingMode.HALF_UP))
                        .arr(periods.getFirst().getNetMonthly().multiply(new BigDecimal("12"))).exitArr(periods.getLast().getNetMonthly().multiply(new BigDecimal("12"))).build()).build());
    }
}
