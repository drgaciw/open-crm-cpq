package com.opencrm.crm.lead;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.crm.contact.Contact;
import com.opencrm.crm.contact.ContactRepository;
import com.opencrm.crm.deal.Deal;
import com.opencrm.crm.deal.DealRepository;
import com.opencrm.crm.deal.DealStage;
import com.opencrm.crm.deal.DealType;
import com.opencrm.crm.organization.Organization;
import com.opencrm.crm.organization.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Service for managing leads.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class LeadService {

    private final LeadRepository leadRepository;
    private final ContactRepository contactRepository;
    private final OrganizationRepository organizationRepository;
    private final DealRepository dealRepository;
    private final LeadMapper leadMapper;

    /**
     * Create a new lead.
     */
    public LeadDto create(LeadCreateRequest request) {
        log.debug("Creating lead with title: {}", request.getTitle());

        Lead lead = leadMapper.toEntity(request);

        // Set contact if provided
        if (request.getContactId() != null) {
            Contact contact = contactRepository.findById(request.getContactId())
                    .orElseThrow(() -> new ResourceNotFoundException("Contact", request.getContactId()));
            lead.setContact(contact);

            // If contact has an organization and no organizationId is provided, use contact's organization
            if (request.getOrganizationId() == null && contact.getOrganization() != null) {
                lead.setOrganization(contact.getOrganization());
            }
        }

        // Set organization if provided
        if (request.getOrganizationId() != null) {
            Organization org = organizationRepository.findById(request.getOrganizationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Organization", request.getOrganizationId()));
            lead.setOrganization(org);
        }

        Lead saved = leadRepository.save(lead);
        log.info("Created lead with ID: {}", saved.getId());

        return leadMapper.toDto(saved);
    }

    /**
     * Get lead by ID.
     */
    @Transactional(readOnly = true)
    public LeadDto getById(Long id) {
        log.debug("Fetching lead by ID: {}", id);

        Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lead", id));

        if (lead.isDeleted()) {
            throw new ResourceNotFoundException("Lead", id);
        }

        return leadMapper.toDto(lead);
    }

    /**
     * Get lead by UUID.
     */
    @Transactional(readOnly = true)
    public LeadDto getByUuid(String uuid) {
        log.debug("Fetching lead by UUID: {}", uuid);

        Lead lead = leadRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Lead", uuid));

        if (lead.isDeleted()) {
            throw new ResourceNotFoundException("Lead", uuid);
        }

        return leadMapper.toDto(lead);
    }

    /**
     * Update a lead.
     */
    public LeadDto update(Long id, LeadUpdateRequest request) {
        log.debug("Updating lead ID: {}", id);

        Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lead", id));

        if (lead.isDeleted()) {
            throw new ResourceNotFoundException("Lead", id);
        }

        leadMapper.updateEntity(request, lead);

        // Update contact if provided
        if (request.getContactId() != null) {
            Contact contact = contactRepository.findById(request.getContactId())
                    .orElseThrow(() -> new ResourceNotFoundException("Contact", request.getContactId()));
            lead.setContact(contact);
        }

        // Update organization if provided
        if (request.getOrganizationId() != null) {
            Organization org = organizationRepository.findById(request.getOrganizationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Organization", request.getOrganizationId()));
            lead.setOrganization(org);
        }

        Lead updated = leadRepository.save(lead);
        log.info("Updated lead ID: {}", updated.getId());

        return leadMapper.toDto(updated);
    }

    /**
     * Soft delete a lead.
     */
    public void delete(Long id) {
        log.debug("Deleting lead ID: {}", id);

        Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lead", id));

        if (lead.isDeleted()) {
            throw new ResourceNotFoundException("Lead", id);
        }

        lead.softDelete();
        leadRepository.save(lead);

        log.info("Deleted lead ID: {}", id);
    }

    /**
     * List leads with optional filtering.
     */
    @Transactional(readOnly = true)
    public Page<LeadDto> list(Pageable pageable, LeadStatus status, LeadPriority priority) {
        log.debug("Listing leads with status: {}, priority: {}", status, priority);

        Page<Lead> leads;

        if (status != null) {
            leads = leadRepository.findByStatusAndDeletedFalse(status, pageable);
        } else if (priority != null) {
            leads = leadRepository.findByPriorityAndDeletedFalse(priority, pageable);
        } else {
            leads = leadRepository.findAllByDeletedFalse(pageable);
        }

        return leads.map(leadMapper::toDto);
    }

    /**
     * Search leads by title.
     */
    @Transactional(readOnly = true)
    public Page<LeadDto> search(String query, Pageable pageable) {
        log.debug("Searching leads with query: {}", query);

        Page<Lead> leads = leadRepository.searchByTitle(query, pageable);
        return leads.map(leadMapper::toDto);
    }

    /**
     * Mark lead as contacted.
     */
    public LeadDto markContacted(Long id) {
        log.debug("Marking lead ID {} as contacted", id);

        Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lead", id));

        if (lead.isDeleted()) {
            throw new ResourceNotFoundException("Lead", id);
        }

        // Validate state transition
        if (lead.getStatus() == LeadStatus.CONVERTED) {
            throw new BusinessException("Cannot contact a lead that is already converted", "INVALID_STATE_TRANSITION");
        }

        if (lead.getStatus() == LeadStatus.LOST) {
            throw new BusinessException("Cannot contact a lead that is lost", "INVALID_STATE_TRANSITION");
        }

        lead.markContacted();
        Lead updated = leadRepository.save(lead);

        log.info("Marked lead ID {} as contacted", id);
        return leadMapper.toDto(updated);
    }

    /**
     * Qualify a lead.
     */
    public LeadDto qualify(Long id) {
        log.debug("Qualifying lead ID: {}", id);

        Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lead", id));

        if (lead.isDeleted()) {
            throw new ResourceNotFoundException("Lead", id);
        }

        // Validate state transition - must be contacted first
        if (lead.getStatus() == LeadStatus.NEW) {
            throw new BusinessException("Cannot qualify a lead that has not been contacted", "INVALID_STATE_TRANSITION");
        }

        if (lead.getStatus() == LeadStatus.CONVERTED) {
            throw new BusinessException("Cannot qualify a lead that is already converted", "INVALID_STATE_TRANSITION");
        }

        if (lead.getStatus() == LeadStatus.LOST) {
            throw new BusinessException("Cannot qualify a lead that is lost", "INVALID_STATE_TRANSITION");
        }

        lead.qualify();
        Lead updated = leadRepository.save(lead);

        log.info("Qualified lead ID: {}", id);
        return leadMapper.toDto(updated);
    }

    /**
     * Convert a lead to a deal.
     */
    public LeadDto convert(Long id) {
        log.debug("Converting lead ID: {}", id);

        Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lead", id));

        if (lead.isDeleted()) {
            throw new ResourceNotFoundException("Lead", id);
        }

        // Validate state transition - must be qualified
        if (lead.getStatus() != LeadStatus.QUALIFIED) {
            throw new BusinessException("Cannot convert a lead that is not qualified", "INVALID_STATE_TRANSITION");
        }

        // Create deal from lead
        Deal deal = Deal.builder()
                .name(lead.getTitle())
                .organization(lead.getOrganization())
                .primaryContact(lead.getContact())
                .amount(lead.getEstimatedValue())
                .currency(lead.getCurrency())
                .stage(DealStage.PROSPECTING)
                .type(DealType.NEW_BUSINESS)
                .ownerId(lead.getOwnerId())
                .sourceLeadId(lead.getId())
                .description(lead.getDescription())
                .probability(10)
                .build();

        Deal savedDeal = dealRepository.save(deal);
        log.info("Created deal ID {} from lead ID {}", savedDeal.getId(), id);

        // Mark lead as converted
        lead.convert(savedDeal.getId());
        Lead updated = leadRepository.save(lead);

        log.info("Converted lead ID {} to deal ID {}", id, savedDeal.getId());
        return leadMapper.toDto(updated);
    }

    /**
     * Mark lead as lost.
     */
    public LeadDto lose(Long id, LeadLoseRequest request) {
        log.debug("Marking lead ID {} as lost with reason: {}", id, request.getReason());

        Lead lead = leadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lead", id));

        if (lead.isDeleted()) {
            throw new ResourceNotFoundException("Lead", id);
        }

        // Validate state transition - cannot lose if already converted
        if (lead.getStatus() == LeadStatus.CONVERTED) {
            throw new BusinessException("Cannot lose a lead that is already converted", "INVALID_STATE_TRANSITION");
        }

        if (lead.getStatus() == LeadStatus.LOST) {
            throw new BusinessException("Lead is already marked as lost", "INVALID_STATE_TRANSITION");
        }

        lead.markLost(request.getReason());
        Lead updated = leadRepository.save(lead);

        log.info("Marked lead ID {} as lost", id);
        return leadMapper.toDto(updated);
    }

    /**
     * Get status counts for dashboard.
     */
    @Transactional(readOnly = true)
    public Map<LeadStatus, Long> getStatusCounts() {
        log.debug("Fetching lead status counts");

        Map<LeadStatus, Long> counts = new EnumMap<>(LeadStatus.class);
<<<<<<< HEAD
        for (LeadStatus status : LeadStatus.values()) {
            counts.put(status, 0L);
        }

=======
        for (LeadStatus s : LeadStatus.values()) {
            counts.put(s, 0L);
        }
>>>>>>> origin/main
        List<Object[]> results = leadRepository.countGroupByStatus();

        for (Object[] row : results) {
            LeadStatus status = (LeadStatus) row[0];
            Long count = (Long) row[1];
            counts.put(status, count);
        }

        return counts;
    }
}
