package com.opencrm.approval.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.approval.domain.PolicyBreach;
import com.opencrm.approval.domain.PolicyStatus;
import com.opencrm.approval.domain.QuotePolicyDecision;
import com.opencrm.approval.domain.QuotePolicyEvaluator;
import com.opencrm.approval.domain.QuoteVersionFacts;
import com.opencrm.approval.dto.PolicyBreachDto;
import com.opencrm.approval.dto.QuoteEvaluationDto;
import com.opencrm.approval.dto.QuoteEvaluationRequest;
import com.opencrm.approval.repository.CommercialPolicyRepository;
import com.opencrm.audit.AuditTrailService;
import com.opencrm.audit.dto.AppendAuditEntryCommand;
import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.QuoteEvaluatedEvent;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.security.Caller;
import com.opencrm.security.CurrentActor;
import com.opencrm.security.SecurityUtils;
import com.opencrm.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code EvaluateQuote} for one quote version (BC-04-02, DRG-442). The verdict and the breach explanations
 * are written to the outbox as {@code QuoteEvaluated} and appended to the BC-20 audit trail in the same
 * transaction. This does not create an approval request and does not choose an approver.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuotePolicyEvaluationService {

    private final CommercialPolicyRepository commercialPolicyRepository;
    private final OutboxEventPublisher outboxEventPublisher;
    private final AuditTrailService auditTrailService;
    private final ObjectMapper objectMapper;
    private final CurrentActor currentActor;

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public QuoteEvaluationDto evaluate(QuoteEvaluationRequest request) {
        Actor actor = requireActor();
        requireTenantMatch(actor, request.getTenantId());

        QuoteVersionFacts facts = new QuoteVersionFacts(
                request.getQuoteId().trim(),
                request.getQuoteVersion(),
                request.getTenantId(),
                request.getSegment(),
                request.getProductFamily(),
                request.getRegion(),
                request.getDiscountPercent(),
                request.getMarginPercent(),
                request.getTermMonths(),
                request.getClauseCodes(),
                request.getAsOf() != null ? request.getAsOf() : LocalDate.now(ZoneOffset.UTC));

        QuotePolicyDecision decision = QuotePolicyEvaluator.evaluateQuote(
                facts,
                commercialPolicyRepository.findByStatusAndDeletedFalse(PolicyStatus.ACTIVE));

        UUID eventId = UUID.randomUUID();
        List<QuoteEvaluatedEvent.BreachExplained> explained = decision.explainPolicyBreaches().stream()
                .map(QuotePolicyEvaluationService::explained)
                .toList();
        QuoteEvaluatedEvent event = new QuoteEvaluatedEvent(
                facts.quoteId(),
                facts.quoteVersion(),
                actor.tenantId(),
                actor.principal(),
                decision.verdict().name(),
                decision.appliedPolicyIds(),
                explained);
        outboxEventPublisher.publish(
                "Quote",
                facts.quoteId(),
                EventContractRegistry.QUOTE_EVALUATED_V1,
                event,
                eventId);

        auditTrailService.append(new AppendAuditEntryCommand(
                actor.tenantId(),
                "Quote",
                facts.quoteId(),
                actor.principal(),
                "QuoteEvaluated",
                details(eventId, decision),
                Instant.now()));

        log.info("Evaluated quote {} version {} verdict {} breaches {}",
                facts.quoteId(), facts.quoteVersion(), decision.verdict(), explained.size());

        return QuoteEvaluationDto.builder()
                .quoteId(facts.quoteId())
                .quoteVersion(facts.quoteVersion())
                .verdict(decision.verdict())
                .breaches(decision.explainPolicyBreaches().stream().map(QuotePolicyEvaluationService::toDto).toList())
                .build();
    }

    /**
     * A person evaluates a quote. A service token cannot. Dev mode, which has no caller, is recorded as this
     * service so local submission still evaluates.
     */
    private Actor requireActor() {
        Optional<Caller> caller = SecurityUtils.getCurrentCaller();
        if (caller.isEmpty()) {
            if (currentActor.unverifiedCallersAllowed()) {
                return new Actor(currentActor.self(), null);
            }
            throw new BusinessException("A user must evaluate the quote", HttpStatus.FORBIDDEN, "ACTOR_REQUIRED");
        }
        if (!(caller.get() instanceof Caller.User)) {
            throw new BusinessException("A user must evaluate the quote", HttpStatus.FORBIDDEN, "ACTOR_REQUIRED");
        }
        UserContext user = SecurityUtils.getCurrentUser()
                .orElseThrow(() -> new BusinessException(
                        "A user must evaluate the quote", HttpStatus.FORBIDDEN, "ACTOR_REQUIRED"));
        if (user.getTenantId() == null || user.getTenantId().isBlank()) {
            throw new BusinessException("Tenant identity is required", HttpStatus.FORBIDDEN, "TENANT_REQUIRED");
        }
        return new Actor("user:" + user.getUserId(), user.getTenantId().strip());
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

    private String details(UUID eventId, QuotePolicyDecision decision) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", eventId.toString());
        body.put("verdict", decision.verdict().name());
        body.put("breaches", decision.explainPolicyBreaches().stream().map(breach -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("policyId", breach.policyId());
            row.put("policyVersion", breach.policyVersion());
            row.put("thresholdType", breach.thresholdType().name());
            row.put("actual", breach.actual());
            row.put("limit", breach.limit());
            row.put("reasonCode", breach.reasonCode());
            return row;
        }).toList());
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("Quote evaluation could not be recorded", "POLICY_EVALUATION_AUDIT_FAILED");
        }
    }

    private static QuoteEvaluatedEvent.BreachExplained explained(PolicyBreach breach) {
        return new QuoteEvaluatedEvent.BreachExplained(
                breach.policyId(),
                breach.policyVersion(),
                breach.thresholdType().name(),
                breach.actual(),
                breach.limit(),
                breach.reasonCode());
    }

    private static PolicyBreachDto toDto(PolicyBreach breach) {
        return PolicyBreachDto.builder()
                .policyId(breach.policyId())
                .policyVersion(breach.policyVersion())
                .thresholdType(breach.thresholdType())
                .actual(breach.actual())
                .limit(breach.limit())
                .reasonCode(breach.reasonCode())
                .build();
    }

    private record Actor(String principal, String tenantId) {
    }
}
