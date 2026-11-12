package com.opencrm.approval.service;

import com.opencrm.approval.domain.CommercialPolicy;
import com.opencrm.approval.domain.PolicyStatus;
import com.opencrm.approval.domain.PolicyThreshold;
import com.opencrm.approval.domain.ThresholdType;
import com.opencrm.approval.dto.CommercialPolicyCreateRequest;
import com.opencrm.approval.dto.CommercialPolicyDto;
import com.opencrm.approval.dto.PolicyThresholdRequest;
import com.opencrm.approval.mapper.CommercialPolicyMapper;
import com.opencrm.approval.repository.CommercialPolicyRepository;
import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.PolicyDefinedEvent;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@code DefinePolicy} command and read access for commercial policies (BC-04-01, DRG-441): a declarative, versioned
 * set of {@link PolicyThreshold}s on discount %, margin floor, term and non-standard clauses, scoped by segment,
 * product family and region. Public API returns {@link CommercialPolicyDto} only — never the JPA entity (ADR-006).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CommercialPolicyService {

    private final CommercialPolicyRepository commercialPolicyRepository;
    private final CommercialPolicyMapper commercialPolicyMapper;
    private final OutboxEventPublisher outboxEventPublisher;

    /**
     * DefinePolicy: validates and persists a new commercial policy version with its thresholds, then emits
     * {@code PolicyDefined}. When {@code supersedesPolicyId} is set, the earlier ACTIVE version is deprecated in the
     * same transaction and the new version's number continues its lineage.
     */
    public CommercialPolicyDto define(CommercialPolicyCreateRequest request) {
        validateRequest(request);

        CommercialPolicy predecessor = null;
        int nextVersion = 1;
        if (request.getSupersedesPolicyId() != null) {
            predecessor = requirePolicy(request.getSupersedesPolicyId());
            int predecessorVersion = predecessor.getPolicyVersion() == null ? 1 : predecessor.getPolicyVersion();
            nextVersion = predecessorVersion + 1;
        }

        CommercialPolicy policy = CommercialPolicy.builder()
                .name(requireNonBlank(request.getName(), "name is required", "INVALID_POLICY_NAME"))
                .description(request.getDescription())
                .status(PolicyStatus.ACTIVE)
                .policyVersion(nextVersion)
                .supersedesId(predecessor != null ? predecessor.getId() : null)
                .segment(normalize(request.getSegment()))
                .productFamily(normalize(request.getProductFamily()))
                .region(normalize(request.getRegion()))
                .effectiveDate(request.getEffectiveDate())
                .expirationDate(request.getExpirationDate())
                .build();

        for (PolicyThresholdRequest thresholdRequest : request.getThresholds()) {
            policy.addThreshold(toThresholdEntity(thresholdRequest));
        }

        CommercialPolicy saved = commercialPolicyRepository.save(policy);

        if (predecessor != null && predecessor.getStatus() == PolicyStatus.ACTIVE) {
            predecessor.deprecate();
            commercialPolicyRepository.save(predecessor);
        }

        publishPolicyDefined(saved);

        log.info("Defined commercial policy id={} version={} segment={} productFamily={} region={} supersedes={}",
                saved.getId(), saved.getPolicyVersion(), saved.getSegment(), saved.getProductFamily(),
                saved.getRegion(), saved.getSupersedesId());

        return commercialPolicyMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public CommercialPolicyDto getById(Long id) {
        return commercialPolicyMapper.toDto(requirePolicy(id));
    }

    @Transactional(readOnly = true)
    public Page<CommercialPolicyDto> list(Pageable pageable) {
        return commercialPolicyRepository.findByDeletedFalse(pageable).map(commercialPolicyMapper::toDto);
    }

    /**
     * ACTIVE policies applicable to the given scope. A null policy field means "applies to all"; a null argument
     * matches everything (used to list every policy for a dimension the caller does not know yet).
     */
    @Transactional(readOnly = true)
    public List<CommercialPolicyDto> findApplicable(String segment, String productFamily, String region) {
        return commercialPolicyRepository.findByStatusAndDeletedFalse(PolicyStatus.ACTIVE).stream()
                .filter(policy -> matchesScope(policy.getSegment(), segment))
                .filter(policy -> matchesScope(policy.getProductFamily(), productFamily))
                .filter(policy -> matchesScope(policy.getRegion(), region))
                .map(commercialPolicyMapper::toDto)
                .toList();
    }

    /** Retires a policy version directly, without a replacement. */
    public CommercialPolicyDto deprecate(Long id) {
        CommercialPolicy policy = requirePolicy(id);
        policy.deprecate();
        CommercialPolicy saved = commercialPolicyRepository.save(policy);
        log.info("Deprecated commercial policy id={} version={}", saved.getId(), saved.getPolicyVersion());
        return commercialPolicyMapper.toDto(saved);
    }

    private void publishPolicyDefined(CommercialPolicy policy) {
        List<PolicyDefinedEvent.ThresholdDefined> thresholds = policy.getThresholds().stream()
                .map(threshold -> new PolicyDefinedEvent.ThresholdDefined(
                        threshold.getThresholdType().name(),
                        threshold.getLimitValue(),
                        threshold.getClauseCode(),
                        threshold.getDescription()))
                .toList();

        PolicyDefinedEvent event = new PolicyDefinedEvent(
                policy.getId(),
                policy.getUuid(),
                policy.getName(),
                policy.getPolicyVersion(),
                policy.getSupersedesId(),
                policy.getSegment(),
                policy.getProductFamily(),
                policy.getRegion(),
                policy.getEffectiveDate(),
                policy.getExpirationDate(),
                thresholds);

        outboxEventPublisher.publish(
                "CommercialPolicy",
                String.valueOf(policy.getId()),
                EventContractRegistry.POLICY_DEFINED_V1,
                event
        );
    }

    private static boolean matchesScope(String policyScopeValue, String requested) {
        return policyScopeValue == null || requested == null || policyScopeValue.equalsIgnoreCase(requested);
    }

    private CommercialPolicy requirePolicy(Long id) {
        return commercialPolicyRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("CommercialPolicy", id));
    }

    private PolicyThreshold toThresholdEntity(PolicyThresholdRequest request) {
        validateThreshold(request);
        return PolicyThreshold.builder()
                .thresholdType(request.getThresholdType())
                .limitValue(request.getLimitValue())
                .clauseCode(normalize(request.getClauseCode()))
                .description(request.getDescription())
                .build();
    }

    private void validateRequest(CommercialPolicyCreateRequest request) {
        if (request == null) {
            throw new BusinessException("Commercial policy definition is required", "INVALID_POLICY_DEFINITION");
        }
        if (request.getThresholds() == null || request.getThresholds().isEmpty()) {
            throw new BusinessException("At least one threshold is required", "MISSING_POLICY_THRESHOLDS");
        }
    }

    private void validateThreshold(PolicyThresholdRequest request) {
        if (request.getThresholdType() == null) {
            throw new BusinessException("thresholdType is required", "INVALID_THRESHOLD_TYPE");
        }
        ThresholdType type = request.getThresholdType();
        if (type == ThresholdType.DISCOUNT_PERCENT) {
            requireRange(request.getLimitValue(), "discount % threshold", 0, 100);
        } else if (type == ThresholdType.MARGIN_FLOOR) {
            requireRange(request.getLimitValue(), "margin floor threshold", 0, 100);
        } else if (type == ThresholdType.TERM_MONTHS) {
            requirePositive(request.getLimitValue(), "term (months) threshold");
        } else if (type == ThresholdType.NON_STANDARD_CLAUSE) {
            if (normalize(request.getClauseCode()) == null) {
                throw new BusinessException(
                        "clauseCode is required for a NON_STANDARD_CLAUSE threshold",
                        "MISSING_CLAUSE_CODE"
                );
            }
        }
    }

    private void requireRange(BigDecimal value, String label, int min, int max) {
        if (value == null) {
            throw new BusinessException(label + " is required", "MISSING_THRESHOLD_LIMIT");
        }
        if (value.compareTo(BigDecimal.valueOf(min)) < 0 || value.compareTo(BigDecimal.valueOf(max)) > 0) {
            throw new BusinessException(
                    label + " must be between " + min + " and " + max,
                    "INVALID_THRESHOLD_LIMIT"
            );
        }
    }

    private void requirePositive(BigDecimal value, String label) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(label + " must be a positive value", "INVALID_THRESHOLD_LIMIT");
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String requireNonBlank(String value, String message, String errorCode) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new BusinessException(message, errorCode);
        }
        return normalized;
    }
}
