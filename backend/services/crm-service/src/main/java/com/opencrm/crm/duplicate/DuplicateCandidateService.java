package com.opencrm.crm.duplicate;

import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.crm.contact.Contact;
import com.opencrm.crm.contact.ContactRepository;
import com.opencrm.crm.contact.ContactStatus;
import com.opencrm.crm.organization.Organization;
import com.opencrm.crm.organization.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only duplicate suggestions. This service does not save, delete, merge, or publish events.
 * Every candidate query is capped at {@link #MAX_CANDIDATES}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DuplicateCandidateService {

    public static final int MAX_CANDIDATES = 50;

    private static final Pageable CANDIDATE_PAGE = PageRequest.of(
            0, MAX_CANDIDATES, Sort.by(Sort.Direction.ASC, "id"));

    /** Name query already orders by id. The page size is the cap after the name predicate. */
    private static final Pageable NAME_CANDIDATE_PAGE = PageRequest.of(0, MAX_CANDIDATES);

    private final OrganizationRepository organizationRepository;
    private final ContactRepository contactRepository;

    public List<DuplicateCandidateDto> organizationCandidates(Long id) {
        Organization source = organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organization", id));
        if (source.isDeleted()) {
            throw new ResourceNotFoundException("Organization", id);
        }
        String domain = effectiveDomain(source);
        if (domain == null || FreeMailDomains.isFreeMail(domain)) {
            return List.of();
        }
        List<Organization> rows = organizationRepository.findActiveDomainCandidates(
                id, likePattern(domain), CANDIDATE_PAGE);
        List<DuplicateCandidateDto> matches = new ArrayList<>();
        for (Organization row : rows) {
            if (matches.size() == MAX_CANDIDATES) {
                break;
            }
            if (row.getId() == null || row.getId().equals(id) || row.isDeleted()) {
                continue;
            }
            String candidateDomain = effectiveDomain(row);
            if (domain.equals(candidateDomain) && !FreeMailDomains.isFreeMail(candidateDomain)) {
                matches.add(DuplicateCandidateDto.builder()
                        .candidateId(row.getId())
                        .candidateType(DuplicateCandidateType.ORGANIZATION)
                        .reason(DuplicateMatchReason.DOMAIN)
                        .matchedValue(domain)
                        .build());
            }
        }
        return List.copyOf(matches);
    }

    public List<DuplicateCandidateDto> contactCandidates(Long id) {
        Contact source = contactRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contact", id));
        if (source.isDeleted()) {
            throw new ResourceNotFoundException("Contact", id);
        }
        if (source.getStatus() != ContactStatus.ACTIVE) {
            return List.of();
        }
        String email = DomainNormalizer.normalizeEmail(source.getEmail());
        if (email != null) {
            List<DuplicateCandidateDto> emailMatches = emailMatches(id, email);
            if (!emailMatches.isEmpty()) {
                return emailMatches;
            }
        }
        Long organizationId = organizationId(source);
        String name = DomainNormalizer.normalizeName(source.getFirstName(), source.getLastName());
        if (organizationId == null || name == null) {
            return List.of();
        }
        return nameMatches(id, organizationId, name);
    }

    /**
     * Bound a LIKE contains pattern to the full normalized domain. {@code %} and {@code _} are escaped
     * so they stay literal. The repository still applies a 50-row page.
     */
    public static String likePattern(String normalizedDomain) {
        String escaped = normalizedDomain
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private List<DuplicateCandidateDto> emailMatches(Long sourceId, String email) {
        List<Contact> rows = contactRepository.findActiveByNormalizedEmail(
                sourceId, email, ContactStatus.ACTIVE, CANDIDATE_PAGE);
        List<DuplicateCandidateDto> matches = new ArrayList<>();
        for (Contact row : rows) {
            if (matches.size() == MAX_CANDIDATES) {
                break;
            }
            if (!isActiveContact(row, sourceId)) {
                continue;
            }
            if (!email.equals(DomainNormalizer.normalizeEmail(row.getEmail()))) {
                continue;
            }
            matches.add(contactCandidate(row, DuplicateMatchReason.EMAIL, email));
        }
        return List.copyOf(matches);
    }

    private List<DuplicateCandidateDto> nameMatches(Long sourceId, Long organizationId, String name) {
        List<Contact> rows = contactRepository.findActiveInOrganization(
                sourceId, organizationId, ContactStatus.ACTIVE, name, NAME_CANDIDATE_PAGE);
        List<DuplicateCandidateDto> matches = new ArrayList<>();
        for (Contact row : rows) {
            if (matches.size() == MAX_CANDIDATES) {
                break;
            }
            if (!isActiveContact(row, sourceId) || !organizationId.equals(organizationId(row))) {
                continue;
            }
            if (!name.equals(DomainNormalizer.normalizeName(row.getFirstName(), row.getLastName()))) {
                continue;
            }
            matches.add(contactCandidate(row, DuplicateMatchReason.NAME_SAME_ORG, name));
        }
        return List.copyOf(matches);
    }

    private static boolean isActiveContact(Contact contact, Long sourceId) {
        return contact.getId() != null
                && !contact.getId().equals(sourceId)
                && !contact.isDeleted()
                && contact.getStatus() == ContactStatus.ACTIVE;
    }

    private static Long organizationId(Contact contact) {
        if (contact.getOrganization() == null) {
            return null;
        }
        return contact.getOrganization().getId();
    }

    private static DuplicateCandidateDto contactCandidate(Contact contact, DuplicateMatchReason reason, String matchedValue) {
        return DuplicateCandidateDto.builder()
                .candidateId(contact.getId())
                .candidateType(DuplicateCandidateType.CONTACT)
                .reason(reason)
                .matchedValue(matchedValue)
                .build();
    }

    /**
     * Normalized {@code websiteDomain}, or {@code website} when the domain is null or blank.
     */
    static String effectiveDomain(Organization organization) {
        String raw = organization.getWebsiteDomain();
        if (raw == null || raw.isBlank()) {
            raw = organization.getWebsite();
        }
        return DomainNormalizer.normalizeDomain(raw);
    }
}
