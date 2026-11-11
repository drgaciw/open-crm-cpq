package com.opencrm.crm.fact;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.crm.contact.Contact;
import com.opencrm.crm.contact.ContactRepository;
import com.opencrm.crm.organization.Organization;
import com.opencrm.crm.organization.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Applies a fact onto a standard contact or organization column.
 * Parent links are refused: {@code AccountHierarchyService} is the only writer, and it is
 * unavailable outside its test seam.
 * TODO: project accepted custom keys onto {@code field_value} once that write is allowed for agents.
 */
@Component
@RequiredArgsConstructor
public class JpaRecordFieldAccess implements RecordFieldAccess {

    private final ContactRepository contactRepository;
    private final OrganizationRepository organizationRepository;
    private final ObjectProvider<CacheManager> cacheManager;

    @Override
    public void requireSubject(FactSubjectType subjectType, long subjectId) {
        switch (subjectType) {
            case CONTACT -> requireContact(subjectId);
            case ORGANIZATION -> requireOrganization(subjectId);
        }
    }

    @Override
    public Optional<ColumnState> read(FactSubjectType subjectType, long subjectId, String fieldKey) {
        if (!StandardFactFields.columnWritable(subjectType, fieldKey)
                && !StandardFactFields.reparent(subjectType, fieldKey)) {
            return Optional.empty();
        }
        return switch (subjectType) {
            case CONTACT -> Optional.of(contactColumn(requireContact(subjectId), fieldKey));
            case ORGANIZATION -> Optional.of(organizationColumn(requireOrganization(subjectId), fieldKey));
        };
    }

    @Override
    public boolean write(FactSubjectType subjectType, long subjectId, String fieldKey, String value) {
        if (StandardFactFields.reparent(subjectType, fieldKey)) {
            throw new BusinessException(
                    "Re-parenting is not applied from the fact ledger. Account hierarchy owns that column.",
                    HttpStatus.CONFLICT,
                    "FACT_REPARENT_UNAVAILABLE");
        }
        if (!StandardFactFields.columnWritable(subjectType, fieldKey)) {
            return false;
        }
        return switch (subjectType) {
            case CONTACT -> writeContact(subjectId, fieldKey, value);
            case ORGANIZATION -> writeOrganization(subjectId, fieldKey, value);
        };
    }

    private boolean writeContact(long subjectId, String fieldKey, String value) {
        Contact contact = requireContact(subjectId);
        String key = StandardFactFields.canonical(fieldKey);
        switch (key) {
            case "jobtitle" -> contact.setJobTitle(value);
            case "email" -> {
                requireUniqueEmail(contact, value);
                contact.setEmail(value);
            }
            case "phone" -> contact.setPhone(value);
            case "department" -> contact.setDepartment(value);
            case "linkedinurl" -> contact.setLinkedinUrl(value);
            case "organizationid" -> contact.setOrganization(requireOrganization(parseLong(value, "organizationId")));
            default -> {
                return false;
            }
        }
        contactRepository.save(contact);
        evict("contacts", contact.getId());
        return true;
    }

    private boolean writeOrganization(long subjectId, String fieldKey, String value) {
        Organization organization = requireOrganization(subjectId);
        String key = StandardFactFields.canonical(fieldKey);
        switch (key) {
            case "industry" -> organization.setIndustry(value);
            case "website" -> organization.setWebsite(value);
            case "phone" -> organization.setPhone(value);
            case "linkedinurl" -> organization.setLinkedinUrl(value);
            case "employeecount" -> organization.setEmployeeCount(parseInteger(value));
            default -> {
                return false;
            }
        }
        organizationRepository.save(organization);
        evict("organizations", organization.getUuid());
        return true;
    }

    private ColumnState contactColumn(Contact contact, String fieldKey) {
        String writer = writerOf(contact.getUpdatedBy(), contact.getCreatedBy());
        String key = StandardFactFields.canonical(fieldKey);
        String value = switch (key) {
            case "jobtitle" -> contact.getJobTitle();
            case "email" -> contact.getEmail();
            case "phone" -> contact.getPhone();
            case "department" -> contact.getDepartment();
            case "linkedinurl" -> contact.getLinkedinUrl();
            case "organizationid" -> contact.getOrganization() == null ? null : String.valueOf(contact.getOrganization().getId());
            default -> null;
        };
        return new ColumnState(value, writer);
    }

    private ColumnState organizationColumn(Organization organization, String fieldKey) {
        String writer = writerOf(organization.getUpdatedBy(), organization.getCreatedBy());
        String key = StandardFactFields.canonical(fieldKey);
        String value = switch (key) {
            case "industry" -> organization.getIndustry();
            case "website" -> organization.getWebsite();
            case "phone" -> organization.getPhone();
            case "linkedinurl" -> organization.getLinkedinUrl();
            case "employeecount" -> organization.getEmployeeCount() == null ? null : String.valueOf(organization.getEmployeeCount());
            case "parentorganizationid" -> organization.getParentOrganizationId() == null
                    ? null
                    : String.valueOf(organization.getParentOrganizationId());
            default -> null;
        };
        return new ColumnState(value, writer);
    }

    private void requireUniqueEmail(Contact contact, String email) {
        contactRepository.findByEmailIgnoreCase(email)
                .filter(other -> !other.isDeleted() && !other.getId().equals(contact.getId()))
                .ifPresent(other -> {
                    throw new BusinessException(
                            "Contact with email '" + email + "' already exists",
                            HttpStatus.CONFLICT,
                            "CONTACT_EMAIL_EXISTS");
                });
    }

    private Contact requireContact(long subjectId) {
        Contact contact = contactRepository.findById(subjectId)
                .orElseThrow(() -> new ResourceNotFoundException("Contact", subjectId));
        if (contact.isDeleted()) {
            throw new ResourceNotFoundException("Contact", subjectId);
        }
        return contact;
    }

    private Organization requireOrganization(long subjectId) {
        Organization organization = organizationRepository.findById(subjectId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", subjectId));
        if (organization.isDeleted()) {
            throw new ResourceNotFoundException("Organization", subjectId);
        }
        return organization;
    }

    private static long parseLong(String value, String fieldKey) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException(
                    fieldKey + " must be a numeric id",
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "FACT_VALUE_TYPE");
        }
    }

    private static int parseInteger(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException(
                    "employeeCount must be a whole number",
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "FACT_VALUE_TYPE");
        }
    }

    private static String writerOf(String updatedBy, String createdBy) {
        if (updatedBy != null && !updatedBy.isBlank()) {
            return updatedBy;
        }
        return createdBy;
    }

    private void evict(String cacheName, Object key) {
        CacheManager manager = cacheManager.getIfAvailable();
        if (manager == null || key == null) {
            return;
        }
        Cache cache = manager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
        }
    }
}
