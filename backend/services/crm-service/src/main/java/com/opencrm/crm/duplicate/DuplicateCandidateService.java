package com.opencrm.crm.duplicate;

import com.opencrm.crm.contact.ContactRepository;
import com.opencrm.crm.organization.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Read-only duplicate suggestions. RED stub returns no candidates and does not apply the match rules.
 */
@Service
@RequiredArgsConstructor
public class DuplicateCandidateService {

    public static final int MAX_CANDIDATES = 50;

    private final OrganizationRepository organizationRepository;
    private final ContactRepository contactRepository;

    public List<DuplicateCandidateDto> organizationCandidates(Long id) {
        return List.of();
    }

    public List<DuplicateCandidateDto> contactCandidates(Long id) {
        return List.of();
    }

    public static String likePattern(String normalizedDomain) {
        return normalizedDomain;
    }
}
