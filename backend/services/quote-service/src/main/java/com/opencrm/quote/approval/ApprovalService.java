package com.opencrm.quote.approval;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.quote.quote.*;
import com.opencrm.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ApprovalService {

    private final ApprovalRequestRepository approvalRepository;
    private final QuoteRepository quoteRepository;

    public ApprovalRequestDto submitForApproval(Long quoteId, ApprovalSubmitRequest request) {
        log.debug("Submitting quote {} for approval at tier {}", quoteId, request.getTier());

        Quote quote = quoteRepository.findByIdAndDeletedFalse(quoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Quote", quoteId));

        if (quote.getStatus() != QuoteStatus.IN_REVIEW && quote.getStatus() != QuoteStatus.DRAFT) {
            throw new BusinessException("Quote must be in DRAFT or IN_REVIEW to submit for approval", "INVALID_STATUS_TRANSITION");
        }

        ApprovalRequest approval = ApprovalRequest.builder()
                .quoteId(quoteId)
                .tier(request.getTier())
                .requestedBy(request.getRequestedBy() != null ? request.getRequestedBy() : "system")
                .build();

        ApprovalRequest saved = approvalRepository.save(approval);

        quote.setStatus(QuoteStatus.PENDING_APPROVAL);
        quote.setApprovalState(ApprovalState.PENDING);
        quote.setApprovalTier(request.getTier().name());
        quoteRepository.save(quote);

        log.info("Quote {} submitted for {} approval", quote.getQuoteNumber(), request.getTier());
        return toDto(saved);
    }

    public ApprovalRequestDto approveRequest(Long approvalId, ApprovalResponseRequest request) {
        ApprovalRequest approval = approvalRepository.findByIdAndDeletedFalse(approvalId)
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalRequest", approvalId));

        if (approval.getStatus() != ApprovalStatus.PENDING) {
            throw new BusinessException("Approval request is not pending", "APPROVAL_NOT_PENDING");
        }

        String currentUser = SecurityUtils.getCurrentUserId();
        approval.setStatus(ApprovalStatus.APPROVED);
        approval.setApprover(currentUser);
        approval.setComments(request.getComments());
        approval.setRespondedAt(Instant.now());

        ApprovalRequest saved = approvalRepository.save(approval);

        Quote quote = quoteRepository.findByIdAndDeletedFalse(approval.getQuoteId())
                .orElseThrow(() -> new ResourceNotFoundException("Quote", approval.getQuoteId()));
        quote.setStatus(QuoteStatus.APPROVED);
        quote.setApprovalState(ApprovalState.APPROVED);
        quoteRepository.save(quote);

        log.info("Approval {} approved for quote {}", approvalId, quote.getQuoteNumber());
        return toDto(saved);
    }

    public ApprovalRequestDto rejectRequest(Long approvalId, ApprovalResponseRequest request) {
        ApprovalRequest approval = approvalRepository.findByIdAndDeletedFalse(approvalId)
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalRequest", approvalId));

        if (approval.getStatus() != ApprovalStatus.PENDING) {
            throw new BusinessException("Approval request is not pending", "APPROVAL_NOT_PENDING");
        }

        String currentUser = SecurityUtils.getCurrentUserId();
        approval.setStatus(ApprovalStatus.REJECTED);
        approval.setApprover(currentUser);
        approval.setComments(request.getComments());
        approval.setRespondedAt(Instant.now());

        ApprovalRequest saved = approvalRepository.save(approval);

        Quote quote = quoteRepository.findByIdAndDeletedFalse(approval.getQuoteId())
                .orElseThrow(() -> new ResourceNotFoundException("Quote", approval.getQuoteId()));
        quote.setStatus(QuoteStatus.REJECTED);
        quote.setApprovalState(ApprovalState.REJECTED);
        quoteRepository.save(quote);

        log.info("Approval {} rejected for quote {}", approvalId, quote.getQuoteNumber());
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequestDto> getByQuoteId(Long quoteId) {
        return approvalRepository.findByQuoteIdAndDeletedFalseOrderByCreatedAtDesc(quoteId)
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Page<ApprovalRequestDto> getPendingApprovals(Pageable pageable) {
        return approvalRepository.findByStatusAndDeletedFalse(ApprovalStatus.PENDING, pageable)
                .map(this::toDto);
    }

    private ApprovalRequestDto toDto(ApprovalRequest a) {
        return ApprovalRequestDto.builder()
                .id(a.getId())
                .uuid(a.getUuid())
                .quoteId(a.getQuoteId())
                .tier(a.getTier())
                .status(a.getStatus())
                .requestedBy(a.getRequestedBy())
                .approver(a.getApprover())
                .comments(a.getComments())
                .requestedAt(a.getRequestedAt())
                .respondedAt(a.getRespondedAt())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
