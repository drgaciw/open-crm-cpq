package com.opencrm.pricing.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.pricing.customer.Customer;
import com.opencrm.pricing.customer.CustomerRepository;
import com.opencrm.pricing.integration.pricebook.PriceBookResolutionClient;
import com.opencrm.pricing.integration.pricebook.ProductEntryResolveDto;
import com.opencrm.pricing.integration.pricebook.ResolveBatchResponse;
import com.opencrm.pricing.product.PricingProductRepository;
import com.opencrm.pricing.rule.Rule;
import com.opencrm.pricing.rule.RuleRepository;
import com.opencrm.pricing.rule.RuleStatus;
import com.opencrm.pricing.rule.RuleType;
import com.opencrm.pricing.ruleset.RuleSet;
import com.opencrm.pricing.ruleset.RuleSetRepository;
import com.opencrm.pricing.customer.CustomerRegion;
import com.opencrm.pricing.ruleset.RuleSetStatus;
import com.opencrm.pricing.waterfall.*;
import com.opencrm.rules.drools.DroolsRulesEngine;
import com.opencrm.rules.engine.RuleDefinition;
import com.opencrm.rules.engine.RuleExecutionContext;
import com.opencrm.rules.engine.RuleExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private DroolsRulesEngine droolsRulesEngine;

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
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

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
    @DisplayName("simulate() should pass OpenL pricing outputs before Drools complex rules by priority")
    void simulateShouldExecuteOpenLPricingOutputsBeforeDroolsComplexRulesByPriority() {
        RuleSet activeRuleSet = activeRuleSet();
        Rule openLPricingRule = activeRule(
                "OpenL table discount",
                "OPENL_PRICE_TABLE",
                720,
                "OPENL_TABLE_DISCOUNT");
        Rule droolsComplexRule = activeRule(
                "Drools margin guardrail",
                "DROOLS_MARGIN_GUARDRAIL",
                610,
                "DROOLS_MARGIN_GUARDRAIL");

        doNothing().when(contextValidator).validate(any(PricingContext.class));
        when(customerRepository.findByExternalIdAndDeletedFalse("CUST-001"))
                .thenReturn(java.util.Optional.of(defaultCustomer));
        when(ruleSetRepository.findByStatusAndEffectiveDateLessThanEqualAndDeletedFalse(any(), any()))
                .thenReturn(List.of(activeRuleSet));
        when(ruleRepository.findByRuleSetIdAndStatusAndDeletedFalseOrderByPriorityDesc(1L, RuleStatus.ACTIVE))
                .thenReturn(List.of(openLPricingRule, droolsComplexRule));
        when(priceBookResolutionClient.resolveBatch(eq("NA"), eq(List.of("PROD-001"))))
                .thenReturn(batchWithEntry(new BigDecimal("100.00")));
        when(metricsCalculator.calculate(any(PricingResult.class), eq(12)))
                .thenReturn(defaultMetrics);
        when(droolsRulesEngine.execute(any(RuleExecutionContext.class), any()))
                .thenReturn(RuleExecutionResult.builder().success(true).rulesFired(2).build());

        executionService.simulate(defaultContext);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RuleDefinition>> rulesCaptor = ArgumentCaptor.forClass(List.class);
        verify(droolsRulesEngine).execute(any(RuleExecutionContext.class), rulesCaptor.capture());
        List<RuleDefinition> ruleDefinitions = rulesCaptor.getValue();

        assertEquals("OpenL table discount", ruleDefinitions.get(0).getName());
        assertEquals(720, ruleDefinitions.get(0).getPriority());
        assertEquals("OPENL_PRICE_TABLE", ruleDefinitions.get(0).getContent());
        assertEquals("OPENL_TABLE_DISCOUNT", ruleDefinitions.get(0).getExplanationCode());
        assertEquals("Drools margin guardrail", ruleDefinitions.get(1).getName());
        assertEquals(610, ruleDefinitions.get(1).getPriority());
        assertEquals("DROOLS_MARGIN_GUARDRAIL", ruleDefinitions.get(1).getContent());
    }

    @Test
    @DisplayName("simulate() should reconcile OpenL discounts and Drools outputs without double-discounting")
    void simulateShouldReconcileOpenLDiscountAndDroolsOutputWithoutDoubleDiscounting() {
        RuleSet activeRuleSet = activeRuleSet();

        doNothing().when(contextValidator).validate(any(PricingContext.class));
        when(customerRepository.findByExternalIdAndDeletedFalse("CUST-001"))
                .thenReturn(java.util.Optional.of(defaultCustomer));
        when(ruleSetRepository.findByStatusAndEffectiveDateLessThanEqualAndDeletedFalse(any(), any()))
                .thenReturn(List.of(activeRuleSet));
        when(ruleRepository.findByRuleSetIdAndStatusAndDeletedFalseOrderByPriorityDesc(1L, RuleStatus.ACTIVE))
                .thenReturn(List.of(
                        activeRule("OpenL table discount", "OPENL_PRICE_TABLE", 720, "OPENL_TABLE_DISCOUNT"),
                        activeRule("Drools complex surcharge", "DROOLS_COMPLEX_SURCHARGE", 610, "DROOLS_COMPLEX_SURCHARGE")));
        when(priceBookResolutionClient.resolveBatch(eq("NA"), eq(List.of("PROD-001"))))
                .thenReturn(batchWithEntry(new BigDecimal("100.00")));
        when(metricsCalculator.calculate(any(PricingResult.class), eq(12)))
                .thenReturn(defaultMetrics);
        when(droolsRulesEngine.execute(any(RuleExecutionContext.class), any()))
                .thenAnswer(invocation -> {
                    RuleExecutionContext ruleContext = invocation.getArgument(0);
                    PricingLineResult line = ruleContext.getFacts().stream()
                            .filter(PricingLineResult.class::isInstance)
                            .map(PricingLineResult.class::cast)
                            .findFirst()
                            .orElseThrow();

                    line.setDiscountRate(new BigDecimal("0.10"));
                    line.setDiscountAmount(new BigDecimal("50.00"));
                    line.setSurchargeAmount(new BigDecimal("25.00"));
                    line.setFinalPrice(new BigDecimal("475.00"));
                    line.getExplanationCodes().add("OPENL_TABLE_DISCOUNT");
                    line.getExplanationCodes().add("DROOLS_COMPLEX_SURCHARGE");

                    return RuleExecutionResult.builder().success(true).rulesFired(2).build();
                });

        PricingResult result = executionService.simulate(defaultContext);

        PricingLineResult lineResult = result.getLineResults().get(0);
        assertEquals(new BigDecimal("500.00"), lineResult.getListPrice().multiply(BigDecimal.valueOf(5)));
        assertEquals(new BigDecimal("0.10"), lineResult.getDiscountRate());
        assertEquals(new BigDecimal("50.00"), lineResult.getDiscountAmount());
        assertEquals(new BigDecimal("25.00"), lineResult.getSurchargeAmount());
        assertEquals(new BigDecimal("475.00"), lineResult.getFinalPrice());
        assertTrue(lineResult.getExplanationCodes().contains("OPENL_TABLE_DISCOUNT"));
        assertTrue(lineResult.getExplanationCodes().contains("DROOLS_COMPLEX_SURCHARGE"));
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

    private static RuleSet activeRuleSet() {
        return RuleSet.builder()
                .id(1L)
                .name("Hybrid pricing rules")
                .status(RuleSetStatus.ACTIVE)
                .effectiveDate(LocalDate.now().minusDays(1))
                .engineType("DROOLS")
                .build();
    }

    private static Rule activeRule(String name, String content, int priority, String explanationCode) {
        return Rule.builder()
                .id((long) priority)
                .ruleSetId(1L)
                .name(name)
                .description(name)
                .ruleType(RuleType.PRICING)
                .priority(priority)
                .status(RuleStatus.ACTIVE)
                .content(content)
                .explanationCode(explanationCode)
                .conflictPolicy("HIGHEST_PRIORITY_THEN_IMPACT")
                .build();
    }
}
