package com.opencrm.pricing.engine;

import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.DiscountApprovalRequiredEvent;
import com.opencrm.common.valueobject.Money;
import com.opencrm.common.valueobject.Percentage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Price calculation service using Money value objects and HALF_UP rounding.
 * Publishes discount approval events when thresholds are exceeded.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PriceCalculationService {
    
    private final OutboxEventPublisher outboxEventPublisher;
    
    private static final Percentage APPROVAL_THRESHOLD = Percentage.of(20.0); // 20% discount threshold
    
    /**
     * Calculate base price for a product.
     * Uses Money value object to avoid floating-point precision issues.
     */
    public Money calculateBasePrice(String productId, int quantity, String currencyCode) {
        // In production, this would fetch from product catalog
        // For now, using sample data
        Money unitPrice = Money.of(BigDecimal.valueOf(100.00), currencyCode);
        return unitPrice.multiply(quantity);
    }
    
    /**
     * Calculate discount based on customer, product, and configuration.
     */
    public Money calculateDiscount(String productId, Money basePrice, String customerId, Map<String, String> configuration) {
        // Sample discount logic - in production, this would use rules engine
        Percentage discountPercentage = Percentage.of(10.0); // 10% discount
        
        // Volume discount
        if (basePrice.getAmount().compareTo(BigDecimal.valueOf(1000)) > 0) {
            discountPercentage = Percentage.of(15.0);
        }
        
        return basePrice.percentage(discountPercentage.getValue());
    }
    
    /**
     * Check if discount requires approval based on margin impact.
     */
    public boolean requiresApproval(Money discount, Money basePrice) {
        if (basePrice.isZero()) {
            return false;
        }
        
        // Calculate discount percentage
        BigDecimal discountPct = discount.getAmount()
                .divide(basePrice.getAmount(), 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        
        Percentage actualDiscount = Percentage.of(discountPct);
        
        return actualDiscount.compareTo(APPROVAL_THRESHOLD) > 0;
    }
    
    /**
     * Publish discount approval required event.
     */
    @Transactional
    public void publishDiscountApprovalRequired(String quoteId, String lineId, Money discount, Money basePrice) {
        BigDecimal marginDelta = discount.getAmount()
                .divide(basePrice.getAmount(), 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        
        DiscountApprovalRequiredEvent event = new DiscountApprovalRequiredEvent(
                quoteId,
                lineId,
                marginDelta,
                discount.getAmount(),
                discount.getCurrencyCode()
        );
        
        outboxEventPublisher.publish(
                "PriceCalculation",
                quoteId,
                EventContractRegistry.DISCOUNT_APPROVAL_REQUIRED_V1,
                event
        );
        
        log.info("Published discount approval required event for quote: {}", quoteId);
    }
}
