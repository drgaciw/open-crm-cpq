package com.opencrm.crm.lead;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.crm.contact.Contact;
import com.opencrm.crm.contact.ContactRepository;
import com.opencrm.crm.deal.Deal;
import com.opencrm.crm.deal.DealRepository;
import com.opencrm.crm.deal.DealStage;
import com.opencrm.crm.organization.Organization;
import com.opencrm.crm.organization.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeadServiceTest {

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private ContactRepository contactRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private DealRepository dealRepository;

    @Mock
    private LeadMapper leadMapper;

    @InjectMocks
    private LeadService leadService;

    private Lead lead;
    private LeadDto leadDto;
    private LeadCreateRequest createRequest;
    private LeadUpdateRequest updateRequest;
    private Contact contact;
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

        lead = Lead.builder()
                .id(1L)
                .uuid("lead-uuid")
                .title("New Lead")
                .status(LeadStatus.NEW)
                .priority(LeadPriority.MEDIUM)
                .estimatedValue(BigDecimal.valueOf(10000))
                .currency("USD")
                .deleted(false)
                .build();

        leadDto = LeadDto.builder()
                .id(1L)
                .uuid("lead-uuid")
                .title("New Lead")
                .status(LeadStatus.NEW)
                .priority(LeadPriority.MEDIUM)
                .build();

        createRequest = LeadCreateRequest.builder()
                .title("New Lead")
                .priority(LeadPriority.MEDIUM)
                .build();

        updateRequest = LeadUpdateRequest.builder()
                .title("Updated Lead")
                .build();
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("should create lead successfully")
        void shouldCreateLead() {
            when(leadMapper.toEntity(createRequest)).thenReturn(lead);
            when(leadRepository.save(lead)).thenReturn(lead);
            when(leadMapper.toDto(lead)).thenReturn(leadDto);

            LeadDto result = leadService.create(createRequest);

            assertThat(result).isNotNull();
            assertThat(result.getTitle()).isEqualTo("New Lead");
            verify(leadRepository).save(lead);
        }

        @Test
        @DisplayName("should create lead with contact")
        void shouldCreateLeadWithContact() {
            createRequest.setContactId(1L);
            when(leadMapper.toEntity(createRequest)).thenReturn(lead);
            when(contactRepository.findById(1L)).thenReturn(Optional.of(contact));
            when(leadRepository.save(lead)).thenReturn(lead);
            when(leadMapper.toDto(lead)).thenReturn(leadDto);

            LeadDto result = leadService.create(createRequest);

            assertThat(result).isNotNull();
            verify(contactRepository).findById(1L);
        }

        @Test
        @DisplayName("should create lead with organization")
        void shouldCreateLeadWithOrganization() {
            createRequest.setOrganizationId(1L);
            when(leadMapper.toEntity(createRequest)).thenReturn(lead);
            when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
            when(leadRepository.save(lead)).thenReturn(lead);
            when(leadMapper.toDto(lead)).thenReturn(leadDto);

            LeadDto result = leadService.create(createRequest);

            assertThat(result).isNotNull();
            verify(organizationRepository).findById(1L);
        }

        @Test
        @DisplayName("should inherit organization from contact if not provided")
        void shouldInheritOrganizationFromContact() {
            createRequest.setContactId(1L);
            contact.setOrganization(organization);
            when(leadMapper.toEntity(createRequest)).thenReturn(lead);
            when(contactRepository.findById(1L)).thenReturn(Optional.of(contact));
            when(leadRepository.save(lead)).thenReturn(lead);
            when(leadMapper.toDto(lead)).thenReturn(leadDto);

            leadService.create(createRequest);

            assertThat(lead.getOrganization()).isEqualTo(organization);
        }

        @Test
        @DisplayName("should throw when contact not found")
        void shouldThrowWhenContactNotFound() {
            createRequest.setContactId(999L);
            when(leadMapper.toEntity(createRequest)).thenReturn(lead);
            when(contactRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> leadService.create(createRequest))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw when organization not found")
        void shouldThrowWhenOrganizationNotFound() {
            createRequest.setOrganizationId(999L);
            when(leadMapper.toEntity(createRequest)).thenReturn(lead);
            when(organizationRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> leadService.create(createRequest))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        @DisplayName("should return lead when found")
        void shouldReturnLead() {
            when(leadRepository.findById(1L)).thenReturn(Optional.of(lead));
            when(leadMapper.toDto(lead)).thenReturn(leadDto);

            LeadDto result = leadService.getById(1L);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should throw when lead not found")
        void shouldThrowWhenNotFound() {
            when(leadRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> leadService.getById(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw when lead is deleted")
        void shouldThrowWhenDeleted() {
            lead.setDeleted(true);
            when(leadRepository.findById(1L)).thenReturn(Optional.of(lead));

            assertThatThrownBy(() -> leadService.getById(1L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getByUuid")
    class GetByUuid {

        @Test
        @DisplayName("should return lead when found by UUID")
        void shouldReturnLeadByUuid() {
            when(leadRepository.findByUuid("lead-uuid")).thenReturn(Optional.of(lead));
            when(leadMapper.toDto(lead)).thenReturn(leadDto);

            LeadDto result = leadService.getByUuid("lead-uuid");

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should throw when UUID not found")
        void shouldThrowWhenUuidNotFound() {
            when(leadRepository.findByUuid("bad-uuid")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> leadService.getByUuid("bad-uuid"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("should update lead successfully")
        void shouldUpdateLead() {
            when(leadRepository.findById(1L)).thenReturn(Optional.of(lead));
            when(leadRepository.save(lead)).thenReturn(lead);
            when(leadMapper.toDto(lead)).thenReturn(leadDto);

            LeadDto result = leadService.update(1L, updateRequest);

            assertThat(result).isNotNull();
            verify(leadMapper).updateEntity(updateRequest, lead);
        }

        @Test
        @DisplayName("should throw when lead not found on update")
        void shouldThrowWhenNotFoundOnUpdate() {
            when(leadRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> leadService.update(999L, updateRequest))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw when updating deleted lead")
        void shouldThrowWhenUpdatingDeleted() {
            lead.setDeleted(true);
            when(leadRepository.findById(1L)).thenReturn(Optional.of(lead));

            assertThatThrownBy(() -> leadService.update(1L, updateRequest))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should update lead contact when provided")
        void shouldUpdateLeadContact() {
            updateRequest.setContactId(1L);
            when(leadRepository.findById(1L)).thenReturn(Optional.of(lead));
            when(contactRepository.findById(1L)).thenReturn(Optional.of(contact));
            when(leadRepository.save(lead)).thenReturn(lead);
            when(leadMapper.toDto(lead)).thenReturn(leadDto);

            leadService.update(1L, updateRequest);

            assertThat(lead.getContact()).isEqualTo(contact);
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("should soft delete lead")
        void shouldSoftDeleteLead() {
            when(leadRepository.findById(1L)).thenReturn(Optional.of(lead));

            leadService.delete(1L);

            assertThat(lead.isDeleted()).isTrue();
            verify(leadRepository).save(lead);
        }

        @Test
        @DisplayName("should throw when deleting non-existent lead")
        void shouldThrowWhenDeletingNonExistent() {
            when(leadRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> leadService.delete(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("list")
    class ListLeads {

        private final Pageable pageable = PageRequest.of(0, 20);

        @Test
        @DisplayName("should list all leads without filter")
        void shouldListAllLeads() {
            Page<Lead> page = new PageImpl<>(List.of(lead));
            when(leadRepository.findAllByDeletedFalse(pageable)).thenReturn(page);
            when(leadMapper.toDto(lead)).thenReturn(leadDto);

            Page<LeadDto> result = leadService.list(pageable, null, null);

            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("should list leads with status filter")
        void shouldListLeadsWithStatusFilter() {
            Page<Lead> page = new PageImpl<>(List.of(lead));
            when(leadRepository.findByStatusAndDeletedFalse(LeadStatus.NEW, pageable)).thenReturn(page);
            when(leadMapper.toDto(lead)).thenReturn(leadDto);

            Page<LeadDto> result = leadService.list(pageable, LeadStatus.NEW, null);

            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("should list leads with priority filter")
        void shouldListLeadsWithPriorityFilter() {
            Page<Lead> page = new PageImpl<>(List.of(lead));
            when(leadRepository.findByPriorityAndDeletedFalse(LeadPriority.HIGH, pageable)).thenReturn(page);
            when(leadMapper.toDto(lead)).thenReturn(leadDto);

            Page<LeadDto> result = leadService.list(pageable, null, LeadPriority.HIGH);

            assertThat(result.getContent()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("search")
    class Search {

        @Test
        @DisplayName("should search leads by title")
        void shouldSearchLeads() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<Lead> page = new PageImpl<>(List.of(lead));
            when(leadRepository.searchByTitle("New", pageable)).thenReturn(page);
            when(leadMapper.toDto(lead)).thenReturn(leadDto);

            Page<LeadDto> result = leadService.search("New", pageable);

            assertThat(result.getContent()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("markContacted")
    class MarkContacted {

        @Test
        @DisplayName("should mark NEW lead as contacted")
        void shouldMarkNewLeadAsContacted() {
            when(leadRepository.findById(1L)).thenReturn(Optional.of(lead));
            when(leadRepository.save(lead)).thenReturn(lead);
            when(leadMapper.toDto(lead)).thenReturn(leadDto);

            leadService.markContacted(1L);

            assertThat(lead.getStatus()).isEqualTo(LeadStatus.CONTACTED);
            assertThat(lead.getFirstContactedAt()).isNotNull();
        }

        @Test
        @DisplayName("should throw when contacting CONVERTED lead")
        void shouldThrowWhenContactingConverted() {
            lead.setStatus(LeadStatus.CONVERTED);
            when(leadRepository.findById(1L)).thenReturn(Optional.of(lead));

            assertThatThrownBy(() -> leadService.markContacted(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("converted");
        }

        @Test
        @DisplayName("should throw when contacting LOST lead")
        void shouldThrowWhenContactingLost() {
            lead.setStatus(LeadStatus.LOST);
            when(leadRepository.findById(1L)).thenReturn(Optional.of(lead));

            assertThatThrownBy(() -> leadService.markContacted(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("lost");
        }

        @Test
        @DisplayName("should throw when lead not found")
        void shouldThrowWhenNotFound() {
            when(leadRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> leadService.markContacted(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw when lead is deleted")
        void shouldThrowWhenDeleted() {
            lead.setDeleted(true);
            when(leadRepository.findById(1L)).thenReturn(Optional.of(lead));

            assertThatThrownBy(() -> leadService.markContacted(1L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("qualify")
    class Qualify {

        @Test
        @DisplayName("should qualify CONTACTED lead")
        void shouldQualifyContactedLead() {
            lead.setStatus(LeadStatus.CONTACTED);
            when(leadRepository.findById(1L)).thenReturn(Optional.of(lead));
            when(leadRepository.save(lead)).thenReturn(lead);
            when(leadMapper.toDto(lead)).thenReturn(leadDto);

            leadService.qualify(1L);

            assertThat(lead.getStatus()).isEqualTo(LeadStatus.QUALIFIED);
        }

        @Test
        @DisplayName("should throw when qualifying NEW lead")
        void shouldThrowWhenQualifyingNew() {
            lead.setStatus(LeadStatus.NEW);
            when(leadRepository.findById(1L)).thenReturn(Optional.of(lead));

            assertThatThrownBy(() -> leadService.qualify(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("not been contacted");
        }

        @Test
        @DisplayName("should throw when qualifying CONVERTED lead")
        void shouldThrowWhenQualifyingConverted() {
            lead.setStatus(LeadStatus.CONVERTED);
            when(leadRepository.findById(1L)).thenReturn(Optional.of(lead));

            assertThatThrownBy(() -> leadService.qualify(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("converted");
        }

        @Test
        @DisplayName("should throw when qualifying LOST lead")
        void shouldThrowWhenQualifyingLost() {
            lead.setStatus(LeadStatus.LOST);
            when(leadRepository.findById(1L)).thenReturn(Optional.of(lead));

            assertThatThrownBy(() -> leadService.qualify(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("lost");
        }
    }

    @Nested
    @DisplayName("convert")
    class Convert {

        @Test
        @DisplayName("should convert QUALIFIED lead to deal")
        void shouldConvertQualifiedLead() {
            lead.setStatus(LeadStatus.QUALIFIED);
            lead.setContact(contact);
            lead.setOrganization(organization);

            Deal savedDeal = Deal.builder().id(10L).name("New Lead").stage(DealStage.PROSPECTING).build();

            when(leadRepository.findById(1L)).thenReturn(Optional.of(lead));
            when(dealRepository.save(any(Deal.class))).thenReturn(savedDeal);
            when(leadRepository.save(lead)).thenReturn(lead);
            when(leadMapper.toDto(lead)).thenReturn(leadDto);

            leadService.convert(1L);

            assertThat(lead.getStatus()).isEqualTo(LeadStatus.CONVERTED);
            assertThat(lead.getConvertedDealId()).isEqualTo(10L);
            verify(dealRepository).save(any(Deal.class));
        }

        @Test
        @DisplayName("should create deal with correct data from lead")
        void shouldCreateDealWithCorrectData() {
            lead.setStatus(LeadStatus.QUALIFIED);
            lead.setContact(contact);
            lead.setOrganization(organization);
            lead.setEstimatedValue(BigDecimal.valueOf(50000));
            lead.setOwnerId("owner-1");

            Deal savedDeal = Deal.builder().id(10L).name("New Lead").stage(DealStage.PROSPECTING).build();
            when(leadRepository.findById(1L)).thenReturn(Optional.of(lead));
            when(dealRepository.save(any(Deal.class))).thenReturn(savedDeal);
            when(leadRepository.save(lead)).thenReturn(lead);
            when(leadMapper.toDto(lead)).thenReturn(leadDto);

            leadService.convert(1L);

            ArgumentCaptor<Deal> dealCaptor = ArgumentCaptor.forClass(Deal.class);
            verify(dealRepository).save(dealCaptor.capture());
            Deal createdDeal = dealCaptor.getValue();

            assertThat(createdDeal.getName()).isEqualTo("New Lead");
            assertThat(createdDeal.getAmount()).isEqualTo(BigDecimal.valueOf(50000));
            assertThat(createdDeal.getOrganization()).isEqualTo(organization);
            assertThat(createdDeal.getPrimaryContact()).isEqualTo(contact);
            assertThat(createdDeal.getOwnerId()).isEqualTo("owner-1");
            assertThat(createdDeal.getSourceLeadId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should throw when converting non-QUALIFIED lead")
        void shouldThrowWhenConvertingNonQualified() {
            lead.setStatus(LeadStatus.CONTACTED);
            when(leadRepository.findById(1L)).thenReturn(Optional.of(lead));

            assertThatThrownBy(() -> leadService.convert(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("not qualified");
        }

        @Test
        @DisplayName("should throw when converting NEW lead")
        void shouldThrowWhenConvertingNew() {
            lead.setStatus(LeadStatus.NEW);
            when(leadRepository.findById(1L)).thenReturn(Optional.of(lead));

            assertThatThrownBy(() -> leadService.convert(1L))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("lose")
    class Lose {

        private final LeadLoseRequest loseRequest = LeadLoseRequest.builder().reason("No budget").build();

        @Test
        @DisplayName("should mark lead as lost")
        void shouldMarkLeadAsLost() {
            when(leadRepository.findById(1L)).thenReturn(Optional.of(lead));
            when(leadRepository.save(lead)).thenReturn(lead);
            when(leadMapper.toDto(lead)).thenReturn(leadDto);

            leadService.lose(1L, loseRequest);

            assertThat(lead.getStatus()).isEqualTo(LeadStatus.LOST);
            assertThat(lead.getLostReason()).isEqualTo("No budget");
        }

        @Test
        @DisplayName("should throw when losing CONVERTED lead")
        void shouldThrowWhenLosingConverted() {
            lead.setStatus(LeadStatus.CONVERTED);
            when(leadRepository.findById(1L)).thenReturn(Optional.of(lead));

            assertThatThrownBy(() -> leadService.lose(1L, loseRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("converted");
        }

        @Test
        @DisplayName("should throw when losing already LOST lead")
        void shouldThrowWhenAlreadyLost() {
            lead.setStatus(LeadStatus.LOST);
            when(leadRepository.findById(1L)).thenReturn(Optional.of(lead));

            assertThatThrownBy(() -> leadService.lose(1L, loseRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("already marked as lost");
        }
    }

    @Nested
    @DisplayName("getStatusCounts")
    class GetStatusCounts {

        @Test
        @DisplayName("should return counts for all statuses")
        void shouldReturnStatusCounts() {
            when(leadRepository.countGroupByStatus()).thenReturn(List.<Object[]>of(
                    new Object[] {LeadStatus.NEW, 5L},
                    new Object[] {LeadStatus.CONTACTED, 3L},
                    new Object[] {LeadStatus.QUALIFIED, 2L},
                    new Object[] {LeadStatus.UNQUALIFIED, 1L},
                    new Object[] {LeadStatus.CONVERTED, 4L},
                    new Object[] {LeadStatus.LOST, 0L}
            ));

            Map<LeadStatus, Long> counts = leadService.getStatusCounts();

            assertThat(counts).hasSize(LeadStatus.values().length);
            assertThat(counts.get(LeadStatus.NEW)).isEqualTo(5L);
            assertThat(counts.get(LeadStatus.CONTACTED)).isEqualTo(3L);
            assertThat(counts.get(LeadStatus.QUALIFIED)).isEqualTo(2L);
            assertThat(counts.get(LeadStatus.CONVERTED)).isEqualTo(4L);
            assertThat(counts.get(LeadStatus.LOST)).isEqualTo(0L);
        }
    }
}
