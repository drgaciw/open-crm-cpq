package com.opencrm.crm.organization;

import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.HierarchyChangedEvent;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.common.valueobject.AccountId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Links an organization to a parent and resolves the chain above it.
 * Account is the organization row; there is no separate account service.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class AccountHierarchyService {

    /** Bounds a corrupt cycle that the seen-set did not catch. */
    private static final int MAX_DEPTH = 64;

    private final OrganizationRepository organizationRepository;
    private final OutboxEventPublisher outboxEventPublisher;

    /**
     * Point {@code childId} at {@code parentOrganizationId}. Null clears the parent.
     * An unchanged parent does not emit an event. A self-parent or any ancestor loop is rejected
     * and leaves the row unchanged.
     */
    @CacheEvict(value = "organizations", key = "#childId.value()")
    public AccountHierarchyDto linkToParent(AccountId childId, Long parentOrganizationId) {
        Organization child = requireActive(childId);
        Long previousParentOrganizationId = child.getParentOrganizationId();
        if (Objects.equals(previousParentOrganizationId, parentOrganizationId)) {
            return describe(child);
        }

        String parentAccountId = null;
        List<Organization> ancestorsNearestFirst = List.of();
        if (parentOrganizationId != null) {
            if (parentOrganizationId.equals(child.getId())) {
                throw cycle("Organization cannot be its own parent");
            }
            Organization parent = requireActiveById(parentOrganizationId);
            ancestorsNearestFirst = ancestorsFrom(parent);
            for (Organization ancestor : ancestorsNearestFirst) {
                if (ancestor.getId().equals(child.getId())) {
                    throw cycle("Linking this parent would create a cycle");
                }
            }
            parentAccountId = parent.getUuid();
        }

        child.setParentOrganizationId(parentOrganizationId);
        Organization saved = organizationRepository.save(child);
        List<String> ancestorIds = ancestorsNearestFirst.stream()
                .map(Organization::getUuid)
                .toList();
        outboxEventPublisher.publish(
                "Organization",
                saved.getUuid(),
                EventContractRegistry.HIERARCHY_CHANGED_V1,
                new HierarchyChangedEvent(
                        saved.getUuid(),
                        parentOrganizationId,
                        parentAccountId,
                        previousParentOrganizationId,
                        ancestorIds,
                        Instant.now()));
        log.info("Linked organization {} to parent {}", saved.getUuid(), parentOrganizationId);
        return toDto(saved, ancestorsNearestFirst);
    }

    /**
     * Chain from the root down to this organization, plus ancestor account ids for inheritance.
     */
    @Transactional(readOnly = true)
    public AccountHierarchyDto resolveHierarchy(AccountId organizationId) {
        return describe(requireActive(organizationId));
    }

    private AccountHierarchyDto describe(Organization organization) {
        List<Organization> ancestors = List.of();
        if (organization.getParentOrganizationId() != null) {
            ancestors = ancestorsFrom(requireActiveById(organization.getParentOrganizationId()));
        }
        return toDto(organization, ancestors);
    }

    /**
     * Parent first, then each ancestor up to the root.
     */
    private List<Organization> ancestorsFrom(Organization start) {
        List<Organization> ancestors = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Organization cursor = start;
        while (cursor != null) {
            Long id = cursor.getId();
            if (id == null || !seen.add(id)) {
                throw cycle("Organization hierarchy contains a cycle");
            }
            ancestors.add(cursor);
            if (ancestors.size() > MAX_DEPTH) {
                throw cycle("Organization hierarchy contains a cycle");
            }
            Long parentId = cursor.getParentOrganizationId();
            if (parentId == null) {
                return ancestors;
            }
            if (parentId.equals(id)) {
                throw cycle("Organization cannot be its own parent");
            }
            cursor = requireActiveById(parentId);
        }
        return ancestors;
    }

    private AccountHierarchyDto toDto(Organization organization, List<Organization> ancestorsNearestFirst) {
        List<AccountHierarchyNodeDto> chain = new ArrayList<>();
        for (int i = ancestorsNearestFirst.size() - 1; i >= 0; i--) {
            chain.add(toNode(ancestorsNearestFirst.get(i)));
        }
        chain.add(toNode(organization));
        List<String> ancestorIds = ancestorsNearestFirst.stream()
                .map(Organization::getUuid)
                .toList();
        return AccountHierarchyDto.builder()
                .organizationId(organization.getId())
                .accountId(organization.getUuid())
                .parentOrganizationId(organization.getParentOrganizationId())
                .ancestorIds(ancestorIds)
                .chain(chain)
                .build();
    }

    private static AccountHierarchyNodeDto toNode(Organization organization) {
        return AccountHierarchyNodeDto.builder()
                .id(organization.getId())
                .accountId(organization.getUuid())
                .name(organization.getName())
                .parentOrganizationId(organization.getParentOrganizationId())
                .build();
    }

    private Organization requireActive(AccountId id) {
        Organization organization = organizationRepository.findByUuidIgnoreCase(id.value())
                .orElseThrow(() -> new ResourceNotFoundException("Organization", id.value()));
        if (organization.isDeleted()) {
            throw new ResourceNotFoundException("Organization", id.value());
        }
        return organization;
    }

    private Organization requireActiveById(Long id) {
        Organization organization = organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", id));
        if (organization.isDeleted()) {
            throw new BusinessException("Cannot link to deleted organization", "ORGANIZATION_DELETED");
        }
        return organization;
    }

    private static BusinessException cycle(String message) {
        return new BusinessException(message, "HIERARCHY_CYCLE");
    }
}
