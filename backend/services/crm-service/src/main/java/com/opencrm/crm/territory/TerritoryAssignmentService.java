package com.opencrm.crm.territory;

import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.TerritoryAssignedEvent;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.common.valueobject.AccountId;
import com.opencrm.crm.organization.Organization;
import com.opencrm.crm.organization.OrganizationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Assigns a territory and a segment from rules, and reads the segment back.
 * The only TerritoryAssigned publish site is {@link #publishAssigned}.
 */
@Service
@Transactional
public class TerritoryAssignmentService {

    private static final int DEFAULT_PRIORITY = 100;

    private final OrganizationRepository organizationRepository;
    private final TerritoryAssignmentRuleRepository ruleRepository;
    private final OrganizationTerritoryAssignmentRepository assignmentRepository;
    private final OutboxEventPublisher outboxEventPublisher;
    private final Clock clock;

    @Autowired
    public TerritoryAssignmentService(OrganizationRepository organizationRepository,
                                      TerritoryAssignmentRuleRepository ruleRepository,
                                      OrganizationTerritoryAssignmentRepository assignmentRepository,
                                      OutboxEventPublisher outboxEventPublisher) {
        this(organizationRepository, ruleRepository, assignmentRepository, outboxEventPublisher, Clock.systemUTC());
    }

    TerritoryAssignmentService(OrganizationRepository organizationRepository,
                               TerritoryAssignmentRuleRepository ruleRepository,
                               OrganizationTerritoryAssignmentRepository assignmentRepository,
                               OutboxEventPublisher outboxEventPublisher,
                               Clock clock) {
        this.organizationRepository = organizationRepository;
        this.ruleRepository = ruleRepository;
        this.assignmentRepository = assignmentRepository;
        this.outboxEventPublisher = outboxEventPublisher;
        this.clock = clock;
    }

    public TerritoryRuleResponse createRule(TerritoryRuleRequest request) {
        if (!request.isEmployeeRangeOrdered()) {
            throw new BusinessException(
                    "minEmployeeCount must not exceed maxEmployeeCount", "INVALID_TERRITORY_RULE");
        }
        TerritoryAssignmentRule rule = TerritoryAssignmentRule.builder()
                .name(required(request.getName(), "Name"))
                .country(required(request.getCountry(), "Country"))
                .state(TerritoryAssignmentRules.normalize(request.getState()))
                .industry(required(request.getIndustry(), "Industry"))
                .minEmployeeCount(request.getMinEmployeeCount())
                .maxEmployeeCount(request.getMaxEmployeeCount())
                .territoryCode(required(request.getTerritoryCode(), "Territory code"))
                .segmentCode(required(request.getSegmentCode(), "Segment code"))
                .priority(request.getPriority() == null ? DEFAULT_PRIORITY : request.getPriority())
                .active(true)
                .build();
        return toResponse(ruleRepository.save(rule));
    }

    @Transactional(readOnly = true)
    public List<TerritoryRuleResponse> listRules() {
        return ruleRepository.findByDeletedFalseOrderByPriorityAscIdAsc().stream()
                .map(TerritoryAssignmentService::toResponse)
                .toList();
    }

    /**
     * AssignTerritory and AssignSegment. A full geography, industry, and size match stores both values
     * and emits one TerritoryAssigned event when the assignment is made or changed. No match leaves the
     * organization unassigned and emits nothing.
     */
    public OrganizationSegmentResponse assign(Long organizationId) {
        Organization organization = requireActive(organizationId);
        Optional<TerritoryAssignmentRule> match = TerritoryAssignmentRules.select(
                facts(organization), ruleRepository.findApplicable());
        Optional<OrganizationTerritoryAssignment> current =
                assignmentRepository.findByOrganizationId(organization.getId());

        if (match.isEmpty()) {
            current.filter(row -> !row.isDeleted()).ifPresent(row -> {
                row.softDelete();
                assignmentRepository.save(row);
            });
            return unassigned(organization);
        }

        TerritoryAssignmentRule rule = match.get();
        OrganizationTerritoryAssignment row = current.orElseGet(OrganizationTerritoryAssignment::new);
        if (!assignmentChanged(row, rule)) {
            return assigned(organization, row);
        }

        Instant assignedAt = clock.instant();
        row.setOrganizationId(organization.getId());
        row.setTerritoryCode(rule.getTerritoryCode());
        row.setSegmentCode(rule.getSegmentCode());
        row.setRuleId(rule.getId());
        row.setAssignedAt(assignedAt);
        row.setDeleted(false);
        OrganizationTerritoryAssignment saved = assignmentRepository.save(row);
        publishAssigned(organization, saved);
        return assigned(organization, saved);
    }

    /** GetSegment. A missing organization is not found. An existing organization may be unassigned. */
    @Transactional(readOnly = true)
    public OrganizationSegmentResponse getSegment(Long organizationId) {
        Organization organization = requireActive(organizationId);
        return assignmentRepository.findByOrganizationId(organization.getId())
                .filter(row -> !row.isDeleted())
                .map(row -> assigned(organization, row))
                .orElseGet(() -> unassigned(organization));
    }

    private void publishAssigned(Organization organization, OrganizationTerritoryAssignment saved) {
        outboxEventPublisher.publish(
                "Organization",
                organization.getUuid(),
                EventContractRegistry.TERRITORY_ASSIGNED_V1,
                new TerritoryAssignedEvent(
                        accountId(organization),
                        saved.getTerritoryCode(),
                        saved.getSegmentCode(),
                        saved.getRuleId(),
                        saved.getAssignedAt()));
    }

    private static boolean assignmentChanged(OrganizationTerritoryAssignment row, TerritoryAssignmentRule rule) {
        if (row.getId() == null || row.isDeleted()) {
            return true;
        }
        return !rule.getTerritoryCode().equals(row.getTerritoryCode())
                || !rule.getSegmentCode().equals(row.getSegmentCode())
                || rule.getId() == null
                || !rule.getId().equals(row.getRuleId());
    }

    private Organization requireActive(Long organizationId) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", organizationId));
        if (organization.isDeleted()) {
            throw new ResourceNotFoundException("Organization", organizationId);
        }
        return organization;
    }

    private static AssignmentFacts facts(Organization organization) {
        return new AssignmentFacts(
                organization.getCountry(),
                organization.getState(),
                organization.getIndustry(),
                organization.getEmployeeCount());
    }

    private static OrganizationSegmentResponse assigned(Organization organization,
                                                        OrganizationTerritoryAssignment row) {
        return OrganizationSegmentResponse.builder()
                .organizationId(accountId(organization))
                .territory(row.getTerritoryCode())
                .segment(row.getSegmentCode())
                .ruleId(row.getRuleId())
                .assigned(true)
                .build();
    }

    private static OrganizationSegmentResponse unassigned(Organization organization) {
        return OrganizationSegmentResponse.builder()
                .organizationId(accountId(organization))
                .assigned(false)
                .build();
    }

    private static String accountId(Organization organization) {
        return AccountId.of(organization.getUuid()).value();
    }

    private static TerritoryRuleResponse toResponse(TerritoryAssignmentRule rule) {
        return TerritoryRuleResponse.builder()
                .id(rule.getId())
                .name(rule.getName())
                .country(rule.getCountry())
                .state(rule.getState())
                .industry(rule.getIndustry())
                .minEmployeeCount(rule.getMinEmployeeCount())
                .maxEmployeeCount(rule.getMaxEmployeeCount())
                .territoryCode(rule.getTerritoryCode())
                .segmentCode(rule.getSegmentCode())
                .priority(rule.getPriority())
                .active(rule.isActive())
                .build();
    }

    private static String required(String value, String field) {
        String normalized = TerritoryAssignmentRules.normalize(value);
        if (normalized == null) {
            throw new BusinessException(field + " is required", "INVALID_TERRITORY_RULE");
        }
        return normalized;
    }
}
