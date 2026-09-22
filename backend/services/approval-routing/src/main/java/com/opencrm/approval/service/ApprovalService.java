package com.opencrm.approval.service;

import com.opencrm.approval.domain.ApprovalRequest;
import com.opencrm.approval.domain.ApprovalStatus;
import com.opencrm.approval.dto.ApprovalRequestDto;
import com.opencrm.approval.dto.ApprovalSubmitRequest;
import com.opencrm.approval.mapper.ApprovalRequestMapper;
import com.opencrm.approval.repository.ApprovalRequestRepository;
import com.opencrm.common.event.KafkaTopics;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.ApprovalRejectedEvent;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Approval service with state machine transitions and event publishing.
 * Public API returns {@link ApprovalRequestDto} only — never the JPA entity.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ApprovalService {

    private final ApprovalRequestRepository approvalRequestRepository;
    private final OutboxEventPublisher outboxEventPublisher;
    private final ApprovalRequestMapper approvalRequestMapper;

    /**
     * Create a PENDING approval for quote BFF orchestration (S2C-12).
     * Additive to {@link com.opencrm.approval.event.DiscountApprovalEventConsumer}.
     */
    public ApprovalRequestDto submit(ApprovalSubmitRequest request) {
        if (request == null) {
            throw new BusinessException("Approval submit request is required", "INVALID_APPROVAL_SUBMIT");
        }
        if (request.getQuoteId() == null || request.getQuoteId().isBlank()) {
            throw new BusinessException("quoteId is required", "INVALID_APPROVAL_SUBMIT");
        }

        QuoteApprovalTierMapper.MappedApproval mapped = QuoteApprovalTierMapper.map(request.getTier());
        String requesterId = (request.getRequesterId() == null || request.getRequesterId().isBlank())
                ? "system"
                : request.getRequesterId();

        ApprovalRequest entity = ApprovalRequest.builder()
                .quoteId(request.getQuoteId().trim())
                .lineId(request.getLineId())
                .approvalType(mapped.approvalType())
                .status(ApprovalStatus.PENDING)
                .requestedDiscount(request.getRequestedDiscount())
                .marginDelta(request.getMarginDelta())
                .currencyCode(request.getCurrencyCode())
                .requesterId(requesterId)
                .submittedAt(Instant.now())
                .approvalTier(mapped.approvalTier())
                .reason(request.getReason() != null ? request.getReason() : "Quote approval submitted via BFF")
                .build();

        ApprovalRequest saved = approvalRequestRepository.save(entity);
        log.info("Created PENDING approval id={} quoteId={} tier={} type={}",
                saved.getId(), saved.getQuoteId(), saved.getApprovalTier(), saved.getApprovalType());
        return approvalRequestMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public ApprovalRequestDto getById(Long id) {
        return approvalRequestMapper.toDto(findEntityById(id));
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequestDto> getByQuoteId(String quoteId) {
        return approvalRequestMapper.toDtoList(
                approvalRequestRepository.findByQuoteIdAndDeletedFalseOrderBySubmittedAtDescIdDesc(quoteId));
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequestDto> getPendingApprovals() {
        return approvalRequestMapper.toDtoList(
                approvalRequestRepository.findByStatus(ApprovalStatus.PENDING));
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequestDto> getPendingApprovalsByTier(String tier) {
        return approvalRequestMapper.toDtoList(
                approvalRequestRepository.findByApprovalTierAndStatus(tier, ApprovalStatus.PENDING));
    }

    public ApprovalRequestDto approve(Long id, String approverId) {
        log.debug("Approving request ID: {}", id);

        ApprovalRequest request = findEntityById(id);
        request.approve(approverId);

        ApprovalRequest saved = approvalRequestRepository.save(request);

        log.info("Approved request ID: {} for quote: {}", id, request.getQuoteId());

        return approvalRequestMapper.toDto(saved);
    }

    public ApprovalRequestDto reject(Long id, String approverId, String rejectionReason) {
        log.debug("Rejecting request ID: {}", id);

        ApprovalRequest request = findEntityById(id);
        request.reject(approverId, rejectionReason);

        ApprovalRequest saved = approvalRequestRepository.save(request);

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

        return approvalRequestMapper.toDto(saved);
    }

    private ApprovalRequest findEntityById(Long id) {
        return approvalRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalRequest", id));
    }
}
