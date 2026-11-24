package com.opencrm.approval.service;

import com.opencrm.approval.domain.PolicyException;
import com.opencrm.approval.dto.PolicyExceptionDto;
import com.opencrm.approval.dto.RecordPolicyExceptionRequest;
import com.opencrm.approval.repository.CommercialPolicyRepository;
import com.opencrm.approval.repository.PolicyExceptionRepository;
import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.ExceptionRecordedEvent;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.security.Caller;
import com.opencrm.security.CurrentActor;
import com.opencrm.security.SecurityUtils;
import com.opencrm.security.UserContext;
import com.opencrm.security.web.EntraRoleAuthorities;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * {@code RecordException} (BC-04-05, DRG-445). approval-routing is the canonical writer. The quote BFF
 * relays the caller's token. The recorded actor and tenant come from that token, never from the body.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyExceptionService {

    private static final int MAX_JUSTIFICATION = 2000;
    private static final Set<String> RECORDERS = Set.of(
            EntraRoleAuthorities.authorityFor("DealDesk"),
            EntraRoleAuthorities.authorityFor("SalesVP"),
            EntraRoleAuthorities.authorityFor("CFO"));

    private final PolicyExceptionRepository policyExceptionRepository;
    private final CommercialPolicyRepository commercialPolicyRepository;
    private final OutboxEventPublisher outboxEventPublisher;
    private final CurrentActor currentActor;

    @Transactional
    public PolicyExceptionDto record(RecordPolicyExceptionRequest request) {
        Actor actor = requireRecorder();
        requireTenantMatch(actor, request.getTenantId());
        String justification = requireJustification(request.getJustification());
        Instant expiresAt = requireFutureExpiry(request.getExpiresAt());
        if (request.getQuoteId() == null || request.getQuoteId().isBlank() || request.getQuoteVersion() == null) {
            throw new BusinessException(
                    "An exception is bound to a quote version", HttpStatus.BAD_REQUEST, "QUOTE_VERSION_REQUIRED");
        }
        if (request.getPolicyId() == null) {
            throw new BusinessException("An exception is bound to a policy", HttpStatus.BAD_REQUEST, "POLICY_REQUIRED");
        }
        commercialPolicyRepository.findByIdAndDeletedFalse(request.getPolicyId())
                .orElseThrow(() -> new ResourceNotFoundException("CommercialPolicy", request.getPolicyId()));

        PolicyException saved = policyExceptionRepository.save(PolicyException.builder()
                .tenantId(actor.tenantId())
                .quoteId(request.getQuoteId().trim())
                .quoteVersion(request.getQuoteVersion())
                .policyId(request.getPolicyId())
                .justification(justification)
                .expiresAt(expiresAt)
                .recordedBy(actor.principal())
                .build());

        outboxEventPublisher.publish(
                "PolicyException",
                String.valueOf(saved.getId()),
                EventContractRegistry.EXCEPTION_RECORDED_V1,
                new ExceptionRecordedEvent(
                        saved.getId(),
                        saved.getQuoteId(),
                        saved.getQuoteVersion(),
                        saved.getPolicyId(),
                        saved.getTenantId(),
                        saved.getRecordedBy(),
                        saved.getJustification(),
                        saved.getExpiresAt()));

        log.info("Recorded policy exception id={} quote={} version={} policy={} expires={}",
                saved.getId(), saved.getQuoteId(), saved.getQuoteVersion(), saved.getPolicyId(), saved.getExpiresAt());
        return toDto(saved);
    }

    private Actor requireRecorder() {
        Optional<Caller> caller = SecurityUtils.getCurrentCaller();
        if (caller.isEmpty()) {
            if (currentActor.unverifiedCallersAllowed()) {
                return new Actor(currentActor.self(), null);
            }
            throw new BusinessException(
                    "A deal desk user must record the exception", HttpStatus.FORBIDDEN, "ACTOR_REQUIRED");
        }
        if (!(caller.get() instanceof Caller.User user)) {
            throw new BusinessException(
                    "A deal desk user must record the exception", HttpStatus.FORBIDDEN, "ACTOR_REQUIRED");
        }
        if (RECORDERS.stream().noneMatch(user.authorities()::contains)) {
            throw new BusinessException(
                    "Recording a policy exception needs DealDesk, SalesVP, or CFO",
                    HttpStatus.FORBIDDEN,
                    "EXCEPTION_ROLE_REQUIRED");
        }
        UserContext context = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new BusinessException(
                        "A deal desk user must record the exception", HttpStatus.FORBIDDEN, "ACTOR_REQUIRED"));
        if (context.getTenantId() == null || context.getTenantId().isBlank()) {
            throw new BusinessException("Tenant identity is required", HttpStatus.FORBIDDEN, "TENANT_REQUIRED");
        }
        return new Actor("user:" + context.getUserId(), context.getTenantId().strip());
    }

    private static void requireTenantMatch(Actor actor, String quoteTenantId) {
        if (actor.tenantId() == null || quoteTenantId == null || quoteTenantId.isBlank()) {
            return;
        }
        if (!actor.tenantId().equals(quoteTenantId.strip())) {
            throw new BusinessException(
                    "Quote tenant does not match the caller", HttpStatus.FORBIDDEN, "TENANT_MISMATCH");
        }
    }

    private static String requireJustification(String justification) {
        if (justification == null || justification.isBlank()) {
            throw new BusinessException(
                    "A justification is required", HttpStatus.BAD_REQUEST, "JUSTIFICATION_REQUIRED");
        }
        String trimmed = justification.trim();
        if (trimmed.length() > MAX_JUSTIFICATION) {
            throw new BusinessException(
                    "Justification is too long", HttpStatus.BAD_REQUEST, "JUSTIFICATION_TOO_LONG");
        }
        return trimmed;
    }

    private static Instant requireFutureExpiry(Instant expiresAt) {
        if (expiresAt == null) {
            throw new BusinessException(
                    "An expiry is required", HttpStatus.BAD_REQUEST, "EXCEPTION_EXPIRY_REQUIRED");
        }
        if (!expiresAt.isAfter(Instant.now())) {
            throw new BusinessException(
                    "An exception must expire in the future", HttpStatus.BAD_REQUEST, "EXCEPTION_EXPIRED");
        }
        return expiresAt;
    }

    private static PolicyExceptionDto toDto(PolicyException saved) {
        return PolicyExceptionDto.builder()
                .id(saved.getId())
                .quoteId(saved.getQuoteId())
                .quoteVersion(saved.getQuoteVersion())
                .policyId(saved.getPolicyId())
                .justification(saved.getJustification())
                .expiresAt(saved.getExpiresAt())
                .recordedBy(saved.getRecordedBy())
                .build();
    }

    private record Actor(String principal, String tenantId) {
    }
}
