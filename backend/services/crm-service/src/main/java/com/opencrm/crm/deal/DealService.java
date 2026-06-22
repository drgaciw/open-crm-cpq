package com.opencrm.crm.deal;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.crm.contact.Contact;
import com.opencrm.crm.contact.ContactRepository;
import com.opencrm.crm.organization.Organization;
import com.opencrm.crm.organization.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for managing deals.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class DealService {

    private final DealRepository dealRepository;
    private final ContactRepository contactRepository;
    private final OrganizationRepository organizationRepository;
    private final DealMapper dealMapper;

    /**
     * Create a new deal.
     */
    public DealDto create(DealCreateRequest request) {
        log.debug("Creating deal with name: {}", request.getName());

        Deal deal = dealMapper.toEntity(request);

        // Set primary contact if provided
        if (request.getPrimaryContactId() != null) {
            Contact contact = contactRepository.findById(request.getPrimaryContactId())
                    .orElseThrow(() -> new ResourceNotFoundException("Contact", request.getPrimaryContactId()));
            deal.setPrimaryContact(contact);

            // If contact has an organization and no organizationId is provided, use contact's organization
            if (request.getOrganizationId() == null && contact.getOrganization() != null) {
                deal.setOrganization(contact.getOrganization());
            }
        }

        // Set organization if provided
        if (request.getOrganizationId() != null) {
            Organization org = organizationRepository.findById(request.getOrganizationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Organization", request.getOrganizationId()));
            deal.setOrganization(org);
        }

        Deal saved = dealRepository.save(deal);
        log.info("Created deal with ID: {}", saved.getId());

        return dealMapper.toDto(saved);
    }

    /**
     * Get deal by ID.
     */
    @Transactional(readOnly = true)
    public DealDto getById(Long id) {
        log.debug("Fetching deal by ID: {}", id);

        Deal deal = dealRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Deal", id));

        if (deal.isDeleted()) {
            throw new ResourceNotFoundException("Deal", id);
        }

        return dealMapper.toDto(deal);
    }

    /**
     * Get deal by UUID.
     */
    @Transactional(readOnly = true)
    public DealDto getByUuid(String uuid) {
        log.debug("Fetching deal by UUID: {}", uuid);

        Deal deal = dealRepository.findByUuid(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("Deal", uuid));

        if (deal.isDeleted()) {
            throw new ResourceNotFoundException("Deal", uuid);
        }

        return dealMapper.toDto(deal);
    }

    /**
     * Update a deal.
     */
    public DealDto update(Long id, DealUpdateRequest request) {
        log.debug("Updating deal ID: {}", id);

        Deal deal = dealRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Deal", id));

        if (deal.isDeleted()) {
            throw new ResourceNotFoundException("Deal", id);
        }

        // Validate stage update if provided
        if (request.getStage() != null) {
            if (deal.getStage() == DealStage.WON || deal.getStage() == DealStage.LOST) {
                throw new BusinessException("Cannot update a deal that is already WON or LOST", "INVALID_STATE_TRANSITION");
            }
        }

        dealMapper.updateEntity(request, deal);

        // Update primary contact if provided
        if (request.getPrimaryContactId() != null) {
            Contact contact = contactRepository.findById(request.getPrimaryContactId())
                    .orElseThrow(() -> new ResourceNotFoundException("Contact", request.getPrimaryContactId()));
            deal.setPrimaryContact(contact);
        }

        // Update organization if provided
        if (request.getOrganizationId() != null) {
            Organization org = organizationRepository.findById(request.getOrganizationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Organization", request.getOrganizationId()));
            deal.setOrganization(org);
        }

        Deal updated = dealRepository.save(deal);
        log.info("Updated deal ID: {}", updated.getId());

        return dealMapper.toDto(updated);
    }

    /**
     * Soft delete a deal.
     */
    public void delete(Long id) {
        log.debug("Deleting deal ID: {}", id);

        Deal deal = dealRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Deal", id));

        if (deal.isDeleted()) {
            throw new ResourceNotFoundException("Deal", id);
        }

        deal.softDelete();
        dealRepository.save(deal);

        log.info("Deleted deal ID: {}", id);
    }

    /**
     * List deals with optional filtering.
     */
    @Transactional(readOnly = true)
    public Page<DealDto> list(Pageable pageable, DealStage stage) {
        log.debug("Listing deals with stage: {}", stage);

        Page<Deal> deals;

        if (stage != null) {
            deals = dealRepository.findByStageAndDeletedFalse(stage, pageable);
        } else {
            deals = dealRepository.findAllByDeletedFalse(pageable);
        }

        return deals.map(dealMapper::toDto);
    }

    /**
     * Search deals by name.
     */
    @Transactional(readOnly = true)
    public Page<DealDto> search(String query, Pageable pageable) {
        log.debug("Searching deals with query: {}", query);

        Page<Deal> deals = dealRepository.searchByName(query, pageable);
        return deals.map(dealMapper::toDto);
    }

    /**
     * Advance deal to next stage.
     */
    public DealDto advanceStage(Long id) {
        log.debug("Advancing stage for deal ID: {}", id);

        Deal deal = dealRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Deal", id));

        if (deal.isDeleted()) {
            throw new ResourceNotFoundException("Deal", id);
        }

        // Validate state transition
        if (deal.getStage() == DealStage.WON) {
            throw new BusinessException("Cannot advance a deal that is already WON", "INVALID_STATE_TRANSITION");
        }

        if (deal.getStage() == DealStage.LOST) {
            throw new BusinessException("Cannot advance a deal that is already LOST", "INVALID_STATE_TRANSITION");
        }

        if (deal.getStage() == DealStage.NEGOTIATION) {
            throw new BusinessException("Deal is at final stage. Use win or lose endpoint to close the deal", "INVALID_STATE_TRANSITION");
        }

        deal.advanceStage();
        Deal updated = dealRepository.save(deal);

        log.info("Advanced deal ID {} to stage {}", id, updated.getStage());
        return dealMapper.toDto(updated);
    }

    /**
     * Mark deal as won.
     */
    public DealDto win(Long id) {
        log.debug("Marking deal ID {} as won", id);

        Deal deal = dealRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Deal", id));

        if (deal.isDeleted()) {
            throw new ResourceNotFoundException("Deal", id);
        }

        // Validate state transition
        if (deal.getStage() == DealStage.WON) {
            throw new BusinessException("Deal is already marked as WON", "INVALID_STATE_TRANSITION");
        }

        if (deal.getStage() == DealStage.LOST) {
            throw new BusinessException("Cannot mark a lost deal as won", "INVALID_STATE_TRANSITION");
        }

        deal.markWon();
        Deal updated = dealRepository.save(deal);

        log.info("Marked deal ID {} as won", id);
        return dealMapper.toDto(updated);
    }

    /**
     * Mark deal as lost.
     */
    public DealDto lose(Long id, DealLoseRequest request) {
        log.debug("Marking deal ID {} as lost with reason: {}", id, request.getReason());

        Deal deal = dealRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Deal", id));

        if (deal.isDeleted()) {
            throw new ResourceNotFoundException("Deal", id);
        }

        // Validate state transition
        if (deal.getStage() == DealStage.WON) {
            throw new BusinessException("Cannot mark a won deal as lost", "INVALID_STATE_TRANSITION");
        }

        if (deal.getStage() == DealStage.LOST) {
            throw new BusinessException("Deal is already marked as LOST", "INVALID_STATE_TRANSITION");
        }

        deal.markLost(request.getReason());
        Deal updated = dealRepository.save(deal);

        log.info("Marked deal ID {} as lost", id);
        return dealMapper.toDto(updated);
    }

    /**
     * Get pipeline summary statistics.
     */
    @Transactional(readOnly = true)
    public List<PipelineSummaryDto> getPipelineSummary() {
        log.debug("Fetching pipeline summary");

        List<PipelineSummaryProjection> results = dealRepository.findPipelineSummary();
        List<PipelineSummaryDto> summary = new ArrayList<>();

        for (PipelineSummaryProjection projection : results) {
            PipelineSummaryDto dto = PipelineSummaryDto.builder()
                    .stage(projection.getStage())
                    .count(projection.getCount())
                    .totalAmount(projection.getTotalAmount())
                    .weightedAmount(projection.getWeightedAmount())
                    .build();
            summary.add(dto);
        }

        return summary;
    }
}
