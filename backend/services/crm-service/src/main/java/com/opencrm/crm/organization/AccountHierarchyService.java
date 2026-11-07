package com.opencrm.crm.organization;

import com.opencrm.common.event.EventActorResolver;
import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.HierarchyChangedEvent;
import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.common.valueobject.AccountId;
import lombok.extern.slf4j.Slf4j;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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
public class AccountHierarchyService {

    /** Bounds a corrupt cycle that the seen-set did not catch. */
    private static final int MAX_DEPTH = 64;

    private final OrganizationRepository organizationRepository;
    private final OutboxEventPublisher outboxEventPublisher;
    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;
    private final AccountHierarchyChangeRepository hierarchyChangeRepository;
    private final EventActorResolver eventActorResolver;
    private final boolean readyForTests;

    /** Production has no configuration or provider that can enable hierarchy. */
    @Autowired
    public AccountHierarchyService(OrganizationRepository organizationRepository, OutboxEventPublisher outboxEventPublisher,
                                   JdbcTemplate jdbc, EntityManager entityManager,
                                   AccountHierarchyChangeRepository hierarchyChangeRepository,
                                   EventActorResolver eventActorResolver) {
        this(organizationRepository, outboxEventPublisher, jdbc, entityManager,
                hierarchyChangeRepository, eventActorResolver, false);
    }

    /** Package-private seam used only by the isolated hierarchy implementation tests. */
    AccountHierarchyService(OrganizationRepository organizationRepository, OutboxEventPublisher outboxEventPublisher,
                            JdbcTemplate jdbc, EntityManager entityManager,
                            AccountHierarchyChangeRepository hierarchyChangeRepository,
                            EventActorResolver eventActorResolver, boolean readyForTests) {
        this.organizationRepository = organizationRepository;
        this.outboxEventPublisher = outboxEventPublisher;
        this.jdbc = jdbc;
        this.entityManager = entityManager;
        this.hierarchyChangeRepository = hierarchyChangeRepository;
        this.eventActorResolver = eventActorResolver;
        this.readyForTests = readyForTests;
    }

    private void requireReady() {
        if (!readyForTests) throw new BusinessException("Account hierarchy is not available", HttpStatus.SERVICE_UNAVAILABLE, "HIERARCHY_UNAVAILABLE");
    }
    private static final long HIERARCHY_LOCK = 0x4849455241524348L;

    /** All parent writes and deletes serialize before reading hierarchy state. */
    void lockHierarchy() {
        jdbc.query("select pg_advisory_xact_lock(?)", rs -> null, HIERARCHY_LOCK);
    }

    void flushAfterLock() { entityManager.flush(); }

    void refreshAfterLock(Organization organization) {
        entityManager.refresh(organization);
        if (organization.isDeleted()) throw new ResourceNotFoundException("Organization", organization.getId());
    }


    /**
     * Point {@code childId} at {@code parentOrganizationId}. Null clears the parent.
     * An unchanged parent does not emit an event. A self-parent or any ancestor loop is rejected
     * and leaves the row unchanged.
     */
    @CacheEvict(value = "organizations", key = "#childId.value()")
    public AccountHierarchyDto linkToParent(AccountId childId, Long parentOrganizationId) {
        requireReady();
        lockHierarchy();
        // A caller may share this transaction and have pending edits; flush them before refresh.
        // If validation later fails, the enclosing transaction rolls the flush back.
        entityManager.flush();
        Organization child = requireActive(childId);
        refreshAfterLock(child);
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
        Instant decidedAt = Instant.now();
        String decidedBy = decidedBy();
        // Same transaction as the parent write and HierarchyChanged outbox (no REQUIRES_NEW).
        hierarchyChangeRepository.save(AccountHierarchyChange.builder()
                .childOrganizationId(saved.getId())
                .childAccountId(saved.getUuid())
                .previousParentOrganizationId(previousParentOrganizationId)
                .newParentOrganizationId(parentOrganizationId)
                .decidedBy(decidedBy)
                .decidedAt(decidedAt)
                .build());
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
                        decidedAt));
        log.info("Linked organization {} to parent {} decided by {}", saved.getUuid(), parentOrganizationId, decidedBy);
        return toDto(saved, ancestorsNearestFirst);
    }

    /**
     * Chain from the root down to this organization, plus ancestor account ids for inheritance.
     */
    @Transactional(readOnly = true)
    public AccountHierarchyDto resolveHierarchy(AccountId organizationId) {
        requireReady();
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
        List<AccountHierarchyNodeDto> ancestorNodes = chain.subList(0, chain.size() - 1);
        List<AccountHierarchyNodeDto> children = organizationRepository
                .findByParentOrganizationIdAndDeletedFalseOrderByIdAsc(organization.getId()).stream()
                .map(AccountHierarchyService::toNode).toList();
        return AccountHierarchyDto.builder()
                .parent(ancestorsNearestFirst.isEmpty() ? null : toNode(ancestorsNearestFirst.getFirst()))
                .ancestors(List.copyOf(ancestorNodes))
                .children(children)
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
        entityManager.refresh(organization);
        if (organization.isDeleted()) {
            throw new BusinessException("Cannot link to deleted organization", "ORGANIZATION_DELETED");
        }
        return organization;
    }

    private static BusinessException cycle(String message) {
        return new BusinessException(message, "HIERARCHY_CYCLE");
    }

    /**
     * Same source pattern as {@code AccountMergeService.decidedBy()}; duplicated intentionally
     * (DRG-714 allowlist — do not extract a shared helper in this PR).
     */
    private String decidedBy() {
        String actor = eventActorResolver.currentActor();
        if (actor == null || actor.isBlank()) {
            throw new BusinessException("Account hierarchy change requires an actor", "HIERARCHY_ACTOR_REQUIRED");
        }
        return actor;
    }
}
