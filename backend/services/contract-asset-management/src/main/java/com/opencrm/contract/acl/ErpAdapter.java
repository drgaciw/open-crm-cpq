package com.opencrm.contract.acl;

import com.opencrm.contract.domain.Subscription;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Anti-Corruption Layer (ACL) adapter for legacy ERP integration.
 * Isolates ERP integration through ACL boundaries with rate limiting.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ErpAdapter {
    
    /**
     * Sync subscription to legacy ERP system.
     * Rate limited to prevent overwhelming legacy system.
     */
    @RateLimiter(name = "erpSync", fallbackMethod = "syncToErpFallback")
    public String syncToErp(Subscription subscription) {
        log.info("Syncing subscription {} to ERP", subscription.getSubscriptionNumber());
        
        try {
            // Simulate ERP API call
            // In production, this would:
            // 1. Transform subscription to ERP format
            // 2. Call ERP REST/SOAP API
            // 3. Handle ERP-specific error codes
            // 4. Return ERP subscription ID
            
            Thread.sleep(500); // Simulate network latency
            
            String erpSubscriptionId = "ERP-" + subscription.getSubscriptionNumber();
            
            log.info("Successfully synced subscription {} to ERP: {}", 
                     subscription.getSubscriptionNumber(), erpSubscriptionId);
            
            return erpSubscriptionId;
            
        } catch (Exception e) {
            log.error("Error syncing to ERP", e);
            throw new ErpSyncException("Failed to sync subscription to ERP", e);
        }
    }
    
    /**
     * Fallback method when rate limit is exceeded.
     */
    public String syncToErpFallback(Subscription subscription, Throwable t) {
        log.warn("ERP sync rate limit exceeded for subscription: {}", 
                 subscription.getSubscriptionNumber());
        throw new ErpRateLimitException("ERP sync rate limit exceeded, please retry later");
    }
    
    /**
     * Retrieve subscription from ERP.
     */
    @RateLimiter(name = "erpSync")
    public ErpSubscriptionData getFromErp(String erpSubscriptionId) {
        log.info("Retrieving subscription {} from ERP", erpSubscriptionId);
        
        try {
            Thread.sleep(300); // Simulate network latency
            
            // Simulate ERP response
            return new ErpSubscriptionData(
                    erpSubscriptionId,
                    "ACTIVE",
                    "2024-01-01",
                    "2025-01-01"
            );
            
        } catch (Exception e) {
            log.error("Error retrieving from ERP", e);
            throw new ErpSyncException("Failed to retrieve subscription from ERP", e);
        }
    }
}
