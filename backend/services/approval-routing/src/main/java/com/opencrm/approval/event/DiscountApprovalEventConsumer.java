package com.opencrm.approval.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.approval.domain.ApprovalRequest;
import com.opencrm.approval.domain.ApprovalStatus;
import com.opencrm.approval.domain.ApprovalType;
import com.opencrm.approval.event.shadow.DiscountTierShadowService;
import com.opencrm.approval.repository.ApprovalRequestRepository;
import com.opencrm.common.event.KafkaTopics;
import com.opencrm.common.event.dto.DiscountApprovalRequiredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Consumes discount approval required events and creates approval requests.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DiscountApprovalEventConsumer {
    
    private final ApprovalRequestRepository approvalRequestRepository;
    private final ObjectMapper objectMapper;
    private final DiscountTierShadowService discountTierShadow;
    
    @KafkaListener(topics = KafkaTopics.DISCOUNT_APPROVAL_REQUIRED, groupId = "approval-routing")
    @Transactional
    public void handleDiscountApprovalRequired(String message) {
        try {
            DiscountApprovalRequiredEvent event = objectMapper.readValue(message, DiscountApprovalRequiredEvent.class);
            
            log.info("Received discount approval required event for quote: {}", event.getQuoteId());
            
            String ruleTier = determineApprovalTier(event.getMarginDelta());

            // Create approval request
            ApprovalRequest request = ApprovalRequest.builder()
                    .quoteId(event.getQuoteId())
                    .lineId(event.getLineId())
                    .approvalType(ApprovalType.DISCOUNT)
                    .status(ApprovalStatus.PENDING)
                    .requestedDiscount(event.getRequestedDiscount())
                    .marginDelta(event.getMarginDelta())
                    .currencyCode(event.getCurrencyCode())
                    .requesterId("system") // In production, extract from context
                    .submittedAt(Instant.now())
                    .approvalTier(ruleTier)
                    .reason("Discount exceeds standard threshold")
                    .build();
            
            ApprovalRequest saved = approvalRequestRepository.save(request);

            // Advisory shadow only (ADR-015): the rule above stays authoritative. Never throws, and runs
            // after this transaction commits, so it cannot cause a redelivery.
            discountTierShadow.recordAfterCommit(saved.getId(), event, ruleTier);
            
            log.info("Created approval request for quote: {}", event.getQuoteId());
            
        } catch (Exception e) {
            log.error("Error processing discount approval required event", e);
            throw new RuntimeException("Failed to process discount approval event", e);
        }
    }
    
    private String determineApprovalTier(java.math.BigDecimal marginDelta) {
        // Simple tier logic - in production, this would be more sophisticated
        if (marginDelta.compareTo(java.math.BigDecimal.valueOf(30)) > 0) {
            return "VP_SALES";
        } else if (marginDelta.compareTo(java.math.BigDecimal.valueOf(20)) > 0) {
            return "DIRECTOR";
        } else {
            return "MANAGER";
        }
    }
}
