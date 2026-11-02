package com.opencrm.crm.buyingrole;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.crm.contact.Contact;
import com.opencrm.crm.contact.ContactRepository;
import com.opencrm.crm.deal.Deal;
import com.opencrm.crm.deal.DealRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Attaches contacts to a deal with one buying role each (DRG-518).
 * Stage-required roles belong to playbooks (DRG-486) and are not enforced here.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DealBuyingRoleService {

    static final String ALREADY_ASSIGNED = "BUYING_ROLE_ALREADY_ASSIGNED";
    static final String INVALID = "BUYING_ROLE_INVALID";

    private final DealBuyingRoleRepository dealBuyingRoleRepository;
    private final DealRepository dealRepository;
    private final ContactRepository contactRepository;

    @Transactional(readOnly = true)
    public List<DealBuyingRoleDto> list(Long dealId) {
        requireActiveDeal(dealId);
        return dealBuyingRoleRepository.findActiveByDealId(dealId).stream()
                .filter(DealBuyingRoleService::visibleContact)
                .map(DealBuyingRoleService::toDto)
                .toList();
    }

    @Transactional
    public DealBuyingRoleDto assign(Long dealId, AssignBuyingRoleRequest request) {
        if (request == null || request.getContactId() == null || request.getRole() == null) {
            throw new BusinessException("Contact and buying role are required", HttpStatus.UNPROCESSABLE_CONTENT, INVALID);
        }
        Deal deal = requireActiveDeal(dealId);
        Contact contact = requireActiveContact(request.getContactId());
        DealBuyingRole existing = dealBuyingRoleRepository
                .findByDealIdAndContactId(deal.getId(), contact.getId())
                .orElse(null);
        if (existing != null && !existing.isDeleted()) {
            throw new BusinessException(
                    "This contact already has a buying role on the deal",
                    HttpStatus.CONFLICT,
                    ALREADY_ASSIGNED);
        }
        DealBuyingRole assignment = existing == null
                ? DealBuyingRole.builder().deal(deal).contact(contact).deleted(false).build()
                : existing;
        assignment.setDeleted(false);
        assignment.setRole(request.getRole());
        DealBuyingRoleDto saved = save(assignment);
        log.info("Assigned buying role {} to contact {} on deal {}", saved.getRole(), saved.getContactId(), saved.getDealId());
        return saved;
    }

    @Transactional
    public void remove(Long dealId, Long contactId) {
        Deal deal = requireActiveDeal(dealId);
        Contact contact = requireActiveContact(contactId);
        DealBuyingRole assignment = dealBuyingRoleRepository
                .findByDealIdAndContactId(deal.getId(), contact.getId())
                .filter(row -> !row.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Buying role", contactId));
        assignment.softDelete();
        dealBuyingRoleRepository.save(assignment);
        log.info("Removed buying role for contact {} on deal {}", contact.getId(), deal.getId());
    }

    private DealBuyingRoleDto save(DealBuyingRole assignment) {
        try {
            return toDto(dealBuyingRoleRepository.saveAndFlush(assignment));
        } catch (DataIntegrityViolationException ex) {
            if (isDealContactConflict(ex)) {
                throw new BusinessException(
                        "This contact already has a buying role on the deal",
                        HttpStatus.CONFLICT,
                        ALREADY_ASSIGNED);
            }
            throw ex;
        }
    }

    private static boolean visibleContact(DealBuyingRole assignment) {
        Contact contact = assignment.getContact();
        return contact != null && !contact.isDeleted();
    }

    private Deal requireActiveDeal(Long dealId) {
        if (dealId == null) {
            throw new ResourceNotFoundException("Deal", "null");
        }
        Deal deal = dealRepository.findById(dealId)
                .orElseThrow(() -> new ResourceNotFoundException("Deal", dealId));
        if (deal.isDeleted()) {
            throw new ResourceNotFoundException("Deal", dealId);
        }
        return deal;
    }

    private Contact requireActiveContact(Long contactId) {
        Contact contact = contactRepository.findById(contactId)
                .orElseThrow(() -> new ResourceNotFoundException("Contact", contactId));
        if (contact.isDeleted()) {
            throw new ResourceNotFoundException("Contact", contactId);
        }
        return contact;
    }

    private static DealBuyingRoleDto toDto(DealBuyingRole assignment) {
        Contact contact = assignment.getContact();
        return DealBuyingRoleDto.builder()
                .id(assignment.getId())
                .uuid(assignment.getUuid())
                .dealId(assignment.getDeal().getId())
                .contactId(contact.getId())
                .contactName(contact.getFullName())
                .role(assignment.getRole())
                .build();
    }

    /**
     * Unique violation on (deal_id, contact_id), including a race past the read check.
     * Other unique failures, such as uuid, are left as integrity errors.
     */
    static boolean isDealContactConflict(Throwable error) {
        for (Throwable cause = error; cause != null && cause.getCause() != cause; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && message.contains("uq_deal_buying_roles_deal_contact")) {
                return true;
            }
        }
        return false;
    }
}
