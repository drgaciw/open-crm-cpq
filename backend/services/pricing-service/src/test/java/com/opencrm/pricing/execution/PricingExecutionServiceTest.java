package com.opencrm.pricing.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.pricing.customer.Customer;
import com.opencrm.pricing.customer.CustomerRepository;
import com.opencrm.pricing.pricebook.PriceBook;
import com.opencrm.pricing.pricebook.PriceBookEntry;
import com.opencrm.pricing.pricebook.PriceBookEntryRepository;
import com.opencrm.pricing.pricebook.PriceBookRepository;
import com.opencrm.pricing.product.PricingProduct;
import com.opencrm.pricing.product.PricingProductRepository;
import com.opencrm.pricing.rule.RuleRepository;
import com.opencrm.pricing.ruleset.RuleSet;
import com.opencrm.pricing.ruleset.RuleSetRepository;
import com.opencrm.pricing.customer.CustomerRegion;
import com.opencrm.pricing.waterfall.*;
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
import java.util.Optional;

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
    private PriceBookRepository priceBookRepository;
    @Mock
    private PriceBookEntryRepository priceBookEntryRepository;
    @Mock
    private PricingAuditLogRepository auditLogRepository;
    @Mock
    private PricingContextValidator contextValidator;
    @Mock
    private MetricsCalculator metricsCalculator;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private PricingExecutionService executionService;

    private PricingContext defaultContext;
    private Customer defaultCustomer;
    private PriceBook defaultPriceBook;
    private FinancialMetrics defaultMetrics;

    @Test
    @DisplayName("execute() should calculate pricing and persist audit log")
    void executeShouldCalculatePricingAndPersistAudit() throws Exception {
        // Arrange
        doNothing().when(contextValidator).validate(any(PricingContext.class));
        when(customerRepository.findByExternalIdAndDeletedFalse("CUST-001"))
                .thenReturn(Optional.of(defaultCustomer));
        when(ruleSetRepository.findByStatusAndEffectiveDateLessThanEqualAndDeletedFalse(any(), any()))
                .thenReturn(new ArrayList<>());
        when(priceBookRepository.findByRegionAndDeletedFalse("NA"))
                .thenReturn(Optional.of(defaultPriceBook));

        PriceBookEntry pbEntry = PriceBookEntry.builder()
                .productExternalId("PROD-001")
                .listPrice(BigDecimal.valueOf(100.0))
                .build();
        when(priceBookEntryRepository.findByPriceBookIdAndProductExternalIdAndDeletedFalse(
                1L, "PROD-001")).thenReturn(Optional.of(pbEntry));

        when(metricsCalculator.calculate(any(PricingResult.class), eq(12)))
                .thenReturn(defaultMetrics);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        // Act
        PricingResult result = executionService.execute(defaultContext);

        // Assert
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertEquals("APPROVED", result.getApprovalState());
        assertEquals(1, result.getLineResults().size());

        PricingLineResult lineResult = result.getLineResults().get(0);
        assertEquals("PROD-001", lineResult.getProductExternalId());
        assertEquals(BigDecimal.valueOf(100.0), lineResult.getListPrice());
        assertEquals(BigDecimal.valueOf(500.0), lineResult.getFinalPrice()); // 100 * 5 quantity

        assertNotNull(result.getFinancialMetrics());
        assertNotNull(result.getWaterfall());

        verify(auditLogRepository).save(any(PricingAuditLog.class));
    }

    @Test
    @DisplayName("simulate() should calculate pricing without persisting audit log")
    void simulateShouldCalculatePricingWithoutAudit() {
        // Arrange
        doNothing().when(contextValidator).validate(any(PricingContext.class));
        when(customerRepository.findByExternalIdAndDeletedFalse("CUST-001"))
                .thenReturn(Optional.of(defaultCustomer));
        when(ruleSetRepository.findByStatusAndEffectiveDateLessThanEqualAndDeletedFalse(any(), any()))
                .thenReturn(new ArrayList<>());
        when(priceBookRepository.findByRegionAndDeletedFalse("NA"))
                .thenReturn(Optional.of(defaultPriceBook));

        PriceBookEntry pbEntry = PriceBookEntry.builder()
                .productExternalId("PROD-001")
                .listPrice(BigDecimal.valueOf(100.0))
                .build();
        when(priceBookEntryRepository.findByPriceBookIdAndProductExternalIdAndDeletedFalse(
                1L, "PROD-001")).thenReturn(Optional.of(pbEntry));

        when(metricsCalculator.calculate(any(PricingResult.class), eq(12)))
                .thenReturn(defaultMetrics);

        // Act
        PricingResult result = executionService.simulate(defaultContext);

        // Assert
        assertNotNull(result);
        assertTrue(result.isSuccess());
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("execute() should throw ResourceNotFoundException when customer not found")
    void executeShouldThrowWhenCustomerNotFound() {
        // Arrange
        doNothing().when(contextValidator).validate(any(PricingContext.class));
        when(customerRepository.findByExternalIdAndDeletedFalse("CUST-001"))
                .thenReturn(Optional.empty());

        // Act & Assert
        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> {
            executionService.execute(defaultContext);
        });
        assertTrue(ex.getMessage().contains("Customer with external ID"));
    }

    @Test
    @DisplayName("execute() should throw BusinessException when no price book found")
    void executeShouldThrowWhenNoPriceBookFound() {
        // Arrange
        doNothing().when(contextValidator).validate(any(PricingContext.class));
        when(customerRepository.findByExternalIdAndDeletedFalse("CUST-001"))
                .thenReturn(Optional.of(defaultCustomer));
        when(ruleSetRepository.findByStatusAndEffectiveDateLessThanEqualAndDeletedFalse(any(), any()))
                .thenReturn(new ArrayList<>());

        when(priceBookRepository.findByRegionAndDeletedFalse("NA"))
                .thenReturn(Optional.empty());
        when(priceBookRepository.findByIsDefaultTrueAndDeletedFalse())
                .thenReturn(Optional.empty());

        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class, () -> {
            executionService.execute(defaultContext);
        });
        assertEquals("NO_PRICE_BOOK", ex.getErrorCode());
    }

    @Test
    @DisplayName("simulateBulk() should calculate pricing for multiple contexts")
    void simulateBulkShouldCalculateMultipleContexts() {
        // Arrange
        List<PricingContext> contexts = List.of(defaultContext, defaultContext);

        doNothing().when(contextValidator).validate(any(PricingContext.class));
        when(customerRepository.findByExternalIdAndDeletedFalse("CUST-001"))
                .thenReturn(Optional.of(defaultCustomer));
        when(ruleSetRepository.findByStatusAndEffectiveDateLessThanEqualAndDeletedFalse(any(), any()))
                .thenReturn(new ArrayList<>());
        when(priceBookRepository.findByRegionAndDeletedFalse("NA"))
                .thenReturn(Optional.of(defaultPriceBook));

        PriceBookEntry pbEntry = PriceBookEntry.builder()
                .productExternalId("PROD-001")
                .listPrice(BigDecimal.valueOf(100.0))
                .build();
        when(priceBookEntryRepository.findByPriceBookIdAndProductExternalIdAndDeletedFalse(
                1L, "PROD-001")).thenReturn(Optional.of(pbEntry));

        when(metricsCalculator.calculate(any(PricingResult.class), eq(12)))
                .thenReturn(defaultMetrics);

        // Act
        BulkPricingSimulationResponse response = executionService.simulateBulk(contexts);

        // Assert
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
        // Act & Assert
        BusinessException ex = assertThrows(BusinessException.class, () -> {
            executionService.simulateBulk(new ArrayList<>());
        });
        assertEquals("INVALID_CONTEXT", ex.getErrorCode());
    }

    @Test
    @DisplayName("getAuditTrail() should return audit logs")
    void getAuditTrailShouldReturnLogs() {
        // Arrange
        String quoteId = "Q-001";
        PricingAuditLog log = PricingAuditLog.builder().quoteExternalId(quoteId).build();
        when(auditLogRepository.findByQuoteExternalIdOrderByExecutedAtDesc(quoteId))
                .thenReturn(List.of(log));

        // Act
        List<PricingAuditLog> result = executionService.getAuditTrail(quoteId);

        // Assert
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

        defaultPriceBook = PriceBook.builder()
                .id(1L)
                .name("NA Standard")
                .region("NA")
                .isDefault(false)
                .build();

        defaultMetrics = FinancialMetrics.builder()
                .arr(BigDecimal.valueOf(6000))
                .tcv(BigDecimal.valueOf(6000))
                .acv(BigDecimal.valueOf(6000))
                .clv(BigDecimal.valueOf(12000))
                .build();
    }

}