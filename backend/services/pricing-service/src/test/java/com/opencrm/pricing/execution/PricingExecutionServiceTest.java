package com.opencrm.pricing.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.common.event.KafkaTopics;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.DiscountApprovalRequiredEvent;
import com.opencrm.common.event.dto.PriceCalculatedEvent;
import com.opencrm.pricing.customer.Customer;
import com.opencrm.pricing.customer.CustomerRepository;
import com.opencrm.pricing.integration.pricebook.PriceBookResolutionClient;
import com.opencrm.pricing.integration.pricebook.ProductEntryResolveDto;
import com.opencrm.pricing.integration.pricebook.ResolveBatchResponse;
import com.opencrm.pricing.product.PricingProductRepository;
import com.opencrm.pricing.rule.RuleRepository;
import com.opencrm.pricing.ruleset.RuleSet;
import com.opencrm.pricing.ruleset.RuleSetRepository;
import com.opencrm.pricing.customer.CustomerRegion;
import com.opencrm.pricing.waterfall.*;
import com.opencrm.rules.drools.DroolsRulesEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;
import org.mockito.Spy;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pricing Execution Service")
class PricingExecutionServiceTest {

    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private PricingProductRepository pricingProductRepository;
    @Mock
    private RuleSetRepository ruleSetRepository;
    @Mock
    private RuleRepository ruleRepository;
    @Mock
    private PriceBookResolutionClient priceBookResolutionClient;
    @Mock
    private PricingAuditLogRepository auditLogRepository;
    @Mock
    private PricingContextValidator contextValidator;
    @Mock
    private MetricsCalculator metricsCalculator;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    @Mock
    private DroolsRulesEngine droolsRulesEngine;
    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    @InjectMocks
    private PricingExecutionService executionService;

    private PricingContext defaultContext;
    private Customer defaultCustomer;
    private FinancialMetrics defaultMetrics;

    private static ResolveBatchResponse batchWithEntry(BigDecimal listPrice) {
        return ResolveBatchResponse.builder()
                .priceBookId(1L)
                .priceBookCurrency("USD")
                .entries(Map.of(
                        "PROD-001",
                        ProductEntryResolveDto.builder()
                                .entryMatched(true)
                                .listPrice(listPrice)
                                .currency("USD")
                                .build()))
                .build();
    }

    @Test
    @DisplayName("execute() should calculate pricing and persist audit log")
    void executeShouldCalculatePricingAndPersistAudit() throws Exception {
        doNothing().when(contextValidator).validate(any(PricingContext.class));
        when(customerRepository.findByExternalIdAndDeletedFalse("CUST-001"))
                .thenReturn(java.util.Optional.of(defaultCustomer));
        when(ruleSetRepository.findByStatusAndEffectiveDateLessThanEqualAndDeletedFalse(any(), any()))
                .thenReturn(new ArrayList<>());

        when(priceBookResolutionClient.resolveBatch(eq("NA"), eq(List.of("PROD-001"))))
                .thenReturn(batchWithEntry(BigDecimal.valueOf(100.0)));

        when(metricsCalculator.calculate(any(PricingResult.class), eq(12)))
                .thenReturn(defaultMetrics);

        PricingResult result = executionService.execute(defaultContext);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("APPROVED", result.getApprovalState());
        assertEquals(1, result.getLineResults().size());

        PricingLineResult lineResult = result.getLineResults().get(0);
        assertEquals("PROD-001", lineResult.getProductExternalId());
        assertEquals(BigDecimal.valueOf(100.0), lineResult.getListPrice());
        assertEquals(BigDecimal.valueOf(500.0), lineResult.getFinalPrice());

        assertNotNull(result.getFinancialMetrics());
        assertNotNull(result.getWaterfall());

        verify(auditLogRepository).save(any(PricingAuditLog.class));
    }

    @Test
    @DisplayName("execute() should publish PriceCalculated with explanation codes and deterministic snapshots")
    void executeShouldPublishPriceCalculatedEvent() throws Exception {
        doNothing().when(contextValidator).validate(any(PricingContext.class));
        when(customerRepository.findByExternalIdAndDeletedFalse("CUST-001"))
                .thenReturn(java.util.Optional.of(defaultCustomer));
        when(ruleSetRepository.findByStatusAndEffectiveDateLessThanEqualAndDeletedFalse(any(), any()))
                .thenReturn(new ArrayList<>());
        when(priceBookResolutionClient.resolveBatch(eq("NA"), eq(List.of("PROD-001"))))
                .thenReturn(batchWithEntry(BigDecimal.valueOf(100.0)));
        when(metricsCalculator.calculate(any(PricingResult.class), eq(12)))
                .thenReturn(defaultMetrics);

        executionService.execute(defaultContext);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxEventPublisher).publish(
                eq("PricingExecution"),
                eq("Q-001"),
                eq(KafkaTopics.PRICE_CALCULATED),
                payloadCaptor.capture()
        );

        PriceCalculatedEvent event = (PriceCalculatedEvent) payloadCaptor.getValue();
        assertEquals("Q-001", event.getQuoteId());
        assertEquals("CUST-001", event.getCustomerId());
        assertEquals(List.of("LIST_PRICE_APPLIED"), event.getExplanationCodes());
        assertTrue(event.getInputSnapshot().contains("\"customerExternalId\":\"CUST-001\""));
        assertTrue(event.getOutputSnapshot().contains("\"explanationCodes\":[\"LIST_PRICE_APPLIED\"]"));
    }

    @Test
    @DisplayName("execute() should publish DiscountApprovalRequired when discount exceeds threshold")
    void executeShouldPublishDiscountApprovalRequiredWhenThresholdExceeded() throws Exception {
        doNothing().when(contextValidator).validate(any(PricingContext.class));
        when(customerRepository.findByExternalIdAndDeletedFalse("CUST-001"))
                .thenReturn(java.util.Optional.of(defaultCustomer));
        when(ruleSetRepository.findByStatusAndEffectiveDateLessThanEqualAndDeletedFalse(any(), any()))
                .thenReturn(new ArrayList<>());
        when(priceBookResolutionClient.resolveBatch(eq("NA"), eq(List.of("PROD-001"))))
                .thenReturn(batchWithEntry(BigDecimal.valueOf(100.0)));
        when(metricsCalculator.calculate(any(PricingResult.class), eq(12)))
                .thenReturn(defaultMetrics);
        defaultContext.getLineItems().get(0).getMetadata().put("discountRate", BigDecimal.valueOf(25));

        executionService.execute(defaultContext);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxEventPublisher).publish(
                eq("PricingExecution"),
                eq("Q-001"),
                eq(KafkaTopics.DISCOUNT_APPROVAL_REQUIRED),
                payloadCaptor.capture()
        );

        DiscountApprovalRequiredEvent event = (DiscountApprovalRequiredEvent) payloadCaptor.getValue();
        assertEquals("Q-001", event.getQuoteId());
        assertEquals("PROD-001", event.getLineId());
        assertEquals(BigDecimal.valueOf(25), event.getMarginDelta());
        assertEquals(0, BigDecimal.valueOf(125).compareTo(event.getRequestedDiscount()));
        assertEquals(List.of("LIST_PRICE_APPLIED", "DISCOUNT_APPROVAL_REQUIRED"), event.getExplanationCodes());
        assertTrue(event.getInputSnapshot().contains("\"discountRate\":25"));
        assertTrue(event.getOutputSnapshot().contains("\"DISCOUNT_APPROVAL_REQUIRED\""));
    }

    @Test
    @DisplayName("simulate() should calculate pricing without persisting audit log")
    void simulateShouldCalculatePricingWithoutAudit() {
        doNothing().when(contextValidator).validate(any(PricingContext.class));
        when(customerRepository.findByExternalIdAndDeletedFalse("CUST-001"))
                .thenReturn(java.util.Optional.of(defaultCustomer));
        when(ruleSetRepository.findByStatusAndEffectiveDateLessThanEqualAndDeletedFalse(any(), any()))
                .thenReturn(new ArrayList<>());

        when(priceBookResolutionClient.resolveBatch(eq("NA"), eq(List.of("PROD-001"))))
                .thenReturn(batchWithEntry(BigDecimal.valueOf(100.0)));

        when(metricsCalculator.calculate(any(PricingResult.class), eq(12)))
                .thenReturn(defaultMetrics);

        PricingResult result = executionService.simulate(defaultContext);

        assertNotNull(result);
        assertTrue(result.isSuccess());
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("execute() should throw ResourceNotFoundException when customer not found")
    void executeShouldThrowWhenCustomerNotFound() {
        doNothing().when(contextValidator).validate(any(PricingContext.class));
        when(customerRepository.findByExternalIdAndDeletedFalse("CUST-001"))
                .thenReturn(java.util.Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> {
            executionService.execute(defaultContext);
        });
        assertTrue(ex.getMessage().contains("Customer with external ID"));
    }

    @Test
    @DisplayName("execute() should throw BusinessException when no price book found")
    void executeShouldThrowWhenNoPriceBookFound() {
        doNothing().when(contextValidator).validate(any(PricingContext.class));
        when(customerRepository.findByExternalIdAndDeletedFalse("CUST-001"))
                .thenReturn(java.util.Optional.of(defaultCustomer));
        when(ruleSetRepository.findByStatusAndEffectiveDateLessThanEqualAndDeletedFalse(any(), any()))
                .thenReturn(new ArrayList<>());

        when(priceBookResolutionClient.resolveBatch(eq("NA"), eq(List.of("PROD-001"))))
                .thenThrow(new BusinessException("No price book found", "NO_PRICE_BOOK"));

        BusinessException ex = assertThrows(BusinessException.class, () -> {
            executionService.execute(defaultContext);
        });
        assertEquals("NO_PRICE_BOOK", ex.getErrorCode());
    }

    @Test
    @DisplayName("simulateBulk() should calculate pricing for multiple contexts")
    void simulateBulkShouldCalculateMultipleContexts() {
        List<PricingContext> contexts = List.of(defaultContext, defaultContext);

        doNothing().when(contextValidator).validate(any(PricingContext.class));
        when(customerRepository.findByExternalIdAndDeletedFalse("CUST-001"))
                .thenReturn(java.util.Optional.of(defaultCustomer));
        when(ruleSetRepository.findByStatusAndEffectiveDateLessThanEqualAndDeletedFalse(any(), any()))
                .thenReturn(new ArrayList<>());

        when(priceBookResolutionClient.resolveBatch(eq("NA"), eq(List.of("PROD-001"))))
                .thenReturn(batchWithEntry(BigDecimal.valueOf(100.0)));

        when(metricsCalculator.calculate(any(PricingResult.class), eq(12)))
                .thenReturn(defaultMetrics);

        BulkPricingSimulationResponse response = executionService.simulateBulk(contexts);

        assertNotNull(response);
        assertEquals(2, response.getTotalRequests());
        assertEquals(2, response.getSuccessfulRequests());
        assertEquals(0, response.getFailedRequests());
        assertEquals(2, response.getResults().size());
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("simulateBulk() should throw when empty list provided")
    void simulateBulkShouldThrowWhenEmpty() {
        BusinessException ex = assertThrows(BusinessException.class, () -> {
            executionService.simulateBulk(new ArrayList<>());
        });
        assertEquals("INVALID_CONTEXT", ex.getErrorCode());
    }

    @Test
    @DisplayName("getAuditTrail() should return audit logs")
    void getAuditTrailShouldReturnLogs() {
        String quoteId = "Q-001";
        PricingAuditLog log = PricingAuditLog.builder().quoteExternalId(quoteId).build();
        when(auditLogRepository.findByQuoteExternalIdOrderByExecutedAtDesc(quoteId))
                .thenReturn(List.of(log));

        List<PricingAuditLog> result = executionService.getAuditTrail(quoteId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(quoteId, result.get(0).getQuoteExternalId());
    }

    @BeforeEach
    void setUp() {
        defaultContext = PricingContext.builder()
                .customerExternalId("CUST-001")
                .quoteExternalId("Q-001")
                .region("NA")
                .durationInMonths(12)
                .lineItems(List.of(
                        PricingLineRequest.builder()
                                .productExternalId("PROD-001")
                                .quantity(5)
                                .lineItemType("SUBSCRIPTION")
                                .metadata(new java.util.HashMap<>())
                                .build()
                ))
                .build();

        defaultCustomer = Customer.builder()
                .externalId("CUST-001")
                .name("Acme Corp")
                .region(CustomerRegion.NA)
                .build();

        defaultMetrics = FinancialMetrics.builder()
                .arr(BigDecimal.valueOf(6000))
                .tcv(BigDecimal.valueOf(6000))
                .acv(BigDecimal.valueOf(6000))
                .clv(BigDecimal.valueOf(12000))
                .build();
    }
}
