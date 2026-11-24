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
import com.opencrm.approval.domain.PolicyVerdict;
import com.opencrm.approval.dto.MatrixRuleDto;
import com.opencrm.approval.dto.MatrixRuleRequest;
import com.opencrm.approval.dto.MatrixStepDto;
import com.opencrm.approval.dto.PolicyBreachRequest;
import com.opencrm.approval.dto.SubmitForApprovalRequest;
import com.opencrm.approval.mapper.ApprovalRequestMapper;
import com.opencrm.approval.repository.ApprovalMatrixRepository;
import com.opencrm.approval.repository.ApprovalRequestRepository;
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
import java.util.LinkedHashSet;
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
    private static final Set<ApprovalStatus> OPEN = EnumSet.of(ApprovalStatus.PENDING);

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
            predecessor = requireTenantMatrix(request.getSupersedesMatrixId(), tenantId);
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
        ApprovalMatrix matrix = requireTenantMatrix(id, requireTenant());
        return toDto(matrix);
    }

    /**
     * {@code SubmitForApproval}. The matrix chooses every approver role. Pass creates nothing and emits nothing.
     * Blocked is refused. RequiresApproval creates one {@link ApprovalStatus#PENDING} request per resolved role,
     * including later serial steps.
     */
    public List<ApprovalRequestDto> submitForApproval(SubmitForApprovalRequest request) {
        Caller.User user = requireUser("Submitting for approval");
        requireRole(user, SUBMITTERS, "REQUESTER_ROLE_REQUIRED", "Submitting for approval");
        String tenantId = requireTenant();
        if (!tenantId.equals(request.getQuoteTenantId().trim())) {
            throw crossTenant();
        }
        String quoteId = request.getQuoteId().trim();
        String quoteVersion = request.getQuoteVersion().trim();
        ApprovalMatrix matrix = selectMatrix(tenantId, request.getMatrixId(), request.getMatrixVersion());
        if (request.getVerdict() == PolicyVerdict.PASS) {
            return List.of();
        }
        if (request.getVerdict() == PolicyVerdict.BLOCKED) {
            throw new BusinessException("The verdict is Blocked; the quote is not routed for approval",
                    HttpStatus.CONFLICT, "POLICY_BLOCKED");
        }
        if (request.getVerdict() != PolicyVerdict.REQUIRES_APPROVAL) {
            throw new BusinessException("Unsupported verdict", "INVALID_VERDICT");
        }

        List<ApprovalRequest> open = approvalRequestRepository
                .findByQuoteIdAndDeletedFalseAndStatusIn(quoteId, OPEN).stream()
                .filter(row -> tenantId.equals(row.getTenantId()))
                .toList();
        if (!open.isEmpty()) {
            boolean sameVersion = open.stream().allMatch(row -> quoteVersion.equals(row.getQuoteVersion()));
            if (!sameVersion) {
                throw new BusinessException("An approval route is already open for this quote",
                        HttpStatus.CONFLICT, "APPROVAL_ALREADY_OPEN");
            }
            return approvalRequestMapper.toDtoList(open);
        }

        ApprovalMatrixResolver.ResolvedRoute route = ApprovalMatrixResolver.resolve(
                matrix.getId(),
                matrix.getMatrixVersion(),
                matrix.getRules().stream().map(ApprovalMatrixService::toResolverRule).toList(),
                toBreaches(request.getBreaches()));

        List<ApprovalRequest> created = new ArrayList<>();
        for (ApprovalMatrixResolver.ResolvedStep step : route.steps()) {
            String breaches = String.join(",", step.breachTypes());
            for (QuoteApprovalTier role : step.roles()) {
                QuoteApprovalTierMapper.MappedApproval mapped = QuoteApprovalTierMapper.map(role);
                ApprovalRequest entity = ApprovalRequest.builder()
                        .quoteId(quoteId)
                        .quoteVersion(quoteVersion)
                        .approvalType(mapped.approvalType())
                        .status(ApprovalStatus.PENDING)
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

        publishRequested(quoteId, quoteVersion, matrix, created);
        log.info("Submitted quote {} version {} on matrix {} v{} as {} requests",
                quoteId, quoteVersion, matrix.getId(), matrix.getMatrixVersion(), created.size());
        return approvalRequestMapper.toDtoList(created);
    }

    /**
     * {@code ListPendingApprovals(approver)}. The queue is that role's pending rows only. A higher role does not
     * see another role's items. The caller is not choosing a tier for a new route.
     */
    @Transactional(readOnly = true)
    public List<ApprovalRequestDto> listPendingApprovals(QuoteApprovalTier approver) {
        if (approver == null) {
            throw new BusinessException("Approver role is required", "APPROVER_ROLE_REQUIRED");
        }
        String tenantId = ApprovalTenancy.currentTenantId();
        List<ApprovalRequest> rows = tenantId == null
                ? approvalRequestRepository.findByStatus(ApprovalStatus.PENDING)
                : approvalRequestRepository.findPendingVisibleToTenant(tenantId);
        List<ApprovalRequest> mine = rows.stream()
                .filter(row -> approver.name().equals(row.getApprovalTier()))
                .filter(row -> tenantId == null || tenantId.equals(row.getTenantId()))
                .sorted(Comparator.comparing(ApprovalRequest::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        return approvalRequestMapper.toDtoList(mine);
    }

    /**
     * Caller inbox. A person sees pending rows only for the approver roles they hold, in their tenant.
     * Holding a higher role does not reveal another role's queue. Asking for a role they do not hold is refused.
     */
    @Transactional(readOnly = true)
    public List<ApprovalRequestDto> listPendingApprovals(String tier, String requestedTenantId) {
        Caller.User user = requireUser("Listing pending approvals");
        String tenantId = requireTenant();
        if (requestedTenantId != null && !requestedTenantId.isBlank() && !tenantId.equals(requestedTenantId.trim())) {
            throw crossTenant();
        }
        Set<String> held = exactRoles(user);
        if (tier != null && !tier.isBlank() && !held.contains(tier.trim().toUpperCase(Locale.ROOT))) {
            throw new BusinessException("Listing a " + tier.trim() + " queue needs that approver role",
                    HttpStatus.FORBIDDEN, "APPROVER_ROLE_REQUIRED");
        }
        String requested = tier == null || tier.isBlank() ? null : tier.trim().toUpperCase(Locale.ROOT);
        List<ApprovalRequest> rows = approvalRequestRepository.findPendingVisibleToTenant(tenantId);
        List<ApprovalRequest> mine = rows.stream()
                .filter(row -> tenantId.equals(row.getTenantId()))
                .filter(row -> held.contains(row.getApprovalTier()))
                .filter(row -> requested == null || requested.equals(row.getApprovalTier()))
                .sorted(Comparator.comparing(ApprovalRequest::getSubmittedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ApprovalRequest::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        return approvalRequestMapper.toDtoList(mine);
    }

    private ApprovalMatrix selectMatrix(String tenantId, Long matrixId, Integer matrixVersion) {
        ApprovalMatrix matrix = selectMatrix(tenantId, matrixId);
        if (matrixVersion != null && !matrixVersion.equals(matrix.getMatrixVersion())) {
            throw new BusinessException("Approval matrix version does not match",
                    HttpStatus.CONFLICT, "MATRIX_VERSION_MISMATCH");
        }
        return matrix;
    }

    private ApprovalMatrix selectMatrix(String tenantId, Long matrixId) {
        if (matrixId != null) {
            ApprovalMatrix matrix = requireTenantMatrix(matrixId, tenantId);
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

    private void publishRequested(String quoteId, String quoteVersion, ApprovalMatrix matrix, List<ApprovalRequest> created) {
        List<Long> ids = created.stream().map(ApprovalRequest::getId).toList();
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
                quoteId,
                quoteVersion,
                matrix.getId(),
                matrix.getMatrixVersion(),
                matrix.getTenantId(),
                ids,
                steps);
        outboxEventPublisher.publish(
                "ApprovalRequest",
                quoteId,
                EventContractRegistry.APPROVAL_REQUESTED_V1,
                event);
    }

    private static Set<String> exactRoles(Caller.User user) {
        Set<String> held = new LinkedHashSet<>();
        if (user.authorities().contains(DEAL_DESK)) {
            held.add("DEAL_DESK");
        }
        if (user.authorities().contains(SALES_VP)) {
            held.add("SALES_VP");
        }
        if (user.authorities().contains(CFO)) {
            held.add("CFO");
        }
        return held;
    }

    private ApprovalMatrix requireMatrix(Long id) {
        return approvalMatrixRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("ApprovalMatrix", id));
    }

    /**
     * The matrix with this id in the caller's tenant. A matrix of another tenant answers exactly like a missing id
     * (404), so a matrix id lookup is not an oracle for which ids exist in other tenants (H4 owner decision,
     * 2026-11-24). A tenant parameter that does not match the token stays 403 {@code CROSS_TENANT}: see
     * {@link #crossTenant()}.
     */
    private ApprovalMatrix requireTenantMatrix(Long id, String tenantId) {
        return approvalMatrixRepository.findByIdAndDeletedFalse(id)
                .filter(matrix -> tenantId.equals(matrix.getTenantId()))
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

    /** For a tenant request parameter that differs from the token tenant. It touches no stored row. */
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

    private static List<ApprovalMatrixResolver.RoutedBreach> toBreaches(List<PolicyBreachRequest> breaches) {
        if (breaches == null) {
            return List.of();
        }
        return breaches.stream()
                .map(row -> new ApprovalMatrixResolver.RoutedBreach(row.getBreachType(), row.getAmount()))
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
                .steps(groupSteps(matrix.getRules()))
                .createdAt(matrix.getCreatedAt())
                .build();
    }

    private static List<MatrixStepDto> groupSteps(List<ApprovalMatrixRule> rules) {
        List<MatrixStepDto> steps = new ArrayList<>();
        if (rules == null) {
            return steps;
        }
        for (ApprovalMatrixRule rule : rules) {
            MatrixStepDto current = steps.isEmpty() ? null : steps.get(steps.size() - 1);
            if (current != null
                    && current.getSequence().equals(rule.getStepOrder())
                    && current.getMode() == rule.getStepMode()
                    && sameAmount(current.getMinAmountInclusive(), rule.getAmountMin())
                    && sameAmount(current.getMaxAmountExclusive(), rule.getAmountMax())) {
                current.getRoles().add(rule.getApproverRole());
            } else {
                List<QuoteApprovalTier> roles = new ArrayList<>();
                roles.add(rule.getApproverRole());
                steps.add(MatrixStepDto.builder()
                        .sequence(rule.getStepOrder())
                        .mode(rule.getStepMode())
                        .roles(roles)
                        .minAmountInclusive(rule.getAmountMin())
                        .maxAmountExclusive(rule.getAmountMax())
                        .build());
            }
        }
        return steps;
    }

    private static boolean sameAmount(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }
        return left.compareTo(right) == 0;
    }
}
