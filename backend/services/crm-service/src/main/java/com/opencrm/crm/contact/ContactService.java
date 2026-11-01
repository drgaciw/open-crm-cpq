package com.opencrm.crm.contact;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.common.valueobject.AccountId;
import com.opencrm.crm.organization.Organization;
import com.opencrm.crm.organization.OrganizationAccountIds;
import com.opencrm.crm.organization.OrganizationRepository;
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
 * Service layer for Contact domain operations.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ContactService {

    private final ContactRepository contactRepository;
    private final OrganizationRepository organizationRepository;
    private final ContactMapper contactMapper;
    private final CrmDomainEvents domainEvents;

    /**
     * Create a new contact.
     */
    public ContactDto create(ContactCreateRequest request) {
        log.debug("Creating new contact: {}", request.getEmail());

        // Validate email uniqueness if provided
        if (request.getEmail() != null && !request.getEmail().isEmpty()) {
            if (contactRepository.existsByEmailIgnoreCaseAndDeletedFalse(request.getEmail())) {
                throw new BusinessException(
                    "Contact with email '" + request.getEmail() + "' already exists",
                    "CONTACT_EMAIL_EXISTS"
                );
            }
        }

        // Convert request to entity
        Contact contact = contactMapper.toEntity(request);

        if (request.getOrganizationId() != null) {
            contact.setOrganization(requireActiveOrganization(OrganizationAccountIds.fromExternalId(request.getOrganizationId(), organizationRepository)));
        }

        // Save and return
        Contact saved = contactRepository.save(contact);
        domainEvents.contactCreated(saved);
        log.info("Created contact with ID: {} and UUID: {}", saved.getId(), saved.getUuid());

        return contactMapper.toDto(saved);
    }

    /**
     * Get contact by ID.
     */
    @Cacheable(value = "contacts", key = "#id")
    @Transactional(readOnly = true)
    public ContactDto getById(Long id) {
        log.debug("Fetching contact by ID: {}", id);

        Contact contact = contactRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Contact", id));

        if (contact.isDeleted()) {
            throw new ResourceNotFoundException("Contact", id);
        }

        return contactMapper.toDto(contact);
    }

    /**
     * Get contact by UUID.
     */
    @Transactional(readOnly = true)
    public ContactDto getByUuid(String uuid) {
        log.debug("Fetching contact by UUID: {}", uuid);

        Contact contact = contactRepository.findByUuid(uuid)
            .orElseThrow(() -> new ResourceNotFoundException("Contact", uuid));

        if (contact.isDeleted()) {
            throw new ResourceNotFoundException("Contact", uuid);
        }

        return contactMapper.toDto(contact);
    }

    /**
     * Update an existing contact.
     */
    @CacheEvict(value = "contacts", key = "#id")
    public ContactDto update(Long id, ContactUpdateRequest request) {
        log.debug("Updating contact with ID: {}", id);

        // Find existing contact
        Contact contact = contactRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Contact", id));

        if (contact.isDeleted()) {
            throw new ResourceNotFoundException("Contact", id);
        }

        // Validate email uniqueness if being updated
        if (request.getEmail() != null && !request.getEmail().isEmpty()) {
            if (!request.getEmail().equalsIgnoreCase(contact.getEmail())) {
                if (contactRepository.existsByEmailIgnoreCaseAndDeletedFalse(request.getEmail())) {
                    throw new BusinessException(
                        "Contact with email '" + request.getEmail() + "' already exists",
                        "CONTACT_EMAIL_EXISTS"
                    );
                }
            }
        }

        if (request.getOrganizationId() != null) {
            AccountId accountId = OrganizationAccountIds.fromExternalId(request.getOrganizationId(), organizationRepository);
            if (contact.getOrganization() == null ||
                !accountId.equals(AccountId.of(contact.getOrganization().getUuid()))) {
                contact.setOrganization(requireActiveOrganization(accountId));
            }
        }

        // Update entity with non-null fields
        contactMapper.updateEntity(request, contact);

        // Save and return
        Contact updated = contactRepository.save(contact);
        log.info("Updated contact with ID: {}", updated.getId());

        return contactMapper.toDto(updated);
    }

    /**
     * Soft delete a contact.
     */
    @CacheEvict(value = "contacts", key = "#id")
    public void delete(Long id) {
        log.debug("Deleting contact with ID: {}", id);

        Contact contact = contactRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Contact", id));

        if (contact.isDeleted()) {
            throw new ResourceNotFoundException("Contact", id);
        }

        contact.softDelete();
        contactRepository.save(contact);
        log.info("Soft deleted contact with ID: {}", id);
    }

    /**
     * List contacts with optional status filter.
     */
    @Transactional(readOnly = true)
    public Page<ContactDto> list(Pageable pageable, ContactStatus status) {
        log.debug("Listing contacts with status filter: {}", status);

        Page<Contact> contacts;
        if (status != null) {
            contacts = contactRepository.findByStatusAndDeletedFalse(status, pageable);
        } else {
            contacts = contactRepository.findAllByDeletedFalse(pageable);
        }

        return contacts.map(contactMapper::toDto);
    }

    /**
     * Search contacts by name.
     */
    @Transactional(readOnly = true)
    public Page<ContactDto> search(String query, Pageable pageable) {
        log.debug("Searching contacts with query: {}", query);

        Page<Contact> contacts = contactRepository.searchByName(query, pageable);
        return contacts.map(contactMapper::toDto);
    }

    /**
     * Get contacts by organization.
     */
    @Transactional(readOnly = true)
    public Page<ContactDto> getByOrganization(AccountId accountId, Pageable pageable) {
        log.debug("Fetching contacts for organization ID: {}", accountId.value());

        Organization organization = organizationRepository.findByUuidIgnoreCase(accountId.value())
                .orElseThrow(() -> new ResourceNotFoundException("Organization", accountId.value()));
        Long organizationId = organization.getId();

        Page<Contact> contacts = contactRepository.findByOrganizationIdAndDeletedFalse(organizationId, pageable);
        return contacts.map(contactMapper::toDto);
    }

    private Organization requireActiveOrganization(AccountId accountId) {
        Organization organization = organizationRepository.findByUuidIgnoreCase(accountId.value())
            .orElseThrow(() -> new ResourceNotFoundException("Organization", accountId.value()));
        if (organization.isDeleted()) {
            throw new BusinessException("Cannot link to deleted organization", "ORGANIZATION_DELETED");
        }
        return organization;
    }
}
