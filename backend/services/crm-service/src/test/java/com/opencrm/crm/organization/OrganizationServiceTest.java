package com.opencrm.crm.organization;

import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.common.valueobject.AccountId;
import com.opencrm.crm.contact.ContactDto;
import com.opencrm.crm.contact.ContactService;
import com.opencrm.crm.outbox.CrmDomainEvents;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OrganizationMapper organizationMapper;

    @Mock
    private ContactService contactService;

    @Mock
    private CrmDomainEvents domainEvents;

    @Mock
    private AccountHierarchyService accountHierarchyService;

    @InjectMocks
    private OrganizationService organizationService;

    private Organization organization;
    private OrganizationDto organizationDto;
    private OrganizationCreateRequest createRequest;
    private OrganizationUpdateRequest updateRequest;

    @BeforeEach
    void setUp() {
        organization = Organization.builder()
                .id(1L)
                .uuid("org-uuid")
                .name("Acme Corp")
                .type(OrganizationType.PROSPECT)
                .deleted(false)
                .build();

        organizationDto = OrganizationDto.builder()
                .id(1L)
                .uuid("org-uuid")
                .name("Acme Corp")
                .type(OrganizationType.PROSPECT)
                .contactCount(0)
                .build();

        createRequest = OrganizationCreateRequest.builder()
                .name("Acme Corp")
                .type(OrganizationType.PROSPECT)
                .build();

        updateRequest = OrganizationUpdateRequest.builder()
                .name("Acme Corp Updated")
                .build();
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("should create organization successfully")
        void shouldCreateOrganization() {
            when(organizationMapper.toEntity(createRequest)).thenReturn(organization);
            when(organizationRepository.save(organization)).thenReturn(organization);
            when(organizationMapper.toDto(organization)).thenReturn(organizationDto);

            OrganizationDto result = organizationService.create(createRequest);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("Acme Corp");
            verify(organizationRepository).save(organization);
            verify(accountHierarchyService, never()).linkToParent(any(), any());
        }

        @Test
        @DisplayName("should link a parent through the hierarchy service")
        void shouldLinkParentOnCreate() {
            String uuid = "550e8400-e29b-41d4-a716-446655440000";
            organization.setUuid(uuid);
            createRequest.setParentOrganizationId(9L);
            when(organizationMapper.toEntity(createRequest)).thenReturn(organization);
            when(organizationRepository.save(organization)).thenReturn(organization);
            when(organizationMapper.toDto(organization)).thenReturn(organizationDto);

            organizationService.create(createRequest);

            verify(domainEvents).organizationCreated(organization);
            verify(accountHierarchyService).linkToParent(AccountId.of(uuid), 9L);
        }
    }

    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        void resolvesLegacyNumericIdToPublicUuid() {
            String uuid = "550e8400-e29b-41d4-a716-446655440000";
            organization.setUuid(uuid);
            when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
            assertThat(organizationService.resolveAccountId(1L)).isEqualTo(AccountId.of(uuid));
        }

        @Test
        void legacyLookupReturnsNotFoundForDeletedOrganization() {
            organization.setDeleted(true);
            when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
            assertThatThrownBy(() -> organizationService.resolveAccountId(1L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("1");
        }

        @Test
        @DisplayName("should return organization when found")
        void shouldReturnOrganization() {
            when(organizationRepository.findByUuidIgnoreCase("550e8400-e29b-41d4-a716-446655440000")).thenReturn(Optional.of(organization));
            when(organizationMapper.toDto(organization)).thenReturn(organizationDto);

            OrganizationDto result = organizationService.getById(AccountId.of("550e8400-e29b-41d4-a716-446655440000"));

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should throw when organization not found")
        void shouldThrowWhenNotFound() {
            when(organizationRepository.findByUuidIgnoreCase("550e8400-e29b-41d4-a716-446655440999")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> organizationService.getById(AccountId.of("550e8400-e29b-41d4-a716-446655440999")))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw when organization is deleted")
        void shouldThrowWhenDeleted() {
            organization.setDeleted(true);
            when(organizationRepository.findByUuidIgnoreCase("550e8400-e29b-41d4-a716-446655440000")).thenReturn(Optional.of(organization));

            assertThatThrownBy(() -> organizationService.getById(AccountId.of("550e8400-e29b-41d4-a716-446655440000")))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getByUuid")
    class GetByUuid {

        @Test
        @DisplayName("should return organization when found by UUID")
        void shouldReturnOrganizationByUuid() {
            when(organizationRepository.findByUuid("org-uuid")).thenReturn(Optional.of(organization));
            when(organizationMapper.toDto(organization)).thenReturn(organizationDto);

            OrganizationDto result = organizationService.getByUuid("org-uuid");

            assertThat(result).isNotNull();
            assertThat(result.getUuid()).isEqualTo("org-uuid");
        }

        @Test
        @DisplayName("should throw when UUID not found")
        void shouldThrowWhenUuidNotFound() {
            when(organizationRepository.findByUuid("bad-uuid")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> organizationService.getByUuid("bad-uuid"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw when organization found by UUID is deleted")
        void shouldThrowWhenDeletedByUuid() {
            organization.setDeleted(true);
            when(organizationRepository.findByUuid("org-uuid")).thenReturn(Optional.of(organization));

            assertThatThrownBy(() -> organizationService.getByUuid("org-uuid"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("should update organization successfully")
        void shouldUpdateOrganization() {
            when(organizationRepository.findByUuidIgnoreCase("550e8400-e29b-41d4-a716-446655440000")).thenReturn(Optional.of(organization));
            when(organizationRepository.save(organization)).thenReturn(organization);
            when(organizationMapper.toDto(organization)).thenReturn(organizationDto);

            OrganizationDto result = organizationService.update(AccountId.of("550e8400-e29b-41d4-a716-446655440000"), updateRequest);

            assertThat(result).isNotNull();
            verify(organizationMapper).updateEntity(updateRequest, organization);
            verify(organizationRepository).save(organization);
            verify(accountHierarchyService, never()).linkToParent(any(), any());
        }

        @Test
        @DisplayName("should link a parent through the hierarchy service")
        void shouldLinkParentOnUpdate() {
            String uuid = "550e8400-e29b-41d4-a716-446655440000";
            organization.setUuid(uuid);
            updateRequest.setParentOrganizationId(9L);
            when(organizationRepository.findByUuidIgnoreCase(uuid)).thenReturn(Optional.of(organization));
            when(organizationRepository.save(organization)).thenReturn(organization);
            when(organizationMapper.toDto(organization)).thenReturn(organizationDto);

            organizationService.update(AccountId.of(uuid), updateRequest);

            verify(accountHierarchyService).linkToParent(AccountId.of(uuid), 9L);
        }

        @Test
        @DisplayName("should throw when organization not found on update")
        void shouldThrowWhenNotFoundOnUpdate() {
            when(organizationRepository.findByUuidIgnoreCase("550e8400-e29b-41d4-a716-446655440999")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> organizationService.update(AccountId.of("550e8400-e29b-41d4-a716-446655440999"), updateRequest))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw when updating deleted organization")
        void shouldThrowWhenUpdatingDeleted() {
            organization.setDeleted(true);
            when(organizationRepository.findByUuidIgnoreCase("550e8400-e29b-41d4-a716-446655440000")).thenReturn(Optional.of(organization));

            assertThatThrownBy(() -> organizationService.update(AccountId.of("550e8400-e29b-41d4-a716-446655440000"), updateRequest))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("should soft delete organization")
        void shouldSoftDeleteOrganization() {
            when(organizationRepository.findByUuidIgnoreCase("550e8400-e29b-41d4-a716-446655440000")).thenReturn(Optional.of(organization));

            organizationService.delete(AccountId.of("550e8400-e29b-41d4-a716-446655440000"));

            assertThat(organization.isDeleted()).isTrue();
            verify(organizationRepository).save(organization);
        }

        @Test
        @DisplayName("should throw when deleting non-existent organization")
        void shouldThrowWhenDeletingNonExistent() {
            when(organizationRepository.findByUuidIgnoreCase("550e8400-e29b-41d4-a716-446655440999")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> organizationService.delete(AccountId.of("550e8400-e29b-41d4-a716-446655440999")))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw when deleting already deleted organization")
        void shouldThrowWhenAlreadyDeleted() {
            organization.setDeleted(true);
            when(organizationRepository.findByUuidIgnoreCase("550e8400-e29b-41d4-a716-446655440000")).thenReturn(Optional.of(organization));

            assertThatThrownBy(() -> organizationService.delete(AccountId.of("550e8400-e29b-41d4-a716-446655440000")))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("list")
    class ListOrganizations {

        private final Pageable pageable = PageRequest.of(0, 20);

        @Test
        @DisplayName("should list all organizations without filter")
        void shouldListAllOrganizations() {
            Page<Organization> page = new PageImpl<>(List.of(organization));
            when(organizationRepository.findAllByDeletedFalse(pageable)).thenReturn(page);
            when(organizationMapper.toDto(organization)).thenReturn(organizationDto);

            Page<OrganizationDto> result = organizationService.list(pageable, null);

            assertThat(result.getContent()).hasSize(1);
            verify(organizationRepository).findAllByDeletedFalse(pageable);
        }

        @Test
        @DisplayName("should list organizations with type filter")
        void shouldListWithTypeFilter() {
            Page<Organization> page = new PageImpl<>(List.of(organization));
            when(organizationRepository.findByTypeAndDeletedFalse(OrganizationType.PROSPECT, pageable)).thenReturn(page);
            when(organizationMapper.toDto(organization)).thenReturn(organizationDto);

            Page<OrganizationDto> result = organizationService.list(pageable, OrganizationType.PROSPECT);

            assertThat(result.getContent()).hasSize(1);
            verify(organizationRepository).findByTypeAndDeletedFalse(OrganizationType.PROSPECT, pageable);
        }
    }

    @Nested
    @DisplayName("search")
    class Search {

        private final Pageable pageable = PageRequest.of(0, 20);

        @Test
        @DisplayName("should search organizations by name")
        void shouldSearchOrganizations() {
            Page<Organization> page = new PageImpl<>(List.of(organization));
            when(organizationRepository.searchByName("Acme", pageable)).thenReturn(page);
            when(organizationMapper.toDto(organization)).thenReturn(organizationDto);

            Page<OrganizationDto> result = organizationService.search("Acme", pageable);

            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("should return empty search results")
        void shouldReturnEmptySearchResults() {
            Page<Organization> page = new PageImpl<>(List.of());
            when(organizationRepository.searchByName("NonExistent", pageable)).thenReturn(page);

            Page<OrganizationDto> result = organizationService.search("NonExistent", pageable);

            assertThat(result.getContent()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getContacts")
    class GetContacts {

        private final Pageable pageable = PageRequest.of(0, 20);

        @Test
        @DisplayName("should get contacts for organization")
        void shouldGetContacts() {
            when(organizationRepository.findByUuidIgnoreCase("550e8400-e29b-41d4-a716-446655440000")).thenReturn(Optional.of(organization));
            Page<ContactDto> contactPage = new PageImpl<>(List.of(
                    ContactDto.builder().id(1L).firstName("John").lastName("Doe").build()
            ));
            when(contactService.getByOrganization(AccountId.of("550e8400-e29b-41d4-a716-446655440000"), pageable)).thenReturn(contactPage);

            Page<ContactDto> result = organizationService.getContacts(AccountId.of("550e8400-e29b-41d4-a716-446655440000"), pageable);

            assertThat(result.getContent()).hasSize(1);
            verify(contactService).getByOrganization(AccountId.of("550e8400-e29b-41d4-a716-446655440000"), pageable);
        }

        @Test
        @DisplayName("should throw when organization not found for getContacts")
        void shouldThrowWhenOrgNotFound() {
            when(organizationRepository.findByUuidIgnoreCase("550e8400-e29b-41d4-a716-446655440999")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> organizationService.getContacts(AccountId.of("550e8400-e29b-41d4-a716-446655440999"), pageable))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw when organization is deleted for getContacts")
        void shouldThrowWhenOrgDeletedForGetContacts() {
            organization.setDeleted(true);
            when(organizationRepository.findByUuidIgnoreCase("550e8400-e29b-41d4-a716-446655440000")).thenReturn(Optional.of(organization));

            assertThatThrownBy(() -> organizationService.getContacts(AccountId.of("550e8400-e29b-41d4-a716-446655440000"), pageable))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
