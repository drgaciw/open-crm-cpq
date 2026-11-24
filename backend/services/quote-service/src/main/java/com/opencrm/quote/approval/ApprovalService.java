package com.opencrm.quote.approval;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.quote.client.ApprovalRoutingClient;
import com.opencrm.quote.client.RoutingApprovalCommentRequest;
import com.opencrm.quote.client.RoutingApprovalDto;
import com.opencrm.quote.client.RoutingApprovalHistoryEntry;
import com.opencrm.quote.client.RoutingApprovalSubmitRequest;
import com.opencrm.quote.quote.ApprovalState;
import com.opencrm.quote.quote.Quote;
import com.opencrm.quote.quote.QuoteAccountResolver;
import com.opencrm.quote.quote.QuoteRepository;
import com.opencrm.quote.quote.QuoteStatus;
import com.opencrm.quote.quote.QuoteTenantResolver;
import com.opencrm.security.Caller;
import com.opencrm.security.SecurityUtils;
import com.opencrm.security.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Quote BFF for approvals (S2C-12 / Wave G4).
 * <p>
 * Single-writer: approval <em>records</em> are created/updated in approval-routing.
 * This service only projects {@link Quote} status / approvalState / approvalTier.
 * Dual-read GETs may fall back to legacy local {@code approval_requests} rows.
 * <p>
 * Identity and authority (ADR-016 decisions 6 and 7): the quote BFF sends no requester or approver. It relays the
 * user's token, and approval-routing records that user and checks that their role may decide the tier. A legacy local
 * approval, one approval-routing does not know, is history only: deciding it is refused, because it would bypass that
 * check.
 */
@Slf4j
@Service
@Transactional
public class ApprovalService {

    private final ApprovalRequestRepository approvalRepository;
    private final QuoteRepository quoteRepository;
    private final ApprovalRoutingClient approvalRoutingClient;
    private final boolean legacyLocalReadEnabled;
    private final com.opencrm.quote.ramp.RampScheduleGuard rampScheduleGuard;
    private final QuotePolicyEvaluation quotePolicyEvaluation;

    @Autowired
    public ApprovalService(
            ApprovalRequestRepository approvalRepository,
            QuoteRepository quoteRepository,
            ApprovalRoutingClient approvalRoutingClient,
            @Value("${approvals.legacy-local-read-enabled:true}") boolean legacyLocalReadEnabled,
            com.opencrm.quote.ramp.RampScheduleGuard rampScheduleGuard,
            QuotePolicyEvaluation quotePolicyEvaluation) {
        this.approvalRepository = approvalRepository;
        this.quoteRepository = quoteRepository;
        this.approvalRoutingClient = approvalRoutingClient;
        this.legacyLocalReadEnabled = legacyLocalReadEnabled;
        this.rampScheduleGuard = rampScheduleGuard;
        this.quotePolicyEvaluation = quotePolicyEvaluation;
    }

    /** Tests that predate EvaluateQuote keep the submit path that does not call approval-routing for a verdict. */
    public ApprovalService(
            ApprovalRequestRepository approvalRepository,
            QuoteRepository quoteRepository,
            ApprovalRoutingClient approvalRoutingClient,
            boolean legacyLocalReadEnabled,
            com.opencrm.quote.ramp.RampScheduleGuard rampScheduleGuard) {
        this(approvalRepository, quoteRepository, approvalRoutingClient, legacyLocalReadEnabled, rampScheduleGuard,
                QuotePolicyEvaluation.notConfigured());
    }

    /**
     * EvaluateQuote for the seller entry point. Does not write an approval. A blocked verdict is also enforced
     * by {@link #submitForApproval} before any approval-routing write.
     */
    public QuotePolicyEvaluationResponse evaluatePolicy(Long quoteId) {
        requireSeller();
        Quote quote = quoteRepository.findByIdAndDeletedFalse(quoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Quote", quoteId));
        String tenantId = SecurityUtils.getCurrentUser().map(UserContext::getTenantId)
                .filter(value -> !value.isBlank()).orElseThrow(() -> new BusinessException(
                        "Tenant identity is required", HttpStatus.FORBIDDEN, "TENANT_REQUIRED"));
        if (quote.getTenantId() != null && !tenantId.equals(quote.getTenantId())) {
            throw new BusinessException(
                    "Quote tenant does not match the caller", HttpStatus.FORBIDDEN, "TENANT_MISMATCH");
        }
        QuotePolicyVerdict verdict = quotePolicyEvaluation.evaluate(quote);
        if (!verdict.configured()) {
            throw new BusinessException(
                    "Commercial policy evaluation is not available",
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "POLICY_EVALUATOR_UNAVAILABLE");
        }
        return new QuotePolicyEvaluationResponse(quote.getId(), verdict.quoteVersion(), verdict.verdict(), verdict.breaches());
    }

    private static void requireSeller() {
        if (SecurityUtils.getCurrentCaller().filter(Caller.User.class::isInstance).isEmpty()) {
            throw new BusinessException("A user must evaluate the quote", HttpStatus.FORBIDDEN, "ACTOR_REQUIRED");
        }
    }

    public ApprovalRequestDto submitForApproval(Long quoteId, ApprovalSubmitRequest request) {
        log.debug("Submitting quote {} for approval at tier {} via approval-routing", quoteId, request.getTier());

        Quote quote = requireQuote(quoteId);

        if (request.getExpectedVersion() == null || request.getExpectedVersion() < 0) {
            throw new BusinessException("expectedVersion is required for retry-safe approval submission",
                    HttpStatus.BAD_REQUEST, "APPROVAL_VERSION_REQUIRED");
        }
        rampScheduleGuard.refuseIfStale(quote);
        if (quote.getStatus() == QuoteStatus.PENDING_APPROVAL && quote.getApprovalSubmissionId() != null) {
            if (request.getExpectedVersion() != null
                    && !request.getExpectedVersion().equals(quote.getApprovalSubmissionVersion())) {
                throw staleSubmission();
            }
            if (!request.getTier().name().equals(quote.getApprovalTier())) {
                throw new BusinessException("A different approval tier is already pending",
                        HttpStatus.CONFLICT, "APPROVAL_SUBMISSION_CONFLICT");
            }
            // A repeated command must not regress an already committed quote projection.
            return toDtoFromRouting(approvalRoutingClient.getById(quote.getApprovalSubmissionId()));
        }
        if (request.getExpectedVersion() != null && !request.getExpectedVersion().equals(quote.getVersion())) {
            throw staleSubmission();
        }

        if (quote.getStatus() != QuoteStatus.IN_REVIEW && quote.getStatus() != QuoteStatus.DRAFT) {
            throw new BusinessException(
                    "Quote must be in DRAFT or IN_REVIEW to submit for approval",
                    "INVALID_STATUS_TRANSITION"
            );
        }

        String fingerprint = com.opencrm.quote.ramp.RampPricingSnapshot.fingerprint(quote);
        com.opencrm.quote.ramp.RampGovernanceDto governance = com.opencrm.quote.ramp.RampPricingSnapshot.governance(quote);
        if (governance != null) {
            RoutingApprovalDto pending = approvalRoutingClient.getByQuoteId(quoteId).stream()
                    .filter(remote -> fingerprint != null && fingerprint.equals(remote.getPricingFingerprint()))
                    .filter(remote -> "PENDING".equals(remote.getStatus())).findFirst()
                    .orElseThrow(() -> new BusinessException("Current ramp approval is not ready; retry after routing catches up",
                            HttpStatus.CONFLICT, "RAMP_APPROVAL_REQUIRED"));
            quote.setStatus(QuoteStatus.PENDING_APPROVAL);
            quote.setApprovalState(ApprovalState.PENDING);
            quote.setApprovalTier(pending.getApprovalTier());
            quoteRepository.save(quote);
            return toDtoFromRouting(pending);
        }

        QuotePolicyVerdict policyVerdict = quotePolicyEvaluation.evaluate(quote);
        if (policyVerdict.refusesSubmission()) {
            throw new PolicyBlockedException(policyVerdict.summary(), policyVerdict.reasonCodes());
        }

        Long submissionVersion = quote.getVersion();
        RoutingApprovalDto remote = approvalRoutingClient.submit(RoutingApprovalSubmitRequest.builder()
                .idempotencyKey("quote-submit:" + quote.getId() + ":" + quote.getUuid() + ":" + submissionVersion)
                .quoteId(String.valueOf(quoteId))
                .tier(request.getTier().name())
                .reason("Quote approval submitted via BFF")
                .pricingFingerprint(fingerprint)
                .governingPeriod(fingerprint == null ? null : 1)
                .build());

        if (!String.valueOf(quoteId).equals(remote.getQuoteId()) || remote.getId() == null
                || !request.getTier().name().equals(remote.getApprovalTier()) || !"PENDING".equals(remote.getStatus())) {
            throw new BusinessException("Routing returned an incompatible submission",
                    HttpStatus.BAD_GATEWAY, "APPROVAL_ROUTING_INVALID_RESPONSE");
        }
        // A frozen receipt preserves the first response, not the current lifecycle state.
        // Never restore PENDING from that snapshot after routing has already decided it.
        RoutingApprovalDto current = approvalRoutingClient.getById(remote.getId());
        if (current == null || !remote.getId().equals(current.getId())
                || !String.valueOf(quoteId).equals(current.getQuoteId())
                || !request.getTier().name().equals(current.getApprovalTier())
                || !java.util.Set.of("PENDING", "APPROVED", "REJECTED").contains(current.getStatus())) {
            throw new BusinessException("Canonical approval changed; refresh before continuing",
                    HttpStatus.CONFLICT, "APPROVAL_SUBMISSION_STATE_CHANGED");
        }
        quote.setApprovalSubmissionId(remote.getId());
        quote.setApprovalSubmissionVersion(submissionVersion);
        // Single-writer: do NOT persist a local ApprovalRequest on submit.
        quote.setStatus(quoteStatusFor(current.getStatus()));
        quote.setApprovalState(ApprovalState.valueOf(current.getStatus()));
        quote.setApprovalTier(request.getTier().name());
        quoteRepository.save(quote);

        log.info("Quote {} submitted for {} approval (routing id={})",
                quote.getQuoteNumber(), request.getTier(), remote.getId());
        return toDtoFromRouting(current);
    }

    private BusinessException staleSubmission() {
        return new BusinessException("Quote changed since this approval submission",
                HttpStatus.CONFLICT, "STALE_APPROVAL_SUBMISSION");
    }

    public ApprovalRequestDto approveRequest(Long approvalId, ApprovalResponseRequest request) {
        RoutingApprovalDto current = currentRouting(approvalId);
        refuseUndecidable(current);
        try {
            String comments = request == null ? null : request.getComments();
            Long quoteVersion = knownQuoteVersion(current);
            RoutingApprovalDto remote = quoteVersion == null
                    ? approvalRoutingClient.approve(approvalId, comments)
                    : approvalRoutingClient.approve(approvalId, comments, quoteVersion);
            applyQuoteDecision(remote.getQuoteId(), QuoteStatus.APPROVED, ApprovalState.APPROVED);
            log.info("Approval {} approved via routing for quote {} by {}",
                    approvalId, remote.getQuoteId(), remote.getApproverId());
            return toDtoFromRouting(remote);
        } catch (ResourceNotFoundException ex) {
            throw notInRouting(approvalId, ex);
        }
    }

    public ApprovalRequestDto rejectRequest(Long approvalId, ApprovalResponseRequest request) {
        String comments = request != null ? request.getComments() : null;
        RoutingApprovalDto current = currentRouting(approvalId);
        refuseUndecidable(current);
        try {
            Long quoteVersion = knownQuoteVersion(current);
            RoutingApprovalDto remote = quoteVersion == null
                    ? approvalRoutingClient.reject(approvalId, comments)
                    : approvalRoutingClient.reject(approvalId, comments, quoteVersion);
            // A rejection returns the quote to Draft so the seller can revise it. The approval row stays REJECTED.
            applyQuoteDecision(remote.getQuoteId(), QuoteStatus.DRAFT, ApprovalState.REJECTED);
            log.info("Approval {} rejected via routing for quote {} by {}",
                    approvalId, remote.getQuoteId(), remote.getApproverId());
            return toDtoFromRouting(remote);
        } catch (ResourceNotFoundException ex) {
            throw notInRouting(approvalId, ex);
        }
    }

    /**
     * An approval approval-routing does not know. A legacy local row is readable history but cannot be decided here:
     * that would skip approval-routing's role check (ADR-016 decision 7) and write a second store (ADR-013).
     */
    private RuntimeException notInRouting(Long approvalId, ResourceNotFoundException notFound) {
        if (legacyLocalReadEnabled && legacyVisible(approvalId).isPresent()) {
            log.warn("Refused a decision on legacy local approval {}: it predates approval-routing", approvalId);
            return new BusinessException(
                    "Approval " + approvalId + " predates approval-routing and can no longer be decided; "
                            + "submit the quote for approval again",
                    HttpStatus.CONFLICT,
                    "LEGACY_APPROVAL_READ_ONLY"
            );
        }
        return notFound;
    }

    /**
     * Advances a stalled approval after its SLA window. The quote stays pending for the next tier.
     * The write goes to approval-routing; this method only projects the quote.
     */
    public ApprovalRequestDto escalateRequest(Long approvalId) {
        RoutingApprovalDto current = currentRouting(approvalId);
        refuseUndecidable(current);
        try {
            Long quoteVersion = knownQuoteVersion(current);
            RoutingApprovalDto remote = quoteVersion == null
                    ? approvalRoutingClient.escalate(approvalId)
                    : approvalRoutingClient.escalate(approvalId, quoteVersion);
            Long quoteId = parseQuoteId(remote.getQuoteId());
            Quote quote = quoteRepository.findByIdAndDeletedFalse(quoteId)
                    .orElseThrow(() -> new ResourceNotFoundException("Quote", quoteId));
            quote.setStatus(QuoteStatus.PENDING_APPROVAL);
            quote.setApprovalState(ApprovalState.PENDING);
            quote.setApprovalTier(remote.getApprovalTier());
            quoteRepository.save(quote);
            log.info("Approval {} escalated via routing for quote {} to {}",
                    approvalId, remote.getQuoteId(), remote.getApprovalTier());
            return toDtoFromRouting(remote);
        } catch (ResourceNotFoundException ex) {
            throw notInRouting(approvalId, ex);
        }
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequestDto> getByQuoteId(Long quoteId) {
        if (ApprovalAccess.current().enforced()) {
            requireQuote(quoteId);
        }
        List<ApprovalRequestDto> result = new ArrayList<>();
        Set<Long> seenIds = new HashSet<>();

        try {
            for (RoutingApprovalDto remote : approvalRoutingClient.getByQuoteId(quoteId)) {
                // Terminal policy-retirement rows are routing audit records, not approver decisions.
                if (remote.getPricingFingerprint() != null && "CANCELLED".equals(remote.getStatus())) continue;
                if (!String.valueOf(quoteId).equals(remote.getQuoteId())) {
                    continue;
                }
                ApprovalRequestDto dto = toDtoFromRouting(remote);
                if (dto.getId() != null) {
                    seenIds.add(dto.getId());
                }
                result.add(dto);
            }
        } catch (BusinessException ex) {
            log.warn("Dual-read: routing history unavailable for quote {}: {}", quoteId, ex.getMessage());
            if (!legacyLocalReadEnabled) {
                throw ex;
            }
        }

        if (legacyLocalReadEnabled) {
            for (ApprovalRequest local : approvalRepository.findByQuoteIdAndDeletedFalseOrderByCreatedAtDesc(quoteId)) {
                if (local.getId() != null && seenIds.contains(local.getId())) {
                    continue;
                }
                // Prefer routing when both stores have a PENDING for the same quote+tier.
                if (local.getStatus() == ApprovalStatus.PENDING
                        && result.stream().anyMatch(r ->
                        r.getStatus() == ApprovalStatus.PENDING
                                && r.getTier() == local.getTier()
                                && quoteId.equals(r.getQuoteId()))) {
                    continue;
                }
                result.add(toDto(local));
            }
        }

        return result;
    }

    /**
     * GetApprovalHistory for a quote. The lookup is scoped to the caller's tenant ({@code tid}) and
     * account ({@code account_id} equals the quote's customer external id, ADR-016). A miss, another
     * tenant, and another account are the same 404. The role check runs in approval-routing after this,
     * and only for a quote in scope. H4 owns a tenant check on approval-routing itself.
     */
    @Transactional(readOnly = true)
    public List<ApprovalHistoryEntryDto> getApprovalHistory(Long quoteId) {
        Quote quote = quoteInCallerScope(quoteId);
        return approvalRoutingClient.getApprovalHistory(quoteId).stream()
                .filter(row -> String.valueOf(quote.getId()).equals(row.getQuoteId()))
                .map(ApprovalService::toHistory)
                .toList();
    }

    /**
     * Writes a comment or thread reply through approval-routing. The quote version link is the
     * quote's current version, not a value from the request body. Scope misses are the same 404
     * as a missing quote, before approval-routing checks the role.
     */
    public ApprovalHistoryEntryDto addCaseComment(Long quoteId, Long approvalId, ApprovalCommentRequest request) {
        Quote quote = quoteInCallerScope(quoteId);
        RoutingApprovalDto remote = currentRouting(approvalId);
        if (remote == null || !String.valueOf(quoteId).equals(remote.getQuoteId())) {
            throw new ResourceNotFoundException("ApprovalRequest", approvalId);
        }
        RoutingApprovalHistoryEntry saved = approvalRoutingClient.addComment(approvalId,
                RoutingApprovalCommentRequest.builder()
                        .body(request == null ? null : request.getBody())
                        .parentCommentId(request == null ? null : request.getParentCommentId())
                        .quoteVersion(quote.getVersion())
                        .build());
        return toHistory(saved);
    }

    /**
     * Auth context (tid and account_id) first, then the scoped row. The thrown exception matches
     * a missing quote: 404 {@code RESOURCE_NOT_FOUND}, message {@code Quote not found with identifier: id}.
     */
    private Quote quoteInCallerScope(Long quoteId) {
        Quote quote = quoteRepository.findByIdAndDeletedFalse(quoteId).orElse(null);
        if (!visibleToCaller(quote)) {
            if (quote != null) {
                log.info("Hiding quote {} from approval history: caller tenant or account is outside its scope",
                        quoteId);
            }
            throw new ResourceNotFoundException("Quote", quoteId);
        }
        return quote;
    }

    private static boolean visibleToCaller(Quote quote) {
        if (quote == null) {
            return false;
        }
        String tenant = QuoteTenantResolver.currentTenantId();
        String account = QuoteAccountResolver.currentAccountId();
        return tenant != null
                && account != null
                && tenant.equals(normalize(quote.getTenantId()))
                && account.equals(normalize(quote.getCustomerExternalId()));
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    private Long knownQuoteVersion(RoutingApprovalDto remote) {
        if (remote == null || remote.getQuoteId() == null) {
            return null;
        }
        try {
            return quoteRepository.findByIdAndDeletedFalse(parseQuoteId(remote.getQuoteId()))
                    .map(Quote::getVersion)
                    .orElse(null);
        } catch (BusinessException ex) {
            return null;
        }
    }

    private static ApprovalHistoryEntryDto toHistory(RoutingApprovalHistoryEntry row) {
        if (row == null) {
            return null;
        }
        return ApprovalHistoryEntryDto.builder()
                .id(row.getId())
                .approvalId(row.getApprovalId())
                .quoteId(row.getQuoteId())
                .quoteVersion(row.getQuoteVersion())
                .commentId(row.getCommentId())
                .parentCommentId(row.getParentCommentId())
                .kind(row.getKind())
                .eventName(row.getEventName())
                .occurredAt(row.getOccurredAt())
                .actorId(row.getActorId())
                .body(row.getBody())
                .fromTier(row.getFromTier())
                .toTier(row.getToTier())
                .build();
    }

    @Transactional(readOnly = true)
    public List<ApprovalRequestDto> getPendingApprovals() {
        ApprovalAccess.Scope scope = ApprovalAccess.current();
        if (scope.enforced()) {
            return pendingForScope(scope);
        }
        List<ApprovalRequestDto> result = new ArrayList<>();
        Set<String> routingQuoteKeys = new HashSet<>();

        try {
            for (RoutingApprovalDto remote : approvalRoutingClient.getPending()) {
                Optional<ApprovalTier> tier = mapQuoteTier(remote.getApprovalTier());
                if (tier.isEmpty()) {
                    // Skip non-quote tiers (e.g. MANAGER discount Kafka path) from agent BFF queue.
                    continue;
                }
                ApprovalRequestDto dto = toDtoFromRouting(remote);
                result.add(dto);
                if (remote.getQuoteId() != null) {
                    routingQuoteKeys.add(remote.getQuoteId() + "|" + tier.get().name());
                }
            }
        } catch (BusinessException ex) {
            log.warn("Dual-read: routing pending unavailable: {}", ex.getMessage());
            if (!legacyLocalReadEnabled) {
                throw ex;
            }
        }

        if (legacyLocalReadEnabled) {
            for (ApprovalRequest local : approvalRepository.findByStatusAndDeletedFalse(ApprovalStatus.PENDING)) {
                String key = local.getQuoteId() + "|" + local.getTier().name();
                if (routingQuoteKeys.contains(key)) {
                    continue;
                }
                result.add(toDto(local));
            }
        }

        return result;
    }

    private List<ApprovalRequestDto> pendingForScope(ApprovalAccess.Scope scope) {
        List<ApprovalRequestDto> result = new ArrayList<>();
        Set<String> routingQuoteKeys = new HashSet<>();
        List<RoutingApprovalDto> remoteRows = new ArrayList<>();
        try {
            remoteRows.addAll(approvalRoutingClient.getPending());
        } catch (BusinessException ex) {
            log.warn("Dual-read: routing pending unavailable: {}", ex.getMessage());
            if (!legacyLocalReadEnabled) {
                throw ex;
            }
        }
        Map<Long, Quote> visible = visibleQuotes(remoteRows, scope);
        for (RoutingApprovalDto remote : remoteRows) {
            Optional<ApprovalTier> tier = mapQuoteTier(remote.getApprovalTier());
            if (tier.isEmpty()) {
                continue;
            }
            Long quoteId = tryParseQuoteId(remote.getQuoteId());
            if (quoteId == null || !visible.containsKey(quoteId)) {
                continue;
            }
            result.add(toDtoFromRouting(remote));
            routingQuoteKeys.add(remote.getQuoteId() + "|" + tier.get().name());
        }
        if (legacyLocalReadEnabled) {
            for (ApprovalRequest local : approvalRepository.findByStatusForTenant(
                    ApprovalStatus.PENDING, scope.tenantId(), scope.accountId())) {
                String key = local.getQuoteId() + "|" + local.getTier().name();
                if (routingQuoteKeys.contains(key)) {
                    continue;
                }
                result.add(toDto(local));
            }
        }
        return result;
    }

    private void applyQuoteDecision(String quoteIdRaw, QuoteStatus status, ApprovalState state) {
        Long quoteId = parseQuoteId(quoteIdRaw);
        Quote quote = requireQuote(quoteId);
        quote.setStatus(status);
        quote.setApprovalState(state);
        quoteRepository.save(quote);
    }

    private RoutingApprovalDto currentRouting(Long approvalId) {
        try {
            return approvalRoutingClient.getById(approvalId);
        } catch (ResourceNotFoundException ex) {
            throw notInRouting(approvalId, ex);
        }
    }

    /**
     * Checks that a decision can be applied to the quote before approval-routing records it, so the two
     * stores cannot diverge. A quote deleted while its approval was pending is refused here (UAT 2026-11-14:
     * the decision used to be recorded in routing and then fail with 404).
     */
    private void refuseUndecidable(RoutingApprovalDto remote) {
        if (remote == null) return;
        Quote quote = requireQuote(parseQuoteId(remote.getQuoteId()));
        if (remote.getPricingFingerprint() == null) return;
        if (quote.isRampScheduleStale()
                || !remote.getPricingFingerprint().equals(com.opencrm.quote.ramp.RampPricingSnapshot.fingerprint(quote))) {
            throw new BusinessException("Approval belongs to an older ramp pricing snapshot",
                    HttpStatus.CONFLICT, "APPROVAL_SUPERSEDED");
        }
    }

    private Quote requireQuote(Long id) {
        ApprovalAccess.Scope scope = ApprovalAccess.current();
        if (!scope.enforced()) {
            return quoteRepository.findByIdAndDeletedFalse(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Quote", id));
        }
        Optional<Quote> own = quoteRepository.findByIdAndTenantIdAndDeletedFalse(id, scope.tenantId());
        if (own.isPresent()) {
            if (scope.accountId() != null && !scope.accountId().equals(own.get().getCustomerExternalId())) {
                throw new ResourceNotFoundException("Quote", id);
            }
            return own.get();
        }
        // Another tenant's quote, a quote with no tenant, and a missing quote all answer the same 404,
        // so a caller cannot learn which quote ids exist in another tenant (user decision, 2026-11-24).
        throw new ResourceNotFoundException("Quote", id);
    }

    private Optional<ApprovalRequest> legacyVisible(Long id) {
        ApprovalAccess.Scope scope = ApprovalAccess.current();
        if (!scope.enforced()) {
            return approvalRepository.findByIdAndDeletedFalse(id);
        }
        return approvalRepository.findVisibleById(id, scope.tenantId(), scope.accountId());
    }

    private Map<Long, Quote> visibleQuotes(List<RoutingApprovalDto> remoteRows, ApprovalAccess.Scope scope) {
        List<Long> ids = new ArrayList<>();
        for (RoutingApprovalDto remote : remoteRows) {
            Long quoteId = tryParseQuoteId(remote.getQuoteId());
            if (quoteId != null) {
                ids.add(quoteId);
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Quote> visible = new HashMap<>();
        for (Quote quote : quoteRepository.findByIdInAndTenantIdAndDeletedFalse(ids, scope.tenantId())) {
            if (scope.accountId() == null || scope.accountId().equals(quote.getCustomerExternalId())) {
                visible.put(quote.getId(), quote);
            }
        }
        return visible;
    }

    private static Long tryParseQuoteId(String quoteIdRaw) {
        if (quoteIdRaw == null || quoteIdRaw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(quoteIdRaw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Long parseQuoteId(String quoteIdRaw) {
        if (quoteIdRaw == null || quoteIdRaw.isBlank()) {
            throw new BusinessException("Routing approval missing quoteId", "INVALID_ROUTING_APPROVAL");
        }
        try {
            return Long.parseLong(quoteIdRaw.trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException(
                    "Routing approval quoteId is not numeric: " + quoteIdRaw,
                    "INVALID_ROUTING_QUOTE_ID"
            );
        }
    }

    private ApprovalRequestDto toDtoFromRouting(RoutingApprovalDto remote) {
        Long quoteId = null;
        if (remote.getQuoteId() != null) {
            try {
                quoteId = Long.parseLong(remote.getQuoteId().trim());
            } catch (NumberFormatException ignored) {
                // Non-numeric routing quoteIds (discount Kafka) stay null in BFF DTO.
            }
        }

        ApprovalTier tier = mapQuoteTier(remote.getApprovalTier()).orElse(null);
        ApprovalStatus status = mapStatus(remote.getStatus());

        Instant respondedAt = remote.getApprovedAt() != null ? remote.getApprovedAt() : remote.getRejectedAt();
        String comments = remote.getDecisionComment() != null
                ? remote.getDecisionComment()
                : remote.getRejectionReason() != null ? remote.getRejectionReason() : remote.getReason();
        if (status == ApprovalStatus.SUPERSEDED) {
            // Nobody responded: routing replaced it. Say by what, so the history reads without the reason column.
            respondedAt = remote.getSupersededAt();
            comments = "Superseded by request " + remote.getSupersededBy();
        }

        return ApprovalRequestDto.builder()
                .id(remote.getId())
                .uuid(remote.getUuid())
                .quoteId(quoteId)
                .tier(tier)
                .status(status)
                .requestedBy(remote.getRequesterId())
                .approver(remote.getApproverId())
                .comments(comments)
                .pricingFingerprint(remote.getPricingFingerprint())
                .governingPeriod(remote.getGoverningPeriod())
                .requestedDiscount(remote.getRequestedDiscount())
                .reason(remote.getReason())
                .requestedAt(remote.getSubmittedAt())
                .respondedAt(respondedAt)
                .supersededBy(status == ApprovalStatus.SUPERSEDED ? remote.getSupersededBy() : null)
                .createdAt(remote.getCreatedAt())
                .build();
    }

    /**
     * Routing REJECTED is an approval outcome. The quote itself goes back to Draft (DRG-444).
     */
    private static QuoteStatus quoteStatusFor(String routingStatus) {
        if ("PENDING".equals(routingStatus)) {
            return QuoteStatus.PENDING_APPROVAL;
        }
        if ("REJECTED".equals(routingStatus)) {
            return QuoteStatus.DRAFT;
        }
        return QuoteStatus.valueOf(routingStatus);
    }

    private static Optional<ApprovalTier> mapQuoteTier(String approvalTier) {
        if (approvalTier == null || approvalTier.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(ApprovalTier.valueOf(approvalTier.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static ApprovalStatus mapStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if ("CANCELLED".equals(normalized) || "CANCELED".equals(normalized)) {
            return null;
        }
        try {
            return ApprovalStatus.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return null;
        }
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
