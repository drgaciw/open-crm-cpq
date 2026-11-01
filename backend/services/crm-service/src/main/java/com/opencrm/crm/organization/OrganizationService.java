package com.opencrm.crm.organization;

import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.common.valueobject.AccountId;
import com.opencrm.crm.contact.ContactDto;
import com.opencrm.crm.contact.ContactService;
import com.opencrm.crm.outbox.CrmDomainEvents;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service layer for Organization domain operations.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMapper organizationMapper;
    private final ContactService contactService;
    private final CrmDomainEvents domainEvents;
    private final AccountHierarchyService accountHierarchyService;

    /** Resolve the legacy numeric API ID without exposing it as a cross-service account ID. */
    @Transactional(readOnly = true)
    public AccountId resolveAccountId(Long legacyId) {
        Organization organization = organizationRepository.findById(legacyId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", legacyId));
        if (organization.isDeleted()) {
            throw new ResourceNotFoundException("Organization", legacyId);
        }
        return AccountId.of(organization.getUuid());
    }

    /**
     * Create a new organization.
     */
    public OrganizationDto create(OrganizationCreateRequest request) {
        log.debug("Creating new organization: {}", request.getName());

        if (request.getParentOrganizationId() != null) throw hierarchyUnavailable();
        // Convert request to entity
        Organization organization = organizationMapper.toEntity(request);

        // Save and return
        Organization saved = organizationRepository.save(organization);
        domainEvents.organizationCreated(saved);
        log.info("Created organization with ID: {} and UUID: {}", saved.getId(), saved.getUuid());

        return organizationMapper.toDto(saved);
    }

    /**
     * Get organization by ID.
     */
    @Transactional(readOnly = true)
    public OrganizationDto getById(AccountId id) {
        log.debug("Fetching organization by ID: {}", id.value());
        return organizationMapper.toDto(requireActive(id));
    }

    /**
     * Get organization by UUID.
     */
    @Transactional(readOnly = true)
    public OrganizationDto getByUuid(String uuid) {
        log.debug("Fetching organization by UUID: {}", uuid);

        Organization organization = organizationRepository.findByUuid(uuid)
            .orElseThrow(() -> new ResourceNotFoundException("Organization", uuid));

        if (organization.isDeleted()) {
            throw new ResourceNotFoundException("Organization", uuid);
        }

        return organizationMapper.toDto(organization);
    }

    /**
     * Update an existing organization.
     */
    @CacheEvict(value = "organizations", key = "#id.value()")
    public OrganizationDto update(AccountId id, OrganizationUpdateRequest request) {
        log.debug("Updating organization with ID: {}", id.value());

        if (request.getParentOrganizationId() != null) throw hierarchyUnavailable();
        Organization organization = requireActive(id);

        // Update entity with non-null fields
        organizationMapper.updateEntity(request, organization);

        Organization updated = organizationRepository.save(organization);
        log.info("Updated organization with ID: {}", updated.getId());

        return organizationMapper.toDto(updated);
    }

    private static com.opencrm.common.exception.BusinessException hierarchyUnavailable() {
        return new com.opencrm.common.exception.BusinessException("Account hierarchy is not available", org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "HIERARCHY_UNAVAILABLE");
    }

    /**
     * Soft delete an organization.
     */
    @CacheEvict(value = "organizations", key = "#id.value()")
    public void delete(AccountId id) {
        log.debug("Deleting organization with ID: {}", id.value());

        accountHierarchyService.lockHierarchy();
        accountHierarchyService.flushAfterLock();
        Organization organization = requireActive(id);
        accountHierarchyService.refreshAfterLock(organization);
        if (organizationRepository.existsByParentOrganizationIdAndDeletedFalse(organization.getId())) {
            throw new com.opencrm.common.exception.BusinessException("This account has active child accounts. Hierarchy management is unavailable; contact an administrator", "HIERARCHY_HAS_CHILDREN");
        }
        organization.softDelete();
        organizationRepository.save(organization);
        log.info("Soft deleted organization with ID: {}", id.value());
    }

    /**
     * List organizations with optional type filter.
     */
    @Transactional(readOnly = true)
    public Page<OrganizationDto> list(Pageable pageable, OrganizationType type) {
        log.debug("Listing organizations with type filter: {}", type);

        Page<Organization> organizations;
        if (type != null) {
            organizations = organizationRepository.findByTypeAndDeletedFalse(type, pageable);
        } else {
            organizations = organizationRepository.findAllByDeletedFalse(pageable);
        }

        return organizations.map(organizationMapper::toDto);
    }

    /**
     * Search organizations by name.
     */
    @Transactional(readOnly = true)
    public Page<OrganizationDto> search(String query, Pageable pageable) {
        log.debug("Searching organizations with query: {}", query);

        Page<Organization> organizations = organizationRepository.searchByName(query, pageable);
        return organizations.map(organizationMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Page<OrganizationDto> filter(String query, OrganizationType type, String industry,
                                        String territory, String ownerId, Pageable pageable) {
        return organizationRepository.findAll(
                OrganizationFilters.matching(query, type, industry, territory, ownerId), pageable)
                .map(organizationMapper::toDto);
    }

    /**
     * Get contacts belonging to an organization.
     */
    @Transactional(readOnly = true)
    public Page<ContactDto> getContacts(AccountId organizationId, Pageable pageable) {
        log.debug("Fetching contacts for organization ID: {}", organizationId.value());
        requireActive(organizationId);
        return contactService.getByOrganization(organizationId, pageable);
    }

    private Organization requireActive(AccountId id) {
        String rawId = id.value();
        Organization organization = organizationRepository.findByUuidIgnoreCase(rawId)
            .orElseThrow(() -> new ResourceNotFoundException("Organization", rawId));
        if (organization.isDeleted()) {
            throw new ResourceNotFoundException("Organization", rawId);
        }
        return organization;
    }
}
