package com.opencrm.approval.service;

import com.opencrm.approval.domain.ApprovalMatrix;
import com.opencrm.approval.domain.ApprovalMatrixRule;
import com.opencrm.approval.domain.ApprovalRequest;
import com.opencrm.approval.domain.ApprovalStatus;
import com.opencrm.approval.domain.MatrixStatus;
import com.opencrm.approval.domain.MatrixStepMode;
import com.opencrm.approval.domain.QuoteApprovalTier;
import com.opencrm.approval.dto.ApprovalMatrixDto;
import com.opencrm.approval.dto.ApprovalRequestDto;
import com.opencrm.approval.dto.DefineApprovalMatrixRequest;
import com.opencrm.approval.dto.MatrixRuleDto;
import com.opencrm.approval.dto.MatrixRuleRequest;
import com.opencrm.approval.dto.PolicyBreachRequest;
import com.opencrm.approval.dto.SubmitForApprovalRequest;
import com.opencrm.approval.mapper.ApprovalRequestMapper;
import com.opencrm.approval.repository.ApprovalMatrixRepository;
import com.opencrm.approval.repository.ApprovalRequestRepository;
import com.opencrm.approval.verdict.PolicyBreach;
import com.opencrm.approval.verdict.PolicyVerdict;
import com.opencrm.approval.verdict.QuoteEvaluation;
import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.ApprovalRequestedEvent;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.security.Caller;
import com.opencrm.security.CurrentActor;
import com.opencrm.security.web.EntraRoleAuthorities;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * {@code DefineApprovalMatrix}, {@code SubmitForApproval} and {@code ListPendingApprovals} (BC-04-03, DRG-443).
 * Steps resolve to the existing quote tiers. {@link ApprovalAuthority} remains the only decider rule.
 * Amendment {@code CONTRACT_MODIFICATION} routing stays on the open OQ-560 questions; this service does not add it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ApprovalMatrixService {

    private static final String DEAL_DESK = EntraRoleAuthorities.authorityFor("DealDesk");
    private static final String SALES_VP = EntraRoleAuthorities.authorityFor("SalesVP");
    private static final String CFO = EntraRoleAuthorities.authorityFor("CFO");
    private static final String CPQ_USER = EntraRoleAuthorities.authorityFor("CPQ.User");
    private static final String ADMIN = EntraRoleAuthorities.authorityFor("Admin");

    private static final Set<String> DEFINERS = Set.of(DEAL_DESK, SALES_VP, CFO, ADMIN);
    private static final Set<String> SUBMITTERS = Set.of(CPQ_USER, DEAL_DESK, SALES_VP, CFO);
    private static final Set<ApprovalStatus> OPEN = EnumSet.of(ApprovalStatus.PENDING, ApprovalStatus.WAITING);

    private final ApprovalMatrixRepository approvalMatrixRepository;
    private final ApprovalRequestRepository approvalRequestRepository;
    private final ApprovalRequestMapper approvalRequestMapper;
    private final OutboxEventPublisher outboxEventPublisher;
    private final CurrentActor currentActor;

    public ApprovalMatrixDto define(DefineApprovalMatrixRequest request) {
        Caller.User user = requireUser("Defining an approval matrix");
        requireRole(user, DEFINERS, "MATRIX_ROLE_REQUIRED", "Defining an approval matrix");
        String tenantId = requireTenant();
        validateRules(request.getRules());

        ApprovalMatrix predecessor = null;
        int nextVersion = 1;
        if (request.getSupersedesMatrixId() != null) {
            predecessor = requireMatrix(request.getSupersedesMatrixId());
            if (!tenantId.equals(predecessor.getTenantId())) {
                throw crossTenant();
            }
            if (predecessor.getStatus() != MatrixStatus.ACTIVE) {
                throw new BusinessException("Only an ACTIVE matrix version can be revised",
                        HttpStatus.CONFLICT, "MATRIX_NOT_ACTIVE");
            }
            nextVersion = (predecessor.getMatrixVersion() == null ? 1 : predecessor.getMatrixVersion()) + 1;
        }

        ApprovalMatrix matrix = ApprovalMatrix.builder()
                .name(request.getName().trim())
                .status(MatrixStatus.ACTIVE)
                .matrixVersion(nextVersion)
                .supersedesId(predecessor == null ? null : predecessor.getId())
                .tenantId(tenantId)
                .build();
        for (MatrixRuleRequest rule : request.getRules()) {
            matrix.addRule(toRule(rule));
        }
        ApprovalMatrix saved = approvalMatrixRepository.save(matrix);
        if (predecessor != null) {
            predecessor.deprecate();
            approvalMatrixRepository.save(predecessor);
        }
        log.info("Defined approval matrix id={} version={} tenant={}", saved.getId(), saved.getMatrixVersion(), tenantId);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public ApprovalMatrixDto getById(Long id) {
        Caller.User user = requireUser("Reading an approval matrix");
        requireRole(user, DEFINERS, "MATRIX_ROLE_REQUIRED", "Reading an approval matrix");
        ApprovalMatrix matrix = requireMatrix(id);
        if (!requireTenant().equals(matrix.getTenantId())) {
            throw crossTenant();
        }
        return toDto(matrix);
    }

    /**
     * {@code SubmitForApproval}. Pass and Blocked create nothing. RequiresApproval creates one request per resolved
     * role. The first step is {@link ApprovalStatus#PENDING}; later steps are {@link ApprovalStatus#WAITING}.
     */
    public List<ApprovalRequestDto> submitForApproval(SubmitForApprovalRequest request) {
        Caller.User user = requireUser("Submitting for approval");
        requireRole(user, SUBMITTERS, "REQUESTER_ROLE_REQUIRED", "Submitting for approval");
        String tenantId = requireTenant();
        if (!tenantId.equals(request.getQuoteTenantId().trim())) {
            throw crossTenant();
        }
        QuoteEvaluation evaluation = new QuoteEvaluation(
                request.getQuoteId().trim(),
                request.getQuoteVersion().trim(),
                request.getVerdict(),
                toBreaches(request.getBreaches()));
        if (evaluation.verdict() == PolicyVerdict.PASS) {
            throw new BusinessException("The verdict is Pass; nothing is submitted",
                    HttpStatus.CONFLICT, "NO_APPROVAL_REQUIRED");
        }
        if (evaluation.verdict() == PolicyVerdict.BLOCKED) {
            throw new BusinessException("The verdict is Blocked; the quote is not routed for approval",
                    HttpStatus.CONFLICT, "POLICY_BLOCKED");
        }
        if (evaluation.verdict() != PolicyVerdict.REQUIRES_APPROVAL) {
            throw new BusinessException("Unsupported verdict", "INVALID_VERDICT");
        }

        List<ApprovalRequest> open = approvalRequestRepository
                .findByQuoteIdAndDeletedFalseAndStatusIn(evaluation.quoteId(), OPEN).stream()
                .filter(row -> tenantId.equals(row.getTenantId()))
                .toList();
        if (!open.isEmpty()) {
            boolean sameVersion = open.stream().allMatch(row -> evaluation.quoteVersion().equals(row.getQuoteVersion()));
            if (!sameVersion) {
                throw new BusinessException("An approval route is already open for this quote",
                        HttpStatus.CONFLICT, "APPROVAL_ALREADY_OPEN");
            }
            return approvalRequestMapper.toDtoList(open);
        }

        ApprovalMatrix matrix = selectMatrix(tenantId, request.getMatrixId());
        ApprovalMatrixResolver.ResolvedRoute route = ApprovalMatrixResolver.resolve(
                matrix.getId(),
                matrix.getMatrixVersion(),
                matrix.getRules().stream().map(ApprovalMatrixService::toResolverRule).toList(),
                evaluation.breaches());

        int firstOrder = route.steps().stream().mapToInt(ApprovalMatrixResolver.ResolvedStep::order).min().orElseThrow();
        List<ApprovalRequest> created = new ArrayList<>();
        for (ApprovalMatrixResolver.ResolvedStep step : route.steps()) {
            ApprovalStatus status = step.order() == firstOrder ? ApprovalStatus.PENDING : ApprovalStatus.WAITING;
            String breaches = String.join(",", step.breachTypes());
            for (QuoteApprovalTier role : step.roles()) {
                QuoteApprovalTierMapper.MappedApproval mapped = QuoteApprovalTierMapper.map(role);
                ApprovalRequest entity = ApprovalRequest.builder()
                        .quoteId(evaluation.quoteId())
                        .quoteVersion(evaluation.quoteVersion())
                        .approvalType(mapped.approvalType())
                        .status(status)
                        .tenantId(tenantId)
                        .matrixId(matrix.getId())
                        .matrixVersion(matrix.getMatrixVersion())
                        .routingStep(step.order())
                        .stepMode(step.mode())
                        .breachTypes(breaches)
                        .approvalTier(mapped.approvalTier())
                        .requesterId(user.actorId())
                        .submittedAt(Instant.now())
                        .reason("Matrix v" + matrix.getMatrixVersion() + " step " + step.order()
                                + " " + step.mode() + ": " + breaches)
                        .build();
                created.add(approvalRequestRepository.save(entity));
            }
        }

        publishRequested(evaluation, matrix, created);
        log.info("Submitted quote {} version {} on matrix {} v{} as {} requests",
                evaluation.quoteId(), evaluation.quoteVersion(), matrix.getId(), matrix.getMatrixVersion(), created.size());
        return approvalRequestMapper.toDtoList(created);
    }

    /**
     * {@code ListPendingApprovals} for the caller. Only {@link ApprovalStatus#PENDING} rows in the caller's tenant
     * whose tier {@link ApprovalAuthority} allows them to decide. {@link ApprovalStatus#WAITING} stays out of the inbox.
     */
    @Transactional(readOnly = true)
    public List<ApprovalRequestDto> listPendingApprovals(String tier, String requestedTenantId) {
        Caller.User user = requireUser("Listing pending approvals");
        String tenantId = requireTenant();
        if (requestedTenantId != null && !requestedTenantId.isBlank() && !tenantId.equals(requestedTenantId.trim())) {
            throw crossTenant();
        }
        if (tier != null && !tier.isBlank() && !ApprovalAuthority.mayDecide(user.authorities(), tier)) {
            throw new BusinessException("Listing a " + tier.trim() + " queue needs a role that may decide it",
                    HttpStatus.FORBIDDEN, "APPROVER_ROLE_REQUIRED");
        }
        List<ApprovalRequest> rows = (tier == null || tier.isBlank())
                ? approvalRequestRepository.findPendingVisibleToTenant(tenantId)
                : approvalRequestRepository.findPendingVisibleToTenantAndTier(tenantId, tier.trim().toUpperCase(Locale.ROOT));
        List<ApprovalRequest> mine = rows.stream()
                .filter(row -> tenantId.equals(row.getTenantId()))
                .filter(row -> ApprovalAuthority.mayDecide(user.authorities(), row.getApprovalTier()))
                .sorted(Comparator.comparing(ApprovalRequest::getSubmittedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ApprovalRequest::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        return approvalRequestMapper.toDtoList(mine);
    }

    private ApprovalMatrix selectMatrix(String tenantId, Long matrixId) {
        if (matrixId != null) {
            ApprovalMatrix matrix = requireMatrix(matrixId);
            if (!tenantId.equals(matrix.getTenantId())) {
                throw crossTenant();
            }
            if (matrix.getStatus() != MatrixStatus.ACTIVE) {
                throw new BusinessException("Approval matrix " + matrixId + " is not active",
                        HttpStatus.CONFLICT, "MATRIX_NOT_ACTIVE");
            }
            return matrix;
        }
        List<ApprovalMatrix> active = approvalMatrixRepository
                .findByTenantIdAndStatusAndDeletedFalse(tenantId, MatrixStatus.ACTIVE);
        if (active.isEmpty()) {
            throw new ResourceNotFoundException("ApprovalMatrix", "active for tenant");
        }
        if (active.size() > 1) {
            throw new BusinessException("More than one active approval matrix; name the matrix to bind",
                    HttpStatus.CONFLICT, "MATRIX_AMBIGUOUS");
        }
        return active.get(0);
    }

    private void publishRequested(QuoteEvaluation evaluation, ApprovalMatrix matrix, List<ApprovalRequest> created) {
        List<ApprovalRequestedEvent.RequestedStep> steps = created.stream()
                .map(row -> new ApprovalRequestedEvent.RequestedStep(
                        row.getId(),
                        row.getRoutingStep(),
                        row.getStepMode().name(),
                        row.getApprovalTier(),
                        row.getStatus().name(),
                        row.getBreachTypes()))
                .toList();
        ApprovalRequestedEvent event = new ApprovalRequestedEvent(
                evaluation.quoteId(),
                evaluation.quoteVersion(),
                matrix.getId(),
                matrix.getMatrixVersion(),
                matrix.getTenantId(),
                steps);
        outboxEventPublisher.publish(
                "ApprovalRequest",
                evaluation.quoteId(),
                EventContractRegistry.APPROVAL_REQUESTED_V1,
                event);
    }

    private ApprovalMatrix requireMatrix(Long id) {
        return approvalMatrixRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalMatrix", id));
    }

    private Caller.User requireUser(String action) {
        return currentActor.caller()
                .filter(Caller.User.class::isInstance)
                .map(Caller.User.class::cast)
                .orElseThrow(() -> new BusinessException(action + " is done by a person, not by a service",
                        currentActor.caller().isEmpty() ? HttpStatus.UNAUTHORIZED : HttpStatus.FORBIDDEN,
                        currentActor.caller().isEmpty() ? "AUTHENTICATION_REQUIRED" : "REQUESTER_MUST_BE_A_USER"));
    }

    private static void requireRole(Caller.User user, Set<String> allowed, String code, String action) {
        if (user.authorities().stream().noneMatch(allowed::contains)) {
            throw new BusinessException(action + " needs one of the roles " + allowed,
                    HttpStatus.FORBIDDEN, code);
        }
    }

    private static String requireTenant() {
        String tenantId = ApprovalTenancy.currentTenantId();
        if (tenantId == null) {
            throw new BusinessException("A tenant claim is required", HttpStatus.UNAUTHORIZED, "TENANT_REQUIRED");
        }
        return tenantId;
    }

    private static BusinessException crossTenant() {
        return new BusinessException("The quote tenant does not match the caller",
                HttpStatus.FORBIDDEN, "CROSS_TENANT");
    }

    private static void validateRules(List<MatrixRuleRequest> rules) {
        if (rules == null || rules.isEmpty()) {
            throw new BusinessException("At least one matrix rule is required", "EMPTY_APPROVAL_MATRIX");
        }
        for (int i = 0; i < rules.size(); i++) {
            MatrixRuleRequest rule = rules.get(i);
            if (rule.getAmountMin() != null && rule.getAmountMax() != null
                    && rule.getAmountMin().compareTo(rule.getAmountMax()) >= 0) {
                throw new BusinessException("amountMin must be less than amountMax", "INVALID_MATRIX_BAND");
            }
            if (rule.getStepMode() == MatrixStepMode.SERIAL) {
                long siblings = rules.stream()
                        .filter(other -> sameBand(rule, other) && rule.getStepOrder().equals(other.getStepOrder()))
                        .count();
                if (siblings > 1) {
                    throw new BusinessException("A serial step has one role", "MATRIX_STEP_CONFLICT");
                }
            }
            for (int j = i + 1; j < rules.size(); j++) {
                MatrixRuleRequest other = rules.get(j);
                if (rule.getBreachType() != other.getBreachType()) {
                    continue;
                }
                if (ApprovalMatrixResolver.bandsOverlap(rule.getAmountMin(), rule.getAmountMax(),
                        other.getAmountMin(), other.getAmountMax())
                        && !(sameBand(rule, other))) {
                    throw new BusinessException("Amount bands overlap for " + rule.getBreachType(),
                            "MATRIX_BAND_OVERLAP");
                }
                if (sameBand(rule, other) && rule.getStepOrder().equals(other.getStepOrder())
                        && rule.getStepMode() != other.getStepMode()) {
                    throw new BusinessException("Step " + rule.getStepOrder() + " mixes serial and parallel",
                            "MATRIX_STEP_CONFLICT");
                }
            }
        }
    }

    private static boolean sameBand(MatrixRuleRequest left, MatrixRuleRequest right) {
        return left.getBreachType() == right.getBreachType()
                && compare(left.getAmountMin(), right.getAmountMin())
                && compare(left.getAmountMax(), right.getAmountMax());
    }

    private static boolean compare(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }
        return left.compareTo(right) == 0;
    }

    private static ApprovalMatrixRule toRule(MatrixRuleRequest request) {
        return ApprovalMatrixRule.builder()
                .breachType(request.getBreachType().name())
                .amountMin(request.getAmountMin())
                .amountMax(request.getAmountMax())
                .stepOrder(request.getStepOrder())
                .stepMode(request.getStepMode())
                .approverRole(request.getApproverRole())
                .build();
    }

    private static ApprovalMatrixResolver.Rule toResolverRule(ApprovalMatrixRule rule) {
        return new ApprovalMatrixResolver.Rule(
                rule.getBreachType(),
                rule.getAmountMin(),
                rule.getAmountMax(),
                rule.getStepOrder(),
                rule.getStepMode(),
                rule.getApproverRole());
    }

    private static List<PolicyBreach> toBreaches(List<PolicyBreachRequest> breaches) {
        if (breaches == null) {
            return List.of();
        }
        return breaches.stream()
                .map(row -> new PolicyBreach(row.getBreachType(), row.getActual(), row.getLimit(),
                        row.getAmount(), row.getClauseCode()))
                .toList();
    }

    private static ApprovalMatrixDto toDto(ApprovalMatrix matrix) {
        List<MatrixRuleDto> rules = matrix.getRules() == null ? List.of() : matrix.getRules().stream()
                .map(rule -> MatrixRuleDto.builder()
                        .id(rule.getId())
                        .breachType(rule.getBreachType())
                        .amountMin(rule.getAmountMin())
                        .amountMax(rule.getAmountMax())
                        .stepOrder(rule.getStepOrder())
                        .stepMode(rule.getStepMode())
                        .approverRole(rule.getApproverRole())
                        .build())
                .toList();
        return ApprovalMatrixDto.builder()
                .id(matrix.getId())
                .uuid(matrix.getUuid())
                .name(matrix.getName())
                .status(matrix.getStatus())
                .matrixVersion(matrix.getMatrixVersion())
                .supersedesMatrixId(matrix.getSupersedesId())
                .tenantId(matrix.getTenantId())
                .rules(rules)
                .createdAt(matrix.getCreatedAt())
                .build();
    }
}
