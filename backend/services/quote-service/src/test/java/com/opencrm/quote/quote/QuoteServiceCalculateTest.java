package com.opencrm.quote.quote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.dto.ApiResponse;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.security.CurrentActor;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.quote.client.PricingExecuteView;
import com.opencrm.quote.client.PricingFinancialMetricsView;
import com.opencrm.quote.client.PricingLineResultView;
import com.opencrm.quote.client.PricingServiceClient;
import com.opencrm.quote.client.ProductServiceClient;
import com.opencrm.quote.event.QuoteEventPublisher;
import com.opencrm.quote.lineitem.QuoteLineItem;
import com.opencrm.quote.lineitem.QuoteLineItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
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
                RampScheduleTestSupport.guard(quoteLineItemRepository)
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
                        .build())
                .waterfall(objectMapper.readTree("""
                        {"listPrice":100.00,"netPrice":450.00,"approvalState":"APPROVED"}
                        """))
                .build();

        when(pricingServiceClient.executePricing(anyMap()))
                .thenReturn(ApiResponse.success(pricingData, "ok"));
        when(quoteRepository.save(quote)).thenReturn(quote);
        when(quoteMapper.toDto(quote)).thenReturn(QuoteDto.builder().id(1L).quoteNumber("Q-1").build());

        quoteService.calculate(1L);

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
}
