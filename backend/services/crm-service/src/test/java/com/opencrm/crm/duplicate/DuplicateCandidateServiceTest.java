package com.opencrm.crm.duplicate;

import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.crm.contact.Contact;
import com.opencrm.crm.contact.ContactRepository;
import com.opencrm.crm.contact.ContactStatus;
import com.opencrm.crm.organization.Organization;
import com.opencrm.crm.organization.OrganizationRepository;
import com.opencrm.crm.organization.OrganizationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DRG-517 duplicate candidate service")
class DuplicateCandidateServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private ContactRepository contactRepository;

    private DuplicateCandidateService service;

    @BeforeEach
    void setUp() {
        service = new DuplicateCandidateService(organizationRepository, contactRepository);
    }

    @Test
    @DisplayName("AC1: normalizedWebsiteDomainsMatch")
    void normalizedWebsiteDomainsMatch() {
        Organization source = organization(1L, "https://www.Acme.com/about", null);
        Organization other = organization(2L, "acme.com", null);
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(source));
        when(organizationRepository.findActiveDomainCandidates(eq(1L), eq("%acme.com%"), any()))
                .thenReturn(List.of(other, source));

        List<DuplicateCandidateDto> result = service.organizationCandidates(1L);

        assertThat(result).singleElement().satisfies(candidate -> {
            assertThat(candidate.getCandidateId()).isEqualTo(2L);
            assertThat(candidate.getCandidateType()).isEqualTo(DuplicateCandidateType.ORGANIZATION);
            assertThat(candidate.getReason()).isEqualTo(DuplicateMatchReason.DOMAIN);
            assertThat(candidate.getMatchedValue()).isEqualTo("acme.com");
        });
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(organizationRepository).findActiveDomainCandidates(eq(1L), eq("%acme.com%"), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(DuplicateCandidateService.MAX_CANDIDATES);
        assertThat(pageable.getValue().getSort().getOrderFor("id").isAscending()).isTrue();
    }

    @Test
    @DisplayName("AC1: websiteIsUsedOnlyWhenDomainIsBlank")
    void websiteIsUsedOnlyWhenDomainIsBlank() {
        Organization source = organization(1L, "  ", "https://www.Acme.com/about");
        Organization match = organization(2L, "acme.com", null);
        Organization otherSite = organization(3L, "other.com", "https://www.acme.com");
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(source));
        when(organizationRepository.findActiveDomainCandidates(eq(1L), eq("%acme.com%"), any()))
                .thenReturn(List.of(match, otherSite));

        assertThat(service.organizationCandidates(1L))
                .extracting(DuplicateCandidateDto::getCandidateId)
                .containsExactly(2L);

        when(organizationRepository.findById(3L)).thenReturn(Optional.of(otherSite));
        when(organizationRepository.findActiveDomainCandidates(eq(3L), eq("%other.com%"), any()))
                .thenReturn(List.of(source, match));
        assertThat(service.organizationCandidates(3L)).isEmpty();
    }

    @Test
    @DisplayName("AC2: freeMailOrganizationYieldsNoCandidates")
    void freeMailOrganizationYieldsNoCandidates() {
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization(1L, "gmail.com", null)));
        when(organizationRepository.findById(2L)).thenReturn(Optional.of(organization(2L, "https://mail.yahoo.com/inbox", null)));

        assertThat(FreeMailDomains.isFreeMail("gmail.com")).isTrue();
        assertThat(FreeMailDomains.isFreeMail("mail.yahoo.com")).isTrue();
        assertThat(service.organizationCandidates(1L)).isEmpty();
        assertThat(service.organizationCandidates(2L)).isEmpty();
        verify(organizationRepository, never()).findActiveDomainCandidates(any(), any(), any());
    }

    @Test
    @DisplayName("AC2: notgmailAndCompanyGmailStillMatch")
    void notgmailAndCompanyGmailStillMatch() {
        Organization source = organization(1L, "notgmail.com", null);
        Organization other = organization(2L, "notgmail.com", null);
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(source));
        when(organizationRepository.findActiveDomainCandidates(eq(1L), eq("%notgmail.com%"), any()))
                .thenReturn(List.of(other));
        assertThat(service.organizationCandidates(1L)).extracting(DuplicateCandidateDto::getMatchedValue)
                .containsExactly("notgmail.com");

        Organization company = organization(3L, "gmail.company.com", null);
        Organization companyPeer = organization(4L, "https://www.gmail.company.com/about", null);
        when(organizationRepository.findById(3L)).thenReturn(Optional.of(company));
        when(organizationRepository.findActiveDomainCandidates(eq(3L), eq("%gmail.company.com%"), any()))
                .thenReturn(List.of(companyPeer));
        assertThat(service.organizationCandidates(3L)).extracting(DuplicateCandidateDto::getCandidateId)
                .containsExactly(4L);
    }

    @Test
    @DisplayName("AC1: blankDomainYieldsNoCandidates")
    void blankDomainYieldsNoCandidates() {
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization(1L, null, "  ")));
        assertThat(service.organizationCandidates(1L)).isEmpty();
        verify(organizationRepository, never()).findActiveDomainCandidates(any(), any(), any());
    }

    @Test
    @DisplayName("AC5: candidateQueriesAreCappedAtFifty")
    void candidateQueriesAreCappedAtFifty() {
        Organization source = organization(1L, "acme.com", null);
        List<Organization> rows = new ArrayList<>();
        for (long id = 2; id <= 61; id++) {
            rows.add(organization(id, "acme.com", null));
        }
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(source));
        when(organizationRepository.findActiveDomainCandidates(eq(1L), eq("%acme.com%"), any())).thenReturn(rows);

        assertThat(service.organizationCandidates(1L)).hasSize(DuplicateCandidateService.MAX_CANDIDATES);
        assertThat(DuplicateCandidateService.likePattern("a_b%c.com")).isEqualTo("%a\\_b\\%c.com%");
        assertThat(DuplicateCandidateService.likePattern("acme.com")).isEqualTo("%acme.com%");
    }

    @Test
    @DisplayName("AC5: deletedOrganizationIsNotACandidateAndDeletedSourceIs404")
    void deletedOrganizationIsNotACandidateAndDeletedSourceIs404() {
        Organization source = organization(1L, "acme.com", null);
        Organization deleted = organization(2L, "acme.com", null);
        deleted.softDelete();
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(source));
        when(organizationRepository.findActiveDomainCandidates(eq(1L), any(), any())).thenReturn(List.of(deleted));
        assertThat(service.organizationCandidates(1L)).isEmpty();

        source.softDelete();
        assertThatThrownBy(() -> service.organizationCandidates(1L)).isInstanceOf(ResourceNotFoundException.class);
        when(organizationRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.organizationCandidates(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("AC3: normalizedEmailsMatchAcrossOrganizations")
    void normalizedEmailsMatchAcrossOrganizations() {
        Organization acme = organization(10L, "acme.com", null);
        Organization other = organization(11L, "other.com", null);
        Contact source = contact(1L, "Ann", "Smith", "  Ann@Acme.com ", acme, ContactStatus.ACTIVE);
        Contact match = contact(2L, "Bea", "Jones", "ann@acme.com", other, ContactStatus.ACTIVE);
        when(contactRepository.findById(1L)).thenReturn(Optional.of(source));
        when(contactRepository.findActiveByNormalizedEmail(eq(1L), eq("ann@acme.com"), eq(ContactStatus.ACTIVE), any()))
                .thenReturn(List.of(match));

        assertThat(service.contactCandidates(1L)).singleElement().satisfies(candidate -> {
            assertThat(candidate.getCandidateId()).isEqualTo(2L);
            assertThat(candidate.getCandidateType()).isEqualTo(DuplicateCandidateType.CONTACT);
            assertThat(candidate.getReason()).isEqualTo(DuplicateMatchReason.EMAIL);
            assertThat(candidate.getMatchedValue()).isEqualTo("ann@acme.com");
        });
        verify(contactRepository, never()).findActiveInOrganization(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("AC3: plusTagsAreNotStripped")
    void plusTagsAreNotStripped() {
        Organization acme = organization(10L, "acme.com", null);
        Contact source = contact(1L, "Ann", "Smith", "ann+news@acme.com", acme, ContactStatus.ACTIVE);
        Contact other = contact(2L, "Bea", "Jones", "ann@acme.com", acme, ContactStatus.ACTIVE);
        when(contactRepository.findById(1L)).thenReturn(Optional.of(source));
        when(contactRepository.findActiveByNormalizedEmail(eq(1L), eq("ann+news@acme.com"), eq(ContactStatus.ACTIVE), any()))
                .thenReturn(List.of(other));
        when(contactRepository.findActiveInOrganization(eq(1L), eq(10L), eq(ContactStatus.ACTIVE), eq("ann smith"), any()))
                .thenReturn(List.of(other));

        assertThat(service.contactCandidates(1L)).isEmpty();
        verify(contactRepository).findActiveByNormalizedEmail(
                eq(1L), eq("ann+news@acme.com"), eq(ContactStatus.ACTIVE), any());
    }

    @Test
    @DisplayName("AC4: sameNameInTheSameOrganizationMatchesWhenEmailDoesNot")
    void sameNameInTheSameOrganizationMatchesWhenEmailDoesNot() {
        Organization acme = organization(10L, "acme.com", null);
        Contact source = contact(1L, "  Ann   Marie ", "Smith", "a@acme.com", acme, ContactStatus.ACTIVE);
        Contact match = contact(2L, "ann marie", "smith", "b@acme.com", acme, ContactStatus.ACTIVE);
        when(contactRepository.findById(1L)).thenReturn(Optional.of(source));
        when(contactRepository.findActiveByNormalizedEmail(eq(1L), eq("a@acme.com"), eq(ContactStatus.ACTIVE), any()))
                .thenReturn(List.of());
        when(contactRepository.findActiveInOrganization(
                eq(1L), eq(10L), eq(ContactStatus.ACTIVE), eq("ann marie smith"), any()))
                .thenReturn(List.of(match, source));

        assertThat(service.contactCandidates(1L)).singleElement().satisfies(candidate -> {
            assertThat(candidate.getCandidateId()).isEqualTo(2L);
            assertThat(candidate.getReason()).isEqualTo(DuplicateMatchReason.NAME_SAME_ORG);
            assertThat(candidate.getMatchedValue()).isEqualTo("ann marie smith");
            assertThat(candidate.getCandidateType()).isEqualTo(DuplicateCandidateType.CONTACT);
        });
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(contactRepository).findActiveInOrganization(
                eq(1L), eq(10L), eq(ContactStatus.ACTIVE), eq("ann marie smith"), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
    }

    @Test
    @DisplayName("AC4: sameNameInDifferentOrganizationsIsNotACandidate")
    void sameNameInDifferentOrganizationsIsNotACandidate() {
        Organization acme = organization(10L, "acme.com", null);
        Organization other = organization(11L, "other.com", null);
        Contact source = contact(1L, "Ann", "Smith", null, acme, ContactStatus.ACTIVE);
        Contact elsewhere = contact(2L, "Ann", "Smith", null, other, ContactStatus.ACTIVE);
        when(contactRepository.findById(1L)).thenReturn(Optional.of(source));
        when(contactRepository.findActiveInOrganization(eq(1L), eq(10L), eq(ContactStatus.ACTIVE), eq("ann smith"), any()))
                .thenReturn(List.of(elsewhere));

        assertThat(service.contactCandidates(1L)).isEmpty();
        verify(contactRepository).findActiveInOrganization(eq(1L), eq(10L), eq(ContactStatus.ACTIVE), eq("ann smith"), any());
        verify(contactRepository, never()).findActiveByNormalizedEmail(any(), any(), any(), any());
    }

    @Test
    @DisplayName("AC4: freeMailDoesNotUpgradeCrossOrganizationNameMatch")
    void freeMailDoesNotUpgradeCrossOrganizationNameMatch() {
        Organization acme = organization(10L, "acme.com", null);
        Organization other = organization(11L, "other.com", null);
        Contact source = contact(1L, "Jane", "Doe", "jane@gmail.com", acme, ContactStatus.ACTIVE);
        Contact elsewhere = contact(2L, "Jane", "Doe", "jane@yahoo.com", other, ContactStatus.ACTIVE);
        when(contactRepository.findById(1L)).thenReturn(Optional.of(source));
        when(contactRepository.findActiveByNormalizedEmail(eq(1L), eq("jane@gmail.com"), eq(ContactStatus.ACTIVE), any()))
                .thenReturn(List.of());
        when(contactRepository.findActiveInOrganization(eq(1L), eq(10L), eq(ContactStatus.ACTIVE), eq("jane doe"), any()))
                .thenReturn(List.of(elsewhere));

        assertThat(service.contactCandidates(1L)).isEmpty();
        verify(contactRepository).findActiveByNormalizedEmail(
                eq(1L), eq("jane@gmail.com"), eq(ContactStatus.ACTIVE), any());
    }

    @Test
    @DisplayName("AC4: emailMatchSuppressesNameMatch")
    void emailMatchSuppressesNameMatch() {
        Organization acme = organization(10L, "acme.com", null);
        Contact source = contact(1L, "Ann", "Smith", "ann@acme.com", acme, ContactStatus.ACTIVE);
        Contact emailMatch = contact(2L, "Bea", "Jones", "ann@acme.com", organization(11L, "other.com", null), ContactStatus.ACTIVE);
        Contact nameMatch = contact(3L, "Ann", "Smith", "other@acme.com", acme, ContactStatus.ACTIVE);
        when(contactRepository.findById(1L)).thenReturn(Optional.of(source));
        when(contactRepository.findActiveByNormalizedEmail(eq(1L), eq("ann@acme.com"), eq(ContactStatus.ACTIVE), any()))
                .thenReturn(List.of(emailMatch, nameMatch));

        assertThat(service.contactCandidates(1L)).extracting(DuplicateCandidateDto::getCandidateId)
                .containsExactly(2L);
        verify(contactRepository, never()).findActiveInOrganization(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("AC5: archivedInactiveOrDeletedContactIsNotACandidate")
    void archivedInactiveOrDeletedContactIsNotACandidate() {
        Organization acme = organization(10L, "acme.com", null);
        Contact source = contact(1L, "Ann", "Smith", "ann@acme.com", acme, ContactStatus.ACTIVE);
        Contact archived = contact(2L, "Ann", "Smith", "ann@acme.com", acme, ContactStatus.ARCHIVED);
        Contact inactive = contact(3L, "Ann", "Smith", "ann@acme.com", acme, ContactStatus.INACTIVE);
        Contact deleted = contact(4L, "Ann", "Smith", "ann@acme.com", acme, ContactStatus.ACTIVE);
        deleted.softDelete();
        when(contactRepository.findById(1L)).thenReturn(Optional.of(source));
        when(contactRepository.findActiveByNormalizedEmail(eq(1L), eq("ann@acme.com"), eq(ContactStatus.ACTIVE), any()))
                .thenReturn(List.of(archived, inactive, deleted));

        assertThat(service.contactCandidates(1L)).isEmpty();
        verify(contactRepository).findActiveByNormalizedEmail(
                eq(1L), eq("ann@acme.com"), eq(ContactStatus.ACTIVE), any());
    }

    @Test
    @DisplayName("AC5: inactiveSourceReturnsEmptyAndDeletedOrUnknownSourceIs404")
    void inactiveSourceReturnsEmptyAndDeletedOrUnknownSourceIs404() {
        Organization acme = organization(10L, "acme.com", null);
        Contact archived = contact(1L, "Ann", "Smith", "ann@acme.com", acme, ContactStatus.ARCHIVED);
        when(contactRepository.findById(1L)).thenReturn(Optional.of(archived));
        assertThat(service.contactCandidates(1L)).isEmpty();
        verify(contactRepository, never()).findActiveByNormalizedEmail(any(), any(), any(), any());

        Contact deleted = contact(2L, "Ann", "Smith", "ann@acme.com", acme, ContactStatus.ACTIVE);
        deleted.softDelete();
        when(contactRepository.findById(2L)).thenReturn(Optional.of(deleted));
        assertThatThrownBy(() -> service.contactCandidates(2L)).isInstanceOf(ResourceNotFoundException.class);
        when(contactRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.contactCandidates(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    private static Organization organization(Long id, String websiteDomain, String website) {
        return Organization.builder()
                .id(id)
                .uuid("00000000-0000-0000-0000-" + String.format("%012d", id))
                .name("Org " + id)
                .websiteDomain(websiteDomain)
                .website(website)
                .type(OrganizationType.PROSPECT)
                .deleted(false)
                .build();
    }

    private static Contact contact(Long id, String first, String last, String email, Organization organization, ContactStatus status) {
        return Contact.builder()
                .id(id)
                .uuid("10000000-0000-0000-0000-" + String.format("%012d", id))
                .firstName(first)
                .lastName(last)
                .email(email)
                .organization(organization)
                .status(status)
                .deleted(false)
                .build();
    }
}
