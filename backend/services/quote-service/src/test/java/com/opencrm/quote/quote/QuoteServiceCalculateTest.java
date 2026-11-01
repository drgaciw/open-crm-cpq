package com.opencrm.quote.quote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.dto.ApiResponse;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.security.CurrentActor;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.quote.client.PricedPeriodView;
import com.opencrm.quote.client.PricingExecuteView;
import com.opencrm.quote.client.PricingFinancialMetricsView;
import com.opencrm.quote.client.PricingLineResultView;
import com.opencrm.quote.client.PricingServiceClient;
import com.opencrm.quote.client.ProductServiceClient;
import com.opencrm.quote.event.QuoteEventPublisher;
import com.opencrm.quote.lineitem.QuoteLineItem;
import com.opencrm.quote.lineitem.QuoteLineItemDto;
import com.opencrm.quote.lineitem.QuoteLineItemRepository;
import com.opencrm.quote.ramp.QuoteRampStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("QuoteService.calculate typed pricing apply (Wave J1)")
class QuoteServiceCalculateTest {

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
    private QuoteContinuationService continuationService;

    private QuoteService quoteService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        quoteService = new QuoteService(
                quoteRepository,
                quoteMapper,
                quoteNumberGenerator,
                eventPublisher,
                quoteLineItemRepository,
                pricingServiceClient,
                productServiceClient,
                objectMapper,
                outboxEventPublisher,
                new CurrentActor("service:quote-service", false),
                RampScheduleTestSupport.guard(quoteLineItemRepository, outboxEventPublisher)
        );
    }

    @Test
    @DisplayName("calculate applies line results, financial metrics, and waterfall")
    void calculateAppliesTypedPricingViews() throws Exception {
        Quote quote = draftQuote();
        QuoteLineItem line = lineItem(quote, "PROD-001", "SUBSCRIPTION", 5, new BigDecimal("80.00"));
        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(quote));
        when(quoteLineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(1L))
                .thenReturn(List.of(line));

        PricingExecuteView pricingData = PricingExecuteView.builder()
                .success(true)
                .approvalState("APPROVED")
                .approvalTier("TIER_1")
                .lineResults(List.of(
                        PricingLineResultView.builder()
                                .productExternalId("PROD-001")
                                .lineItemType("SUBSCRIPTION")
                                .listPrice(new BigDecimal("100.00"))
                                .unitPrice(new BigDecimal("90.00"))
                                .discountRate(new BigDecimal("0.1000"))
                                .discountAmount(new BigDecimal("50.00"))
                                .finalPrice(new BigDecimal("450.00"))
                                .explanationCodes(List.of("LIST", "DISC"))
                                .build()
                ))
                .financialMetrics(PricingFinancialMetricsView.builder()
                        .arr(new BigDecimal("5400"))
                        .tcv(new BigDecimal("5400"))
                        .acv(new BigDecimal("450.00"))
                        .exitArr(new BigDecimal("99.00"))
                        .build())
                .waterfall(objectMapper.readTree("""
                        {"listPrice":100.00,"netPrice":450.00,"approvalState":"APPROVED"}
                        """))
                .build();

        when(pricingServiceClient.executePricing(anyMap()))
                .thenReturn(ApiResponse.success(pricingData, "ok"));
        when(quoteRepository.save(quote)).thenReturn(quote);
        when(quoteMapper.toDto(quote)).thenReturn(QuoteDto.builder().id(1L).quoteNumber("Q-1").build());

        QuoteDto view = quoteService.calculate(1L);
        verify(quoteRepository).flush();

        assertThat(line.getListPrice()).isEqualByComparingTo("100.00");
        assertThat(line.getUnitPrice()).isEqualByComparingTo("90.00");
        assertThat(line.getDiscountAmount()).isEqualByComparingTo("50.00");
        assertThat(line.getDiscountRate()).isEqualByComparingTo("0.1000");
        assertThat(line.getExtendedAmount()).isEqualByComparingTo("450.00");
        assertThat(line.getNetPrice()).isEqualByComparingTo("90.0000");
        assertThat(line.getPricingExplanationCode()).isEqualTo("LIST");

        assertThat(quote.getSubtotal()).isEqualByComparingTo("500.00");
        assertThat(quote.getTotalDiscount()).isEqualByComparingTo("50.00");
        assertThat(quote.getTotalAmount()).isEqualByComparingTo("450.00");
        assertThat(quote.getArr()).isEqualByComparingTo("5400");
        assertThat(quote.getTcv()).isEqualByComparingTo("5400");
        assertThat(quote.getAcv()).isEqualByComparingTo("450.00");
        assertThat(quote.getExitArr()).isEqualByComparingTo("99.00");
        assertThat(view.getExitArr()).isEqualByComparingTo("99.00");
        assertThat(quote.getApprovalState()).isEqualTo(ApprovalState.APPROVED);
        assertThat(quote.getApprovalTier()).isEqualTo("TIER_1");
        assertThat(quote.getPricingWaterfallJson()).contains("\"netPrice\":450.0");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> contextCaptor = ArgumentCaptor.forClass(Map.class);
        verify(pricingServiceClient).executePricing(contextCaptor.capture());
        assertThat(contextCaptor.getValue().get("quoteExternalId")).isEqualTo("Q-1");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sentLines =
                (List<Map<String, Object>>) contextCaptor.getValue().get("lineItems");
        assertThat(sentLines).allSatisfy(lineMap -> assertThat(lineMap.keySet())
                .containsExactlyInAnyOrder("productExternalId", "lineItemType", "quantity"));
        verify(quoteLineItemRepository).saveAll(List.of(line));
    }

    @Test
    @DisplayName("calculate uses decimal fallbacks when line price fields are null")
    void calculateUsesDecimalFallbacks() {
        Quote quote = draftQuote();
        QuoteLineItem line = lineItem(quote, "PROD-001", "SUBSCRIPTION", 2, new BigDecimal("100.00"));
        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(quote));
        when(quoteLineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(1L))
                .thenReturn(List.of(line));

        PricingExecuteView pricingData = PricingExecuteView.builder()
                .success(true)
                .lineResults(List.of(
                        PricingLineResultView.builder()
                                .productExternalId("PROD-001")
                                .lineItemType("SUBSCRIPTION")
                                .build()
                ))
                .build();
        when(pricingServiceClient.executePricing(anyMap()))
                .thenReturn(ApiResponse.success(pricingData));
        when(quoteRepository.save(quote)).thenReturn(quote);
        when(quoteMapper.toDto(quote)).thenReturn(QuoteDto.builder().id(1L).build());

        quoteService.calculate(1L);

        assertThat(line.getListPrice()).isEqualByComparingTo("100.00");
        assertThat(line.getUnitPrice()).isEqualByComparingTo("100.00");
        assertThat(line.getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(line.getDiscountRate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(line.getExtendedAmount()).isEqualByComparingTo("200.00");
        assertThat(line.getNetPrice()).isEqualByComparingTo("100.0000");
        assertThat(line.getPricingExplanationCode()).isNull();
        assertThat(quote.getSubtotal()).isEqualByComparingTo("200.00");
        assertThat(quote.getTotalAmount()).isEqualByComparingTo("200.00");
    }

    @Test
    @DisplayName("calculate maps RestClientException to PRICING_SERVICE_UNAVAILABLE")
    void calculateMapsTransportFailure() {
        Quote quote = draftQuote();
        QuoteLineItem line = lineItem(quote, "PROD-001", "SUBSCRIPTION", 1, BigDecimal.TEN);
        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(quote));
        when(quoteLineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(1L))
                .thenReturn(List.of(line));
        when(pricingServiceClient.executePricing(anyMap()))
                .thenThrow(new RestClientException("connection refused"));

        assertThatThrownBy(() -> quoteService.calculate(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("PRICING_SERVICE_UNAVAILABLE");
    }

    @Test
    @DisplayName("calculate maps wrapper success=false to PRICING_EXECUTION_FAILED")
    void calculateMapsWrapperFailure() {
        Quote quote = draftQuote();
        QuoteLineItem line = lineItem(quote, "PROD-001", "SUBSCRIPTION", 1, BigDecimal.TEN);
        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(quote));
        when(quoteLineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(1L))
                .thenReturn(List.of(line));
        when(pricingServiceClient.executePricing(anyMap()))
                .thenReturn(ApiResponse.error("upstream rejected"));

        assertThatThrownBy(() -> quoteService.calculate(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo("PRICING_EXECUTION_FAILED");
                    assertThat(be.getMessage()).isEqualTo("upstream rejected");
                });
    }

    @Test
    @DisplayName("calculate maps data.success=false to PRICING_EXECUTION_FAILED with errorMessage")
    void calculateMapsPricingDataFailure() {
        Quote quote = draftQuote();
        QuoteLineItem line = lineItem(quote, "PROD-001", "SUBSCRIPTION", 1, BigDecimal.TEN);
        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(quote));
        when(quoteLineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(1L))
                .thenReturn(List.of(line));
        when(pricingServiceClient.executePricing(anyMap()))
                .thenReturn(ApiResponse.success(PricingExecuteView.builder()
                        .success(false)
                        .errorMessage("no price book entry")
                        .build()));

        assertThatThrownBy(() -> quoteService.calculate(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo("PRICING_EXECUTION_FAILED");
                    assertThat(be.getMessage()).isEqualTo("no price book entry");
                });
    }

    @Test
    @DisplayName("calculate maps missing line result to PRICING_RESULT_MISMATCH")
    void calculateMapsMissingLineResult() {
        Quote quote = draftQuote();
        QuoteLineItem line = lineItem(quote, "PROD-001", "SUBSCRIPTION", 1, BigDecimal.TEN);
        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(quote));
        when(quoteLineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(1L))
                .thenReturn(List.of(line));
        when(pricingServiceClient.executePricing(anyMap()))
                .thenReturn(ApiResponse.success(PricingExecuteView.builder()
                        .success(true)
                        .lineResults(List.of(
                                PricingLineResultView.builder()
                                        .productExternalId("OTHER")
                                        .lineItemType("SUBSCRIPTION")
                                        .listPrice(BigDecimal.ONE)
                                        .build()
                        ))
                        .build()));

        assertThatThrownBy(() -> quoteService.calculate(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("PRICING_RESULT_MISMATCH");
    }

    @Test
    @DisplayName("AC-01 calculate returns TCV 81000, ACV 27000, arr 24000, and exitArr 30000")
    void calculateAc01ReturnsRampTotalsOnTheQuoteView() {
        Quote quote = draftQuote();
        quote.setQuoteNumber("Q-RAMP-01");
        quote.setCustomerExternalId("CUST-RAMP-01");
        quote.setDurationInMonths(36);
        QuoteLineItem line = lineItem(quote, "PROD-SEAT", "SAAS_SEAT_NAMED", 50, new BigDecimal("50.00"));
        line.setProductType("SAAS_SEAT_NAMED");
        line.setId(5L);
        line.setBillingFrequency("MONTHLY");
        line.setRampSteps(new ArrayList<>());
        line.getRampSteps().add(absoluteStep(line, 1, 1, "40"));
        line.getRampSteps().add(absoluteStep(line, 2, 13, "45"));
        line.getRampSteps().add(absoluteStep(line, 3, 25, "50"));
        QuoteRampStep deleted = absoluteStep(line, 4, 37, "1");
        deleted.softDelete();
        line.getRampSteps().add(deleted);

        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(quote));
        when(quoteLineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(1L))
                .thenReturn(List.of(line));

        List<PricedPeriodView> periods = List.of(
                period(1, 1, "40.0000", "2000.00", "24000.00"),
                period(2, 13, "45.0000", "2250.00", "27000.00"),
                period(3, 25, "50.0000", "2500.00", "30000.00"));
        PricingExecuteView pricingData = PricingExecuteView.builder()
                .success(true)
                .lineResults(List.of(
                        PricingLineResultView.builder()
                                .productExternalId("PROD-SEAT")
                                .lineItemType("SAAS_SEAT_NAMED")
                                .quantity(50)
                                .listPrice(new BigDecimal("50.00"))
                                .unitPrice(new BigDecimal("40.0000"))
                                .discountRate(new BigDecimal("0.2000"))
                                .discountAmount(new BigDecimal("500.00"))
                                .finalPrice(new BigDecimal("2000.00"))
                                .metadata(Map.of("rampGovernanceEvaluated", true,
                                        "authoritativeProductType", "SAAS_SEAT_NAMED")).pricedPeriods(periods)
                                .build()))
                .financialMetrics(PricingFinancialMetricsView.builder()
                        .arr(new BigDecimal("24000.00"))
                        .tcv(new BigDecimal("81000.00"))
                        .acv(new BigDecimal("27000.00"))
                        .exitArr(new BigDecimal("30000.00"))
                        .build())
                .build();
        when(pricingServiceClient.executePricing(anyMap()))
                .thenReturn(ApiResponse.success(pricingData, "ok"));
        when(quoteRepository.save(quote)).thenReturn(quote);
        QuoteLineItemDto lineDto = QuoteLineItemDto.builder()
                .id(5L)
                .productExternalId("PROD-SEAT")
                .lineItemType("SAAS_SEAT_NAMED")
                .quantity(50)
                .build();
        QuoteDto mapped = QuoteDto.builder()
                .id(1L)
                .quoteNumber("Q-RAMP-01")
                .lineItems(new ArrayList<>(List.of(lineDto)))
                .build();
        when(quoteMapper.toDto(quote)).thenReturn(mapped);

        QuoteDto view = quoteService.calculate(1L);

        assertThat(view.getTcv()).isEqualByComparingTo("81000.00");
        assertThat(view.getAcv()).isEqualByComparingTo("27000.00");
        assertThat(view.getArr()).isEqualByComparingTo("24000.00");
        assertThat(view.getExitArr()).isEqualByComparingTo("30000.00");
        assertThat(quote.getTcv()).isEqualByComparingTo("81000.00");
        assertThat(quote.getAcv()).isEqualByComparingTo("27000.00");
        assertThat(quote.getArr()).isEqualByComparingTo("24000.00");
        assertThat(quote.getExitArr()).isEqualByComparingTo("30000.00");
        assertThat(view.getLineItems().getFirst().getPricedPeriods()).containsExactlyElementsOf(periods);
        assertThat(view.getLineItems().getFirst().getPricedPeriods())
                .extracting(PricedPeriodView::getPeriodAmount)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("24000.00"), new BigDecimal("27000.00"), new BigDecimal("30000.00"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> contextCaptor = ArgumentCaptor.forClass(Map.class);
        verify(pricingServiceClient).executePricing(contextCaptor.capture());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sentLines =
                (List<Map<String, Object>>) contextCaptor.getValue().get("lineItems");
        assertThat(sentLines).hasSize(1);
        assertThat(sentLines.getFirst().get("billingFrequency")).isEqualTo("MONTHLY");
        assertThat(sentLines.getFirst()).doesNotContainKeys("steps", "escalator");
        JsonNode sentLine = objectMapper.valueToTree(sentLines.getFirst());
        assertThat(sentLine.get("schedule").isArray()).isTrue();
        assertThat(sentLine.get("schedule")).hasSize(3);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> schedule =
                (List<Map<String, Object>>) sentLines.getFirst().get("schedule");
        assertThat(schedule).hasSize(3);
        assertThat(schedule).allSatisfy(step -> assertThat(step.keySet()).containsExactlyInAnyOrder(
                "sequence", "startMonth", "months", "quantity", "priceBasis", "unitPrice"));
        assertThat(schedule).extracting(step -> step.get("sequence")).containsExactly(1, 2, 3);
        assertThat(schedule).extracting(step -> step.get("startMonth")).containsExactly(1, 13, 25);
        assertThat(schedule).extracting(step -> step.get("months")).containsExactly(12, 12, 12);
        assertThat(schedule).extracting(step -> step.get("quantity")).containsExactly(50, 50, 50);
        assertThat(schedule).allSatisfy(step -> {
            assertThat(step.get("priceBasis")).isEqualTo("ABSOLUTE");
            assertThat(step).doesNotContainKey("discountFraction");
        });
        assertThat((BigDecimal) schedule.get(0).get("unitPrice")).isEqualByComparingTo("40");
        assertThat((BigDecimal) schedule.get(1).get("unitPrice")).isEqualByComparingTo("45");
        assertThat((BigDecimal) schedule.get(2).get("unitPrice")).isEqualByComparingTo("50");
        verify(quoteLineItemRepository).saveAll(List.of(line));
        verify(quoteRepository).save(quote);
    }

    @Test
    @DisplayName("calculate sends the mapped quote DealType, not the CRM source name")
    void calculateSendsMappedDealType() {
        quoteService.setContinuationService(continuationService);
        when(continuationService.validate(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(null);
        when(pricingServiceClient.executePricing(anyMap())).thenReturn(ApiResponse.success(
                PricingExecuteView.builder().success(true).lineResults(List.of(
                        PricingLineResultView.builder().productExternalId("PROD-001").lineItemType("SUBSCRIPTION").build()
                )).build()));
        when(quoteRepository.save(org.mockito.ArgumentMatchers.any(Quote.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(quoteMapper.toDto(org.mockito.ArgumentMatchers.any(Quote.class)))
                .thenReturn(QuoteDto.builder().id(1L).build());

        assertThat(CrmQuoteDealTypeMapping.toQuoteDealType("NEW_BUSINESS")).isEqualTo(DealType.NEW);
        assertThat(CrmQuoteDealTypeMapping.toQuoteDealType("EXISTING_BUSINESS")).isEqualTo(DealType.EXPANSION);
        assertThat(CrmQuoteDealTypeMapping.toQuoteDealType("RENEWAL")).isEqualTo(DealType.RENEWAL);
        assertThat(CrmQuoteDealTypeMapping.toQuoteDealType("UPSELL")).isEqualTo(DealType.EXPANSION);
        assertThat(CrmQuoteDealTypeMapping.toQuoteDealType("CROSS_SELL")).isEqualTo(DealType.EXPANSION);

        for (String crmName : List.of("NEW_BUSINESS", "EXISTING_BUSINESS", "RENEWAL", "UPSELL", "CROSS_SELL")) {
            DealType dealType = CrmQuoteDealTypeMapping.toQuoteDealType(crmName);
            Quote quote = draftQuote();
            quote.setDealType(dealType);
            quote.setTargetSubscriptionNumber("SUB-1");
            when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(quote));
            when(quoteLineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(1L))
                    .thenReturn(List.of(lineItem(quote, "PROD-001", "SUBSCRIPTION", 1, BigDecimal.TEN)));
            org.mockito.Mockito.clearInvocations(pricingServiceClient);
            quoteService.calculate(1L);
            Map<String, Object> sent = sentPricingContext();
            assertThat(sent.get("metadata")).isEqualTo(Map.of("dealType", dealType.name()));
            assertThat(sent).doesNotContainKey("churnScore");
            assertThat(dealType.name()).isNotIn("NEW_BUSINESS", "EXISTING_BUSINESS", "UPSELL", "CROSS_SELL", "EXTENSION");
        }

        Quote amendment = draftQuote();
        amendment.setDealType(DealType.AMENDMENT);
        amendment.setTargetSubscriptionNumber("SUB-1");
        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(amendment));
        when(quoteLineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(1L))
                .thenReturn(List.of(lineItem(amendment, "PROD-001", "SUBSCRIPTION", 1, BigDecimal.TEN)));
        org.mockito.Mockito.clearInvocations(pricingServiceClient);
        quoteService.calculate(1L);
        assertThat(sentPricingContext().get("metadata")).isEqualTo(Map.of("dealType", "AMENDMENT"));

        Quote quote = draftQuote();
        quote.setDealType(null);
        quote.setTargetSubscriptionNumber("SUB-1");
        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(quote));
        when(quoteLineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(1L))
                .thenReturn(List.of(lineItem(quote, "PROD-001", "SUBSCRIPTION", 1, BigDecimal.TEN)));
        org.mockito.Mockito.clearInvocations(pricingServiceClient);
        quoteService.calculate(1L);
        assertThat(sentPricingContext()).doesNotContainKey("metadata");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sentPricingContext() {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(pricingServiceClient).executePricing(captor.capture());
        return captor.getValue();
    }

    private static Quote draftQuote() {
        Quote quote = Quote.builder()
                .quoteNumber("Q-1")
                .name("Test")
                .customerExternalId("CUST-1")
                .status(QuoteStatus.DRAFT)
                .currency("USD")
                .region("NA")
                .durationInMonths(12)
                .build();
        quote.setId(1L);
        return quote;
    }

    @Test
    void acceptedQuoteCannotBeRepriced() {
        Quote quote = draftQuote();
        quote.setStatus(QuoteStatus.ACCEPTED);
        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(quote));
        assertThatThrownBy(() -> quoteService.calculate(1L)).isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode()).isEqualTo("QUOTE_NOT_EDITABLE");
        org.mockito.Mockito.verifyNoInteractions(pricingServiceClient, quoteLineItemRepository, outboxEventPublisher);
    }

    @Test
    void rampRepricingClearsStalePersistsPeriodsAndPublishesOnlyChangedBindings() {
        Quote quote = draftQuote(); quote.setDurationInMonths(36); quote.setRampScheduleStale(true);
        QuoteLineItem line = lineItem(quote, "PROD-SEAT", "SUBSCRIPTION", 50, new BigDecimal("50"));
        line.setProductType("SAAS_SEAT_NAMED");
        line.setId(5L); line.setBillingFrequency("MONTHLY"); line.setRampSteps(new ArrayList<>());
        line.getRampSteps().add(absoluteStep(line, 1, 1, "34"));
        line.getRampSteps().add(absoluteStep(line, 2, 13, "45"));
        line.getRampSteps().add(absoluteStep(line, 3, 25, "50"));
        when(quoteRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(quote));
        when(quoteLineItemRepository.findByQuoteIdAndDeletedFalseOrderBySortOrderAsc(1L)).thenReturn(List.of(line));
        when(quoteRepository.save(quote)).thenReturn(quote);
        when(quoteMapper.toDto(quote)).thenReturn(QuoteDto.builder().id(1L).build());
        PricingLineResultView priced = PricingLineResultView.builder().productExternalId("PROD-SEAT").lineItemType("SUBSCRIPTION")
                .quantity(50).listPrice(new BigDecimal("50")).unitPrice(new BigDecimal("34"))
                .finalPrice(new BigDecimal("1700")).discountRate(new BigDecimal("0.32"))
                .discountAmount(new BigDecimal("800"))
                .pricedPeriods(List.of(period(1, 1, "34", "1700", "20400"),
                        period(2, 13, "45", "2250", "27000"), period(3, 25, "50", "2500", "30000")))
                .metadata(Map.of("rampGovernanceEvaluated", true, "authoritativeProductType", "SAAS_SEAT_NAMED", "rampGovernance", Map.of("governingPeriod", 1, "discountPercent", 32,
                        "stepDown", false, "reasons", List.of("RAMP_DISCOUNT_THRESHOLD"), "approvalTier", "SALES_VP"))).build();
        PricingExecuteView response = PricingExecuteView.builder().success(true).approvalState("PENDING").approvalTier("SALES_VP")
                .lineResults(List.of(priced)).financialMetrics(PricingFinancialMetricsView.builder()
                        .tcv(new BigDecimal("77400")).acv(new BigDecimal("25800")).arr(new BigDecimal("20400"))
                        .exitArr(new BigDecimal("30000")).build()).build();
        when(pricingServiceClient.executePricing(anyMap())).thenReturn(ApiResponse.success(response));
        Map<String, Object> serverMetadata = priced.getMetadata();
        priced.setMetadata(Map.of());
        assertThatThrownBy(() -> quoteService.calculate(1L)).isInstanceOf(com.opencrm.quote.ramp.RampScheduleException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode()).isEqualTo("RAMP_NOT_SUPPORTED");
        org.mockito.Mockito.verify(quoteRepository, org.mockito.Mockito.never()).save(quote);
        assertThat(quote.isRampScheduleStale()).isTrue();
        priced.setMetadata(serverMetadata);
        quoteService.calculate(1L);
        String first = com.opencrm.quote.ramp.RampPricingSnapshot.fingerprint(quote);
        assertThat(quote.isRampScheduleStale()).isFalse();
        assertThat(com.opencrm.quote.ramp.RampPricingSnapshot.pricedPeriods(line)).hasSize(3);
        assertThat(com.opencrm.quote.ramp.RampPricingSnapshot.governance(quote).getGoverningPeriod()).isEqualTo(1);
        quoteService.calculate(1L);
        org.mockito.Mockito.verify(outboxEventPublisher, org.mockito.Mockito.times(1))
                .publish(org.mockito.ArgumentMatchers.eq("Quote"), org.mockito.ArgumentMatchers.eq("1"),
                        org.mockito.ArgumentMatchers.eq(com.opencrm.common.event.EventContractRegistry.RAMP_APPROVAL_BINDING_CHANGED_V1),
                        org.mockito.ArgumentMatchers.any(com.opencrm.common.event.dto.DiscountApprovalRequiredEvent.class));
        line.getRampSteps().get(1).setUnitPrice(new BigDecimal("40"));
        quote.setRampScheduleStale(true);
        quoteService.calculate(1L);
        assertThat(com.opencrm.quote.ramp.RampPricingSnapshot.fingerprint(quote)).isNotEqualTo(first);
        org.mockito.Mockito.verify(outboxEventPublisher, org.mockito.Mockito.times(2))
                .publish(org.mockito.ArgumentMatchers.eq("Quote"), org.mockito.ArgumentMatchers.eq("1"),
                        org.mockito.ArgumentMatchers.eq(com.opencrm.common.event.EventContractRegistry.RAMP_APPROVAL_BINDING_CHANGED_V1),
                        org.mockito.ArgumentMatchers.any(com.opencrm.common.event.dto.DiscountApprovalRequiredEvent.class));
        priced.setMetadata(Map.of("rampGovernanceEvaluated", true, "authoritativeProductType", "SAAS_SEAT_NAMED"));
        response.setApprovalState("APPROVED"); response.setApprovalTier(null);
        line.getRampSteps().getFirst().setUnitPrice(new BigDecimal("50"));
        line.getRampSteps().get(1).setUnitPrice(new BigDecimal("50"));
        quoteService.calculate(1L);
        var events = org.mockito.ArgumentCaptor.forClass(com.opencrm.common.event.dto.DiscountApprovalRequiredEvent.class);
        org.mockito.Mockito.verify(outboxEventPublisher, org.mockito.Mockito.times(3))
                .publish(org.mockito.ArgumentMatchers.eq("Quote"), org.mockito.ArgumentMatchers.eq("1"),
                        org.mockito.ArgumentMatchers.eq(com.opencrm.common.event.EventContractRegistry.RAMP_APPROVAL_BINDING_CHANGED_V1), events.capture());
        assertThat(events.getValue().getApprovalRequired()).isFalse();
        line.getRampSteps().clear();
        priced.setPricedPeriods(null);
        quoteService.calculate(1L);
        assertThat(line.getPricingWaterfallJson())
                .isEqualTo("{\"authoritativeProductType\":\"SAAS_SEAT_NAMED\"}");
        assertThat(com.opencrm.quote.ramp.RampPricingSnapshot.governance(quote)).isNull();
    }

    private static QuoteLineItem lineItem(
            Quote quote,
            String productExternalId,
            String lineItemType,
            int quantity,
            BigDecimal listPrice) {
        return QuoteLineItem.builder()
                .quote(quote)
                .productExternalId(productExternalId)
                .lineItemType(lineItemType)
                .quantity(quantity)
                .listPrice(listPrice)
                .build();
    }

    private static QuoteRampStep absoluteStep(
            QuoteLineItem line, int sequence, int startMonth, String unitPrice) {
        QuoteRampStep step = QuoteRampStep.builder()
                .lineItem(line)
                .sequence(sequence)
                .startMonth(startMonth)
                .months(12)
                .quantity(50)
                .priceBasis("ABSOLUTE")
                .unitPrice(new BigDecimal(unitPrice))
                .build();
        return step;
    }

    private static PricedPeriodView period(
            int sequence,
            int startMonth,
            String netUnitPrice,
            String netMonthly,
            String periodAmount) {
        return PricedPeriodView.builder()
                .sequence(sequence)
                .startMonth(startMonth)
                .months(12)
                .quantity(50)
                .netUnitPrice(new BigDecimal(netUnitPrice))
                .netMonthly(new BigDecimal(netMonthly))
                .periodAmount(new BigDecimal(periodAmount))
                .build();
    }
}
