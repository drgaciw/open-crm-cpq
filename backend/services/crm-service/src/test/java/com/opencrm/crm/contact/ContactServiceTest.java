package com.opencrm.crm.contact;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.crm.organization.Organization;
import com.opencrm.crm.organization.OrganizationRepository;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContactServiceTest {

    @Mock
    private ContactRepository contactRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private ContactMapper contactMapper;

    @Mock
    private CrmDomainEvents domainEvents;

    @InjectMocks
    private ContactService contactService;

    private Contact contact;
    private ContactDto contactDto;
    private ContactCreateRequest createRequest;
    private ContactUpdateRequest updateRequest;
    private Organization organization;

    @BeforeEach
    void setUp() {
        organization = Organization.builder()
                .id(1L)
                .uuid("org-uuid")
                .name("Acme Corp")
                .deleted(false)
                .build();

        contact = Contact.builder()
                .id(1L)
                .uuid("contact-uuid")
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .deleted(false)
                .build();

        contactDto = ContactDto.builder()
                .id(1L)
                .uuid("contact-uuid")
                .firstName("John")
                .lastName("Doe")
                .fullName("John Doe")
                .email("john@example.com")
                .build();

        createRequest = ContactCreateRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john@example.com")
                .build();

        updateRequest = ContactUpdateRequest.builder()
                .firstName("Jane")
                .build();
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("should create contact successfully")
        void shouldCreateContact() {
            when(contactRepository.existsByEmailIgnoreCaseAndDeletedFalse("john@example.com")).thenReturn(false);
            when(contactMapper.toEntity(createRequest)).thenReturn(contact);
            when(contactRepository.save(contact)).thenReturn(contact);
            when(contactMapper.toDto(contact)).thenReturn(contactDto);

            ContactDto result = contactService.create(createRequest);

            assertThat(result).isNotNull();
            assertThat(result.getFirstName()).isEqualTo("John");
            verify(contactRepository).save(contact);
        }

        @Test
        @DisplayName("should create contact with organization")
        void shouldCreateContactWithOrganization() {
            createRequest.setOrganizationId(1L);
            when(contactRepository.existsByEmailIgnoreCaseAndDeletedFalse("john@example.com")).thenReturn(false);
            when(contactMapper.toEntity(createRequest)).thenReturn(contact);
            when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
            when(contactRepository.save(contact)).thenReturn(contact);
            when(contactMapper.toDto(contact)).thenReturn(contactDto);

            ContactDto result = contactService.create(createRequest);

            assertThat(result).isNotNull();
            verify(organizationRepository).findById(1L);
        }

        @Test
        @DisplayName("should throw when email already exists")
        void shouldThrowWhenEmailExists() {
            when(contactRepository.existsByEmailIgnoreCaseAndDeletedFalse("john@example.com")).thenReturn(true);

            assertThatThrownBy(() -> contactService.create(createRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("already exists");
        }

        @Test
        @DisplayName("should create contact without email")
        void shouldCreateContactWithoutEmail() {
            createRequest.setEmail(null);
            when(contactMapper.toEntity(createRequest)).thenReturn(contact);
            when(contactRepository.save(contact)).thenReturn(contact);
            when(contactMapper.toDto(contact)).thenReturn(contactDto);

            ContactDto result = contactService.create(createRequest);

            assertThat(result).isNotNull();
            verify(contactRepository, never()).existsByEmailIgnoreCaseAndDeletedFalse(anyString());
        }

        @Test
        @DisplayName("should throw when organization not found")
        void shouldThrowWhenOrganizationNotFound() {
            createRequest.setOrganizationId(999L);
            when(contactRepository.existsByEmailIgnoreCaseAndDeletedFalse("john@example.com")).thenReturn(false);
            when(contactMapper.toEntity(createRequest)).thenReturn(contact);
            when(organizationRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> contactService.create(createRequest))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw when organization is deleted")
        void shouldThrowWhenOrganizationDeleted() {
            createRequest.setOrganizationId(1L);
            organization.setDeleted(true);
            when(contactRepository.existsByEmailIgnoreCaseAndDeletedFalse("john@example.com")).thenReturn(false);
            when(contactMapper.toEntity(createRequest)).thenReturn(contact);
            when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));

            assertThatThrownBy(() -> contactService.create(createRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("deleted organization");
        }
    }

    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        @DisplayName("should return contact when found")
        void shouldReturnContact() {
            when(contactRepository.findById(1L)).thenReturn(Optional.of(contact));
            when(contactMapper.toDto(contact)).thenReturn(contactDto);

            ContactDto result = contactService.getById(1L);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should throw when contact not found")
        void shouldThrowWhenNotFound() {
            when(contactRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> contactService.getById(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw when contact is deleted")
        void shouldThrowWhenDeleted() {
            contact.setDeleted(true);
            when(contactRepository.findById(1L)).thenReturn(Optional.of(contact));

            assertThatThrownBy(() -> contactService.getById(1L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getByUuid")
    class GetByUuid {

        @Test
        @DisplayName("should return contact when found by UUID")
        void shouldReturnContactByUuid() {
            when(contactRepository.findByUuid("contact-uuid")).thenReturn(Optional.of(contact));
            when(contactMapper.toDto(contact)).thenReturn(contactDto);

            ContactDto result = contactService.getByUuid("contact-uuid");

            assertThat(result).isNotNull();
            assertThat(result.getUuid()).isEqualTo("contact-uuid");
        }

        @Test
        @DisplayName("should throw when UUID not found")
        void shouldThrowWhenUuidNotFound() {
            when(contactRepository.findByUuid("bad-uuid")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> contactService.getByUuid("bad-uuid"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw when contact found by UUID is deleted")
        void shouldThrowWhenDeletedByUuid() {
            contact.setDeleted(true);
            when(contactRepository.findByUuid("contact-uuid")).thenReturn(Optional.of(contact));

            assertThatThrownBy(() -> contactService.getByUuid("contact-uuid"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("should update contact successfully")
        void shouldUpdateContact() {
            when(contactRepository.findById(1L)).thenReturn(Optional.of(contact));
            when(contactRepository.save(contact)).thenReturn(contact);
            when(contactMapper.toDto(contact)).thenReturn(contactDto);

            ContactDto result = contactService.update(1L, updateRequest);

            assertThat(result).isNotNull();
            verify(contactMapper).updateEntity(updateRequest, contact);
            verify(contactRepository).save(contact);
        }

        @Test
        @DisplayName("should throw when contact not found on update")
        void shouldThrowWhenNotFoundOnUpdate() {
            when(contactRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> contactService.update(999L, updateRequest))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw when updating deleted contact")
        void shouldThrowWhenUpdatingDeleted() {
            contact.setDeleted(true);
            when(contactRepository.findById(1L)).thenReturn(Optional.of(contact));

            assertThatThrownBy(() -> contactService.update(1L, updateRequest))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw on email conflict during update")
        void shouldThrowOnEmailConflict() {
            updateRequest.setEmail("taken@example.com");
            when(contactRepository.findById(1L)).thenReturn(Optional.of(contact));
            when(contactRepository.existsByEmailIgnoreCaseAndDeletedFalse("taken@example.com")).thenReturn(true);

            assertThatThrownBy(() -> contactService.update(1L, updateRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("already exists");
        }

        @Test
        @DisplayName("should allow updating email to same value")
        void shouldAllowSameEmail() {
            updateRequest.setEmail("john@example.com");
            when(contactRepository.findById(1L)).thenReturn(Optional.of(contact));
            when(contactRepository.save(contact)).thenReturn(contact);
            when(contactMapper.toDto(contact)).thenReturn(contactDto);

            ContactDto result = contactService.update(1L, updateRequest);

            assertThat(result).isNotNull();
            verify(contactRepository, never()).existsByEmailIgnoreCaseAndDeletedFalse(anyString());
        }

        @Test
        @DisplayName("should update organization on contact")
        void shouldUpdateOrganization() {
            updateRequest.setOrganizationId(1L);
            when(contactRepository.findById(1L)).thenReturn(Optional.of(contact));
            when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
            when(contactRepository.save(contact)).thenReturn(contact);
            when(contactMapper.toDto(contact)).thenReturn(contactDto);

            ContactDto result = contactService.update(1L, updateRequest);

            assertThat(result).isNotNull();
            verify(organizationRepository).findById(1L);
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("should soft delete contact")
        void shouldSoftDeleteContact() {
            when(contactRepository.findById(1L)).thenReturn(Optional.of(contact));

            contactService.delete(1L);

            assertThat(contact.isDeleted()).isTrue();
            verify(contactRepository).save(contact);
        }

        @Test
        @DisplayName("should throw when deleting non-existent contact")
        void shouldThrowWhenDeletingNonExistent() {
            when(contactRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> contactService.delete(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw when deleting already deleted contact")
        void shouldThrowWhenAlreadyDeleted() {
            contact.setDeleted(true);
            when(contactRepository.findById(1L)).thenReturn(Optional.of(contact));

            assertThatThrownBy(() -> contactService.delete(1L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("list")
    class ListContacts {

        private final Pageable pageable = PageRequest.of(0, 20);

        @Test
        @DisplayName("should list all contacts without filter")
        void shouldListAllContacts() {
            Page<Contact> page = new PageImpl<>(List.of(contact));
            when(contactRepository.findAllByDeletedFalse(pageable)).thenReturn(page);
            when(contactMapper.toDto(contact)).thenReturn(contactDto);

            Page<ContactDto> result = contactService.list(pageable, null);

            assertThat(result.getContent()).hasSize(1);
            verify(contactRepository).findAllByDeletedFalse(pageable);
        }

        @Test
        @DisplayName("should list contacts with status filter")
        void shouldListContactsWithStatusFilter() {
            Page<Contact> page = new PageImpl<>(List.of(contact));
            when(contactRepository.findByStatusAndDeletedFalse(ContactStatus.ACTIVE, pageable)).thenReturn(page);
            when(contactMapper.toDto(contact)).thenReturn(contactDto);

            Page<ContactDto> result = contactService.list(pageable, ContactStatus.ACTIVE);

            assertThat(result.getContent()).hasSize(1);
            verify(contactRepository).findByStatusAndDeletedFalse(ContactStatus.ACTIVE, pageable);
        }
    }

    @Nested
    @DisplayName("search")
    class Search {

        private final Pageable pageable = PageRequest.of(0, 20);

        @Test
        @DisplayName("should search contacts by name")
        void shouldSearchContacts() {
            Page<Contact> page = new PageImpl<>(List.of(contact));
            when(contactRepository.searchByName("John", pageable)).thenReturn(page);
            when(contactMapper.toDto(contact)).thenReturn(contactDto);

            Page<ContactDto> result = contactService.search("John", pageable);

            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("should return empty when no search results")
        void shouldReturnEmptySearchResults() {
            Page<Contact> page = new PageImpl<>(List.of());
            when(contactRepository.searchByName("NonExistent", pageable)).thenReturn(page);

            Page<ContactDto> result = contactService.search("NonExistent", pageable);

            assertThat(result.getContent()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getByOrganization")
    class GetByOrganization {

        private final Pageable pageable = PageRequest.of(0, 20);

        @Test
        @DisplayName("should get contacts by organization")
        void shouldGetContactsByOrganization() {
            when(organizationRepository.existsById(1L)).thenReturn(true);
            Page<Contact> page = new PageImpl<>(List.of(contact));
            when(contactRepository.findByOrganizationIdAndDeletedFalse(1L, pageable)).thenReturn(page);
            when(contactMapper.toDto(contact)).thenReturn(contactDto);

            Page<ContactDto> result = contactService.getByOrganization(1L, pageable);

            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("should throw when organization not found")
        void shouldThrowWhenOrgNotFound() {
            when(organizationRepository.existsById(999L)).thenReturn(false);

            assertThatThrownBy(() -> contactService.getByOrganization(999L, pageable))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
