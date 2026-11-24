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
        if (request.getPricingFingerprint() != null && (!request.getPricingFingerprint().matches("[a-f0-9]{64}")
                || request.getGoverningPeriod() == null || request.getGoverningPeriod() < 1)) {
            throw new BusinessException("Invalid ramp approval binding", "INVALID_APPROVAL_SUBMIT");
        }

        ApprovalPrincipal.Scope scope = writeScope();
        ApprovalRequest entity = ApprovalRequest.builder()
                .quoteId(request.getQuoteId().trim())
                .lineId(request.getLineId())
                .approvalType(mapped.approvalType())
                .status(ApprovalStatus.PENDING)
                .requestedDiscount(request.getRequestedDiscount())
                .marginDelta(request.getMarginDelta())
                .currencyCode(request.getCurrencyCode())
                .pricingFingerprint(request.getPricingFingerprint())
                .governingPeriod(request.getGoverningPeriod())
                .requesterId(currentActor.id())
                .submittedAt(Instant.now())
                .approvalTier(mapped.approvalTier())
                .reason(request.getReason() != null ? request.getReason() : "Quote approval submitted via BFF")
                .tenantId(scope.tenantId())
                .accountId(scope.accountId())
                .build();

        ApprovalRequest saved = approvalRequestRepository.save(entity);
        log.info("Created PENDING approval id={} quoteId={} tier={} type={}",
                saved.getId(), saved.getQuoteId(), saved.getApprovalTier(), saved.getApprovalType());
        return approvalRequestMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public ApprovalRequestDto getById(Long id) {
        readScope();
        ApprovalRequest entity = approvalRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalRequest", id));
        requireSameTenant(entity);
        return approvalRequestMapper.toDto(entity);
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequestDto> getByQuoteId(String quoteId) {
        ApprovalPrincipal.Scope scope = readScope();
        List<ApprovalRequest> rows = scope.enforced()
                ? approvalRequestRepository.findScopedByQuoteId(quoteId, scope.tenantId(), scope.accountId())
                : approvalRequestRepository.findByQuoteIdAndDeletedFalseOrderBySubmittedAtDescIdDesc(quoteId);
        return approvalRequestMapper.toDtoList(rows);
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequestDto> getPendingApprovals() {
        ApprovalPrincipal.Scope scope = readScope();
        List<ApprovalRequest> rows = scope.enforced()
                ? approvalRequestRepository.findScopedByStatus(ApprovalStatus.PENDING, scope.tenantId(), scope.accountId())
                : approvalRequestRepository.findByStatus(ApprovalStatus.PENDING);
        return approvalRequestMapper.toDtoList(rows);
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequestDto> getPendingApprovalsByTier(String tier) {
        ApprovalPrincipal.Scope scope = readScope();
        List<ApprovalRequest> rows = scope.enforced()
                ? approvalRequestRepository.findScopedByTierAndStatus(tier, ApprovalStatus.PENDING, scope.tenantId(), scope.accountId())
                : approvalRequestRepository.findByApprovalTierAndStatus(tier, ApprovalStatus.PENDING);
        return approvalRequestMapper.toDtoList(rows);
    }

    public ApprovalRequestDto approve(Long id) {
        log.debug("Approving request ID: {}", id);

        ApprovalRequest request = findEntityById(id);
        String approver = authorizeDecision(request);
        requireNotSuperseded(request);
        request.approve(approver);

        ApprovalRequest saved = approvalRequestRepository.save(request);

        log.info("Approved request ID: {} for quote: {} by {}", id, request.getQuoteId(), request.getApproverId());

        return approvalRequestMapper.toDto(saved);
    }

    public ApprovalRequestDto reject(Long id, String rejectionReason) {
        log.debug("Rejecting request ID: {}", id);

        ApprovalRequest request = findEntityById(id);
        String approver = authorizeDecision(request);
        requireNotSuperseded(request);
        request.reject(approver, rejectionReason);

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
     * Who decides, after checking they may (ADR-016 decision 7). Refusals are 401 without a verified caller; then
     * 404 for another tenant's row, exactly like a missing id (H4), before any other check so the answer cannot
     * reveal that the id exists; then 403 for a service, a role that does not satisfy the tier, or the requester
     * deciding their own request.
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
        requireSameTenant(request);
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

    /**
     * H4 owns this check. Decisions (and DRG-444 escalate) must call it BEFORE the role, self-approval and
     * people-only checks, so a caller without the role cannot tell another tenant's id from a missing one
     * (a 403 there would be an existence oracle). A different tenant, a row with
     * no tenant (so an unbackfilled row is not shared), and a different account when the caller is
     * account-scoped all answer 404, exactly like a missing id, so a caller cannot learn which ids exist in
     * another tenant (user decision, 2026-11-24). Dev mode returns before this method.
     */
    void requireSameTenant(ApprovalRequest request) {
        ApprovalPrincipal.Scope scope = ApprovalPrincipal.current();
        if (!scope.enforced()) {
            return;
        }
        String owner = request.getTenantId() == null ? null : request.getTenantId().strip();
        if (owner == null || owner.isEmpty() || !owner.equals(scope.tenantId())) {
            throw new ResourceNotFoundException("ApprovalRequest", request.getId());
        }
        if (scope.accountId() != null && !scope.accountId().equals(request.getAccountId())) {
            throw new ResourceNotFoundException("ApprovalRequest", request.getId());
        }
    }

    /**
     * A superseded request was replaced by a newer one for the same quote line, carrying different figures (owner
     * decision 9), so a decision on it would approve or reject figures nobody is asking about any more. Checked after
     * {@link #authorizeDecision}, so a caller who may not decide learns nothing about the request's state.
     */
    private static void requireNotSuperseded(ApprovalRequest request) {
        if (request.getStatus() == ApprovalStatus.SUPERSEDED) {
            throw new BusinessException("Approval " + request.getId() + " was superseded by approval "
                    + request.getSupersededBy() + " and can no longer be decided; decide the newer request",
                    HttpStatus.CONFLICT, "APPROVAL_SUPERSEDED");
        }
    }

    /**
     * Writes require a tenant from the verified token. Dev mode, with no token, may still record a null tenant.
     * A token without {@code tid} is refused. The request body is not a source.
     */
    private ApprovalPrincipal.Scope writeScope() {
        ApprovalPrincipal.Scope scope = ApprovalPrincipal.current();
        if (scope.jwtWithoutTenant() || (!scope.enforced() && !currentActor.unverifiedCallersAllowed())) {
            throw tenantRequired();
        }
        return scope;
    }

    /**
     * Reads outside dev mode require a tenant. A different tenant, or a different account when the caller
     * is account-scoped, is not found. Dev mode with no token keeps the unscoped queries.
     */
    private ApprovalPrincipal.Scope readScope() {
        ApprovalPrincipal.Scope scope = ApprovalPrincipal.current();
        if (scope.jwtWithoutTenant() || (!scope.enforced() && !currentActor.unverifiedCallersAllowed())) {
            throw tenantRequired();
        }
        return scope;
    }

    private ApprovalRequest findEntityById(Long id) {
        ApprovalPrincipal.Scope scope = ApprovalPrincipal.current();
        if (scope.jwtWithoutTenant()) {
            throw tenantRequired();
        }
        return approvalRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalRequest", id));
    }

    private static BusinessException tenantRequired() {
        return new BusinessException("Tenant is not on the verified token",
                HttpStatus.FORBIDDEN, "TENANT_REQUIRED");
    }
}
