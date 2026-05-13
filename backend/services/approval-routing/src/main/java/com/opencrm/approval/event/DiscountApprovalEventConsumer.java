package com.opencrm.approval.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.approval.domain.ApprovalRequest;
import com.opencrm.approval.domain.ApprovalStatus;
import com.opencrm.approval.domain.ApprovalType;
import com.opencrm.approval.repository.ApprovalRequestRepository;
import com.opencrm.common.event.KafkaTopics;
import com.opencrm.common.event.dto.DiscountApprovalRequiredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Consumes discount approval required events and creates approval requests.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DiscountApprovalEventConsumer {
    
    private final ApprovalRequestRepository approvalRequestRepository;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(topics = KafkaTopics.DISCOUNT_APPROVAL_REQUIRED, groupId = "approval-routing")
    @Transactional
    public void handleDiscountApprovalRequired(String message) {
        try {
            DiscountApprovalRequiredEvent event = objectMapper.readValue(message, DiscountApprovalRequiredEvent.class);
            
            log.info("Received discount approval required event for quote: {}", event.getQuoteId());
            
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
                    .approvalTier(determineApprovalTier(event.getMarginDelta()))
                    .reason("Discount exceeds standard threshold")
                    .policyEvaluationSnapshot(writePolicyEvaluationSnapshot(event))
                    .build();
            
            approvalRequestRepository.save(request);
            
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

    private String writePolicyEvaluationSnapshot(DiscountApprovalRequiredEvent event) {
        try {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("explanationCodes", event.getExplanationCodes());
            snapshot.put("inputSnapshot", event.getInputSnapshot());
            snapshot.put("outputSnapshot", event.getOutputSnapshot());
            snapshot.put("marginDelta", event.getMarginDelta());
            snapshot.put("requestedDiscount", event.getRequestedDiscount());
            snapshot.put("currencyCode", event.getCurrencyCode());
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize policy evaluation snapshot", e);
        }
    }
}
