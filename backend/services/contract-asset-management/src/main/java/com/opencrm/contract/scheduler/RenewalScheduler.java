package com.opencrm.contract.scheduler;

import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.SubscriptionRenewalEligibleEvent;
import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Scheduled job to check for renewal-eligible subscriptions.
 * Publishes renewal events to Kafka.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RenewalScheduler {
    
    private final SubscriptionRepository subscriptionRepository;
    private final OutboxEventPublisher outboxEventPublisher;
    
    /**
     * Check for renewal-eligible subscriptions daily at 2 AM.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void checkRenewalEligibility() {
        log.info("Checking for renewal-eligible subscriptions");
        
        LocalDate today = LocalDate.now();
        List<Subscription> eligibleSubscriptions = subscriptionRepository.findRenewalEligible(today);
        
        for (Subscription subscription : eligibleSubscriptions) {
            try {
                // Publish renewal eligible event
                SubscriptionRenewalEligibleEvent event = new SubscriptionRenewalEligibleEvent(
                        subscription.getId().toString(),
                        subscription.getRenewalDate(),
                        subscription.getPlanName(),
                        subscription.getUsageBand()
                );
                
                outboxEventPublisher.publish(
                        "Subscription",
                        subscription.getId().toString(),
                        EventContractRegistry.SUBSCRIPTION_RENEWAL_ELIGIBLE_V1,
                        event
                );
                
                log.info("Published renewal eligible event for subscription: {}", 
                         subscription.getSubscriptionNumber());
                
            } catch (Exception e) {
                log.error("Error publishing renewal event for subscription: {}", 
                          subscription.getSubscriptionNumber(), e);
            }
        }
        
        log.info("Completed renewal eligibility check. Found {} eligible subscriptions", 
                 eligibleSubscriptions.size());
    }
}
