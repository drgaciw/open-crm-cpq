package com.opencrm.approval.service;

import com.opencrm.approval.domain.ApprovalRequest;
import com.opencrm.approval.domain.ApprovalStatus;
import com.opencrm.approval.dto.ApprovalRequestDto;
import com.opencrm.approval.dto.ApprovalSubmitRequest;
import com.opencrm.approval.mapper.ApprovalRequestMapper;
import com.opencrm.approval.repository.ApprovalRequestRepository;
import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.ApprovalRejectedEvent;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.security.Caller;
import com.opencrm.security.CurrentActor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Approval service with state machine transitions and event publishing.
 * Public API returns {@link ApprovalRequestDto} only — never the JPA entity.
 *
 * <p>Actors and authority (ADR-016 decisions 6 and 7):
 * <ul>
 *   <li>the requester is the verified caller, a person or {@code service:<client-id>};</li>
 *   <li>a decision needs a person whose app role satisfies the request's tier ({@link ApprovalAuthority}), who is not
 *       the requester. A service token cannot decide. The approver recorded is that person's {@code oid};</li>
 *   <li>only {@code dev} mode lets a request without a verified caller act; it is recorded as this service.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ApprovalService {

    private final ApprovalRequestRepository approvalRequestRepository;
    private final OutboxEventPublisher outboxEventPublisher;
    private final ApprovalRequestMapper approvalRequestMapper;
    private final CurrentActor currentActor;

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

        ApprovalRequest entity = ApprovalRequest.builder()
                .quoteId(request.getQuoteId().trim())
                .lineId(request.getLineId())
                .approvalType(mapped.approvalType())
                .status(ApprovalStatus.PENDING)
                .requestedDiscount(request.getRequestedDiscount())
                .marginDelta(request.getMarginDelta())
                .currencyCode(request.getCurrencyCode())
                .requesterId(currentActor.id())
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

    public ApprovalRequestDto approve(Long id) {
        log.debug("Approving request ID: {}", id);

        ApprovalRequest request = findEntityById(id);
        request.approve(authorizeDecision(request));

        ApprovalRequest saved = approvalRequestRepository.save(request);

        log.info("Approved request ID: {} for quote: {} by {}", id, request.getQuoteId(), request.getApproverId());

        return approvalRequestMapper.toDto(saved);
    }

    public ApprovalRequestDto reject(Long id, String rejectionReason) {
        log.debug("Rejecting request ID: {}", id);

        ApprovalRequest request = findEntityById(id);
        request.reject(authorizeDecision(request), rejectionReason);

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
                EventContractRegistry.QUOTE_APPROVAL_REJECTED_V1,
                event
        );

        log.info("Rejected request ID: {} for quote: {} by {}", id, request.getQuoteId(), request.getApproverId());

        return approvalRequestMapper.toDto(saved);
    }

    /**
     * Who decides, after checking they may (ADR-016 decision 7). Refusals are 401 without a verified caller, and 403
     * for a service, a role that does not satisfy the tier, or the requester deciding their own request.
     */
    private String authorizeDecision(ApprovalRequest request) {
        Optional<Caller> caller = currentActor.caller();
        if (caller.isEmpty()) {
            if (!currentActor.unverifiedCallersAllowed()) {
                throw new BusinessException("An authenticated user is required to decide approvals",
                        HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED");
            }
            log.warn("dev mode: approval {} decided without a verified user, recorded as {}",
                    request.getId(), currentActor.self());
            return currentActor.self();
        }
        if (!(caller.get() instanceof Caller.User user)) {
            throw new BusinessException("Approvals are decided by people, not by services",
                    HttpStatus.FORBIDDEN, "APPROVER_MUST_BE_A_USER");
        }
        if (!ApprovalAuthority.mayDecide(user.authorities(), request.getApprovalTier())) {
            log.warn("Refused decision on approval {} (tier {}) by {}: none of {}", request.getId(),
                    request.getApprovalTier(), user.actorId(), ApprovalAuthority.decidersOf(request.getApprovalTier()));
            throw new BusinessException("Deciding a " + request.getApprovalTier() + " approval needs one of the roles "
                    + new TreeSet<>(ApprovalAuthority.decidersOf(request.getApprovalTier())),
                    HttpStatus.FORBIDDEN, "APPROVER_ROLE_REQUIRED");
        }
        if (user.actorId().equals(request.getRequesterId())) {
            log.warn("Refused self-approval of approval {} by its requester {}", request.getId(), user.actorId());
            throw new BusinessException("The requester cannot decide their own approval",
                    HttpStatus.FORBIDDEN, "SELF_APPROVAL");
        }
        return user.actorId();
    }

    private ApprovalRequest findEntityById(Long id) {
        return approvalRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalRequest", id));
    }
}
