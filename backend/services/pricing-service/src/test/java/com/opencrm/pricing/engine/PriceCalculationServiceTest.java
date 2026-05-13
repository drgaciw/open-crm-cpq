package com.opencrm.pricing.engine;

import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.DiscountApprovalRequiredEvent;
import com.opencrm.common.valueobject.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * JUnit 5 tests for Pricing Engine calculation service.
 * Validates Money value objects, HALF_UP rounding, and discount approval thresholds.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Pricing Engine - Price Calculation")
class PriceCalculationServiceTest {

    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    @InjectMocks
    private PriceCalculationService priceCalculationService;

    @Test
    @DisplayName("should calculate base price using Money value objects")
    void calculateBasePrice() {
        Money result = priceCalculationService.calculateBasePrice("PROD-001", 5, "USD");

        assertNotNull(result);
        assertEquals("USD", result.getCurrencyCode());
        assertEquals(new BigDecimal("500.00"), result.getAmount());
    }

    @Test
    @DisplayName("should calculate discount with Money value objects")
    void calculateDiscount() {
        Money basePrice = Money.usd(BigDecimal.valueOf(500.00));
        Money discount = priceCalculationService.calculateDiscount(
                "PROD-001", basePrice, "CUST-001", Map.of());

        assertNotNull(discount);
        assertTrue(discount.isPositive());
        assertEquals("USD", discount.getCurrencyCode());
    }

    @Test
    @DisplayName("should not require approval for small discounts")
    void noApprovalForSmallDiscount() {
        Money basePrice = Money.usd(BigDecimal.valueOf(100.00));
        Money discount = Money.usd(BigDecimal.valueOf(10.00)); // 10%

        assertFalse(priceCalculationService.requiresApproval(discount, basePrice));
    }

    @Test
    @DisplayName("should require approval for large discounts exceeding threshold")
    void requireApprovalForLargeDiscount() {
        Money basePrice = Money.usd(BigDecimal.valueOf(100.00));
        Money discount = Money.usd(BigDecimal.valueOf(25.00)); // 25% > 20% threshold

        assertTrue(priceCalculationService.requiresApproval(discount, basePrice));
    }

    @Test
    @DisplayName("should not require approval when base price is zero")
    void noApprovalForZeroBase() {
        Money basePrice = Money.zero("USD");
        Money discount = Money.usd(BigDecimal.valueOf(10.00));

        assertFalse(priceCalculationService.requiresApproval(discount, basePrice));
    }

    @Test
    @DisplayName("should publish discount approval event via outbox")
    void publishDiscountApprovalEvent() {
        Money discount = Money.usd(BigDecimal.valueOf(30.00));
        Money basePrice = Money.usd(BigDecimal.valueOf(100.00));

        priceCalculationService.publishDiscountApprovalRequired("Q-001", "L-001", discount, basePrice);

        verify(outboxEventPublisher).publish(
                eq("PriceCalculation"),
                eq("Q-001"),
                eq("cpq.discount.approval.required"),
                any(DiscountApprovalRequiredEvent.class)
        );
    }

    @Test
    @DisplayName("should include explanation code on approval event")
    void publishDiscountApprovalEventWithExplanationCode() {
        Money discount = Money.usd(BigDecimal.valueOf(30.00));
        Money basePrice = Money.usd(BigDecimal.valueOf(100.00));

        priceCalculationService.publishDiscountApprovalRequired("Q-001", "L-001", discount, basePrice);

        ArgumentCaptor<DiscountApprovalRequiredEvent> captor =
                ArgumentCaptor.forClass(DiscountApprovalRequiredEvent.class);
        verify(outboxEventPublisher).publish(
                eq("PriceCalculation"),
                eq("Q-001"),
                eq("cpq.discount.approval.required"),
                captor.capture()
        );

        DiscountApprovalRequiredEvent event = captor.getValue();
        assertEquals("DISCOUNT_APPROVAL_REQUIRED", event.getExplanationCodes().get(0));
        assertEquals(new BigDecimal("30.0000"), event.getMarginDelta());
    }
}
