package com.opencrm.approval.service;

import com.opencrm.approval.domain.ApprovalRequest;
import com.opencrm.approval.domain.ApprovalStatus;
import com.opencrm.approval.repository.ApprovalRequestRepository;
import com.opencrm.common.event.KafkaTopics;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.ApprovalRejectedEvent;
import com.opencrm.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Approval service with state machine transitions and event publishing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ApprovalService {
    
    private final ApprovalRequestRepository approvalRequestRepository;
    private final OutboxEventPublisher outboxEventPublisher;
    
    public ApprovalRequest getById(Long id) {
        return approvalRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalRequest", id));
    }
    
    public List<ApprovalRequest> getPendingApprovals() {
        return approvalRequestRepository.findByStatus(ApprovalStatus.PENDING);
    }
    
    public List<ApprovalRequest> getPendingApprovalsByTier(String tier) {
        return approvalRequestRepository.findByApprovalTierAndStatus(tier, ApprovalStatus.PENDING);
    }
    
    public ApprovalRequest approve(Long id, String approverId) {
        log.debug("Approving request ID: {}", id);
        
        ApprovalRequest request = getById(id);
        request.approve(approverId);
        
        ApprovalRequest saved = approvalRequestRepository.save(request);
        
        // Publish approval approved event
        // In production, this would have a proper event DTO
        log.info("Approved request ID: {} for quote: {}", id, request.getQuoteId());
        
        return saved;
    }
    
    public ApprovalRequest reject(Long id, String approverId, String rejectionReason) {
        log.debug("Rejecting request ID: {}", id);
        
        ApprovalRequest request = getById(id);
        request.reject(approverId, rejectionReason);
        
        ApprovalRequest saved = approvalRequestRepository.save(request);
        
        // Publish rejection event via Transactional Outbox
        ApprovalRejectedEvent event = new ApprovalRejectedEvent(
                request.getQuoteId(),
                request.getRejectedAt(),
                rejectionReason,
                "REVISE_QUOTE"
        );
        
        outboxEventPublisher.publish(
                "ApprovalRequest",
                request.getQuoteId(),
                KafkaTopics.QUOTE_APPROVAL_REJECTED,
                event
        );
        
        log.info("Rejected request ID: {} for quote: {}", id, request.getQuoteId());
        
        return saved;
    }
}
