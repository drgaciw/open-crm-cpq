package com.opencrm.crm.organization;

import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.common.valueobject.AccountId;
import com.opencrm.crm.contact.ContactDto;
import com.opencrm.crm.contact.ContactService;
import com.opencrm.crm.outbox.CrmDomainEvents;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
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

    /**
     * Create a new organization.
     */
    public OrganizationDto create(OrganizationCreateRequest request) {
        log.debug("Creating new organization: {}", request.getName());

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
    @Cacheable(value = "organizations", key = "#id.value()")
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

        Organization organization = requireActive(id);

        // Update entity with non-null fields
        organizationMapper.updateEntity(request, organization);

        // Save and return
        Organization updated = organizationRepository.save(organization);
        log.info("Updated organization with ID: {}", updated.getId());

        return organizationMapper.toDto(updated);
    }

    /**
     * Soft delete an organization.
     */
    @CacheEvict(value = "organizations", key = "#id.value()")
    public void delete(AccountId id) {
        log.debug("Deleting organization with ID: {}", id.value());

        Organization organization = requireActive(id);
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
        Long rawId = id.value();
        Organization organization = organizationRepository.findById(rawId)
            .orElseThrow(() -> new ResourceNotFoundException("Organization", rawId));
        if (organization.isDeleted()) {
            throw new ResourceNotFoundException("Organization", rawId);
        }
        return organization;
    }
}
