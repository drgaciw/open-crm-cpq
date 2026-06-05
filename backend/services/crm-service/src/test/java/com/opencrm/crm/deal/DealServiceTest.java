package com.opencrm.crm.deal;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.crm.contact.Contact;
import com.opencrm.crm.contact.ContactRepository;
import com.opencrm.crm.organization.Organization;
import com.opencrm.crm.organization.OrganizationRepository;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DealServiceTest {

    private PipelineSummaryProjection createMockProjection(DealStage stage, Long count, BigDecimal totalAmount, BigDecimal weightedAmount) {
        return new PipelineSummaryProjection() {
            @Override
            public DealStage getStage() { return stage; }
            @Override
            public Long getCount() { return count; }
            @Override
            public BigDecimal getTotalAmount() { return totalAmount; }
            @Override
            public BigDecimal getWeightedAmount() { return weightedAmount; }
        };
    }

    @Mock
    private DealRepository dealRepository;

    @Mock
    private ContactRepository contactRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private DealMapper dealMapper;

    @InjectMocks
    private DealService dealService;

    private Deal deal;
    private DealDto dealDto;
    private DealCreateRequest createRequest;
    private DealUpdateRequest updateRequest;
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
                .deleted(false)
                .build();

        deal = Deal.builder()
                .id(1L)
                .uuid("deal-uuid")
                .name("Big Deal")
                .stage(DealStage.PROSPECTING)
                .amount(BigDecimal.valueOf(50000))
                .currency("USD")
                .probability(10)
                .deleted(false)
                .build();

        dealDto = DealDto.builder()
                .id(1L)
                .uuid("deal-uuid")
                .name("Big Deal")
                .stage(DealStage.PROSPECTING)
                .amount(BigDecimal.valueOf(50000))
                .build();

        createRequest = DealCreateRequest.builder()
                .name("Big Deal")
                .amount(BigDecimal.valueOf(50000))
                .build();

        updateRequest = DealUpdateRequest.builder()
                .name("Updated Deal")
                .build();
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("should create deal successfully")
        void shouldCreateDeal() {
            when(dealMapper.toEntity(createRequest)).thenReturn(deal);
            when(dealRepository.save(deal)).thenReturn(deal);
            when(dealMapper.toDto(deal)).thenReturn(dealDto);

            DealDto result = dealService.create(createRequest);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("Big Deal");
            verify(dealRepository).save(deal);
        }

        @Test
        @DisplayName("should create deal with primary contact")
        void shouldCreateDealWithContact() {
            createRequest.setPrimaryContactId(1L);
            when(dealMapper.toEntity(createRequest)).thenReturn(deal);
            when(contactRepository.findById(1L)).thenReturn(Optional.of(contact));
            when(dealRepository.save(deal)).thenReturn(deal);
            when(dealMapper.toDto(deal)).thenReturn(dealDto);

            DealDto result = dealService.create(createRequest);

            assertThat(result).isNotNull();
            verify(contactRepository).findById(1L);
        }

        @Test
        @DisplayName("should create deal with organization")
        void shouldCreateDealWithOrganization() {
            createRequest.setOrganizationId(1L);
            when(dealMapper.toEntity(createRequest)).thenReturn(deal);
            when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
            when(dealRepository.save(deal)).thenReturn(deal);
            when(dealMapper.toDto(deal)).thenReturn(dealDto);

            DealDto result = dealService.create(createRequest);

            assertThat(result).isNotNull();
            verify(organizationRepository).findById(1L);
        }

        @Test
        @DisplayName("should inherit organization from contact if not provided")
        void shouldInheritOrganizationFromContact() {
            createRequest.setPrimaryContactId(1L);
            contact.setOrganization(organization);
            when(dealMapper.toEntity(createRequest)).thenReturn(deal);
            when(contactRepository.findById(1L)).thenReturn(Optional.of(contact));
            when(dealRepository.save(deal)).thenReturn(deal);
            when(dealMapper.toDto(deal)).thenReturn(dealDto);

            dealService.create(createRequest);

            assertThat(deal.getOrganization()).isEqualTo(organization);
        }

        @Test
        @DisplayName("should throw when contact not found")
        void shouldThrowWhenContactNotFound() {
            createRequest.setPrimaryContactId(999L);
            when(dealMapper.toEntity(createRequest)).thenReturn(deal);
            when(contactRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> dealService.create(createRequest))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw when organization not found")
        void shouldThrowWhenOrganizationNotFound() {
            createRequest.setOrganizationId(999L);
            when(dealMapper.toEntity(createRequest)).thenReturn(deal);
            when(organizationRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> dealService.create(createRequest))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        @DisplayName("should return deal when found")
        void shouldReturnDeal() {
            when(dealRepository.findById(1L)).thenReturn(Optional.of(deal));
            when(dealMapper.toDto(deal)).thenReturn(dealDto);

            DealDto result = dealService.getById(1L);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should throw when deal not found")
        void shouldThrowWhenNotFound() {
            when(dealRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> dealService.getById(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw when deal is deleted")
        void shouldThrowWhenDeleted() {
            deal.setDeleted(true);
            when(dealRepository.findById(1L)).thenReturn(Optional.of(deal));

            assertThatThrownBy(() -> dealService.getById(1L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getByUuid")
    class GetByUuid {

        @Test
        @DisplayName("should return deal when found by UUID")
        void shouldReturnDealByUuid() {
            when(dealRepository.findByUuid("deal-uuid")).thenReturn(Optional.of(deal));
            when(dealMapper.toDto(deal)).thenReturn(dealDto);

            DealDto result = dealService.getByUuid("deal-uuid");

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should throw when UUID not found")
        void shouldThrowWhenUuidNotFound() {
            when(dealRepository.findByUuid("bad-uuid")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> dealService.getByUuid("bad-uuid"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw when deal found by UUID is deleted")
        void shouldThrowWhenDeletedByUuid() {
            deal.setDeleted(true);
            when(dealRepository.findByUuid("deal-uuid")).thenReturn(Optional.of(deal));

            assertThatThrownBy(() -> dealService.getByUuid("deal-uuid"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("should update deal successfully")
        void shouldUpdateDeal() {
            when(dealRepository.findById(1L)).thenReturn(Optional.of(deal));
            when(dealRepository.save(deal)).thenReturn(deal);
            when(dealMapper.toDto(deal)).thenReturn(dealDto);

            DealDto result = dealService.update(1L, updateRequest);

            assertThat(result).isNotNull();
            verify(dealMapper).updateEntity(updateRequest, deal);
        }

        @Test
        @DisplayName("should throw when deal not found on update")
        void shouldThrowWhenNotFoundOnUpdate() {
            when(dealRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> dealService.update(999L, updateRequest))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw when updating deleted deal")
        void shouldThrowWhenUpdatingDeleted() {
            deal.setDeleted(true);
            when(dealRepository.findById(1L)).thenReturn(Optional.of(deal));

            assertThatThrownBy(() -> dealService.update(1L, updateRequest))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw when updating stage on WON deal")
        void shouldThrowWhenUpdatingStageOnWon() {
            deal.setStage(DealStage.WON);
            updateRequest.setStage(DealStage.PROSPECTING);
            when(dealRepository.findById(1L)).thenReturn(Optional.of(deal));

            assertThatThrownBy(() -> dealService.update(1L, updateRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("WON or LOST");
        }

        @Test
        @DisplayName("should throw when updating stage on LOST deal")
        void shouldThrowWhenUpdatingStageOnLost() {
            deal.setStage(DealStage.LOST);
            updateRequest.setStage(DealStage.PROSPECTING);
            when(dealRepository.findById(1L)).thenReturn(Optional.of(deal));

            assertThatThrownBy(() -> dealService.update(1L, updateRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("WON or LOST");
        }

        @Test
        @DisplayName("should update deal primary contact")
        void shouldUpdateDealContact() {
            updateRequest.setPrimaryContactId(1L);
            when(dealRepository.findById(1L)).thenReturn(Optional.of(deal));
            when(contactRepository.findById(1L)).thenReturn(Optional.of(contact));
            when(dealRepository.save(deal)).thenReturn(deal);
            when(dealMapper.toDto(deal)).thenReturn(dealDto);

            dealService.update(1L, updateRequest);

            assertThat(deal.getPrimaryContact()).isEqualTo(contact);
        }

        @Test
        @DisplayName("should update deal organization")
        void shouldUpdateDealOrganization() {
            updateRequest.setOrganizationId(1L);
            when(dealRepository.findById(1L)).thenReturn(Optional.of(deal));
            when(organizationRepository.findById(1L)).thenReturn(Optional.of(organization));
            when(dealRepository.save(deal)).thenReturn(deal);
            when(dealMapper.toDto(deal)).thenReturn(dealDto);

            dealService.update(1L, updateRequest);

            assertThat(deal.getOrganization()).isEqualTo(organization);
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("should soft delete deal")
        void shouldSoftDeleteDeal() {
            when(dealRepository.findById(1L)).thenReturn(Optional.of(deal));

            dealService.delete(1L);

            assertThat(deal.isDeleted()).isTrue();
            verify(dealRepository).save(deal);
        }

        @Test
        @DisplayName("should throw when deleting non-existent deal")
        void shouldThrowWhenDeletingNonExistent() {
            when(dealRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> dealService.delete(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw when deleting already deleted deal")
        void shouldThrowWhenAlreadyDeleted() {
            deal.setDeleted(true);
            when(dealRepository.findById(1L)).thenReturn(Optional.of(deal));

            assertThatThrownBy(() -> dealService.delete(1L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("list")
    class ListDeals {

        private final Pageable pageable = PageRequest.of(0, 20);

        @Test
        @DisplayName("should list all deals without filter")
        void shouldListAllDeals() {
            Page<Deal> page = new PageImpl<>(List.of(deal));
            when(dealRepository.findAllByDeletedFalse(pageable)).thenReturn(page);
            when(dealMapper.toDto(deal)).thenReturn(dealDto);

            Page<DealDto> result = dealService.list(pageable, null);

            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("should list deals with stage filter")
        void shouldListDealsWithStageFilter() {
            Page<Deal> page = new PageImpl<>(List.of(deal));
            when(dealRepository.findByStageAndDeletedFalse(DealStage.PROSPECTING, pageable)).thenReturn(page);
            when(dealMapper.toDto(deal)).thenReturn(dealDto);

            Page<DealDto> result = dealService.list(pageable, DealStage.PROSPECTING);

            assertThat(result.getContent()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("search")
    class Search {

        @Test
        @DisplayName("should search deals by name")
        void shouldSearchDeals() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<Deal> page = new PageImpl<>(List.of(deal));
            when(dealRepository.searchByName("Big", pageable)).thenReturn(page);
            when(dealMapper.toDto(deal)).thenReturn(dealDto);

            Page<DealDto> result = dealService.search("Big", pageable);

            assertThat(result.getContent()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("advanceStage")
    class AdvanceStage {

        @Test
        @DisplayName("should advance from PROSPECTING to QUALIFICATION")
        void shouldAdvanceFromProspecting() {
            deal.setStage(DealStage.PROSPECTING);
            when(dealRepository.findById(1L)).thenReturn(Optional.of(deal));
            when(dealRepository.save(deal)).thenReturn(deal);
            when(dealMapper.toDto(deal)).thenReturn(dealDto);

            dealService.advanceStage(1L);

            assertThat(deal.getStage()).isEqualTo(DealStage.QUALIFICATION);
        }

        @Test
        @DisplayName("should advance from QUALIFICATION to NEEDS_ANALYSIS")
        void shouldAdvanceFromQualification() {
            deal.setStage(DealStage.QUALIFICATION);
            when(dealRepository.findById(1L)).thenReturn(Optional.of(deal));
            when(dealRepository.save(deal)).thenReturn(deal);
            when(dealMapper.toDto(deal)).thenReturn(dealDto);

            dealService.advanceStage(1L);

            assertThat(deal.getStage()).isEqualTo(DealStage.NEEDS_ANALYSIS);
        }

        @Test
        @DisplayName("should advance from NEEDS_ANALYSIS to PROPOSAL")
        void shouldAdvanceFromNeedsAnalysis() {
            deal.setStage(DealStage.NEEDS_ANALYSIS);
            when(dealRepository.findById(1L)).thenReturn(Optional.of(deal));
            when(dealRepository.save(deal)).thenReturn(deal);
            when(dealMapper.toDto(deal)).thenReturn(dealDto);

            dealService.advanceStage(1L);

            assertThat(deal.getStage()).isEqualTo(DealStage.PROPOSAL);
        }

        @Test
        @DisplayName("should advance from PROPOSAL to NEGOTIATION")
        void shouldAdvanceFromProposal() {
            deal.setStage(DealStage.PROPOSAL);
            when(dealRepository.findById(1L)).thenReturn(Optional.of(deal));
            when(dealRepository.save(deal)).thenReturn(deal);
            when(dealMapper.toDto(deal)).thenReturn(dealDto);

            dealService.advanceStage(1L);

            assertThat(deal.getStage()).isEqualTo(DealStage.NEGOTIATION);
        }

        @Test
        @DisplayName("should throw when advancing WON deal")
        void shouldThrowWhenAdvancingWon() {
            deal.setStage(DealStage.WON);
            when(dealRepository.findById(1L)).thenReturn(Optional.of(deal));

            assertThatThrownBy(() -> dealService.advanceStage(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("WON");
        }

        @Test
        @DisplayName("should throw when advancing LOST deal")
        void shouldThrowWhenAdvancingLost() {
            deal.setStage(DealStage.LOST);
            when(dealRepository.findById(1L)).thenReturn(Optional.of(deal));

            assertThatThrownBy(() -> dealService.advanceStage(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("LOST");
        }

        @Test
        @DisplayName("should throw when advancing from NEGOTIATION")
        void shouldThrowWhenAdvancingFromNegotiation() {
            deal.setStage(DealStage.NEGOTIATION);
            when(dealRepository.findById(1L)).thenReturn(Optional.of(deal));

            assertThatThrownBy(() -> dealService.advanceStage(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("final stage");
        }

        @Test
        @DisplayName("should throw when deal not found")
        void shouldThrowWhenNotFound() {
            when(dealRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> dealService.advanceStage(999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("should throw when deal is deleted")
        void shouldThrowWhenDeleted() {
            deal.setDeleted(true);
            when(dealRepository.findById(1L)).thenReturn(Optional.of(deal));

            assertThatThrownBy(() -> dealService.advanceStage(1L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("win")
    class Win {

        @Test
        @DisplayName("should mark deal as won")
        void shouldMarkDealAsWon() {
            deal.setStage(DealStage.NEGOTIATION);
            when(dealRepository.findById(1L)).thenReturn(Optional.of(deal));
            when(dealRepository.save(deal)).thenReturn(deal);
            when(dealMapper.toDto(deal)).thenReturn(dealDto);

            dealService.win(1L);

            assertThat(deal.getStage()).isEqualTo(DealStage.WON);
            assertThat(deal.getProbability()).isEqualTo(100);
            assertThat(deal.getWonAt()).isNotNull();
        }

        @Test
        @DisplayName("should throw when deal is already WON")
        void shouldThrowWhenAlreadyWon() {
            deal.setStage(DealStage.WON);
            when(dealRepository.findById(1L)).thenReturn(Optional.of(deal));

            assertThatThrownBy(() -> dealService.win(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("already marked as WON");
        }

        @Test
        @DisplayName("should throw when deal is LOST")
        void shouldThrowWhenLostOnWin() {
            deal.setStage(DealStage.LOST);
            when(dealRepository.findById(1L)).thenReturn(Optional.of(deal));

            assertThatThrownBy(() -> dealService.win(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("lost deal");
        }
    }

    @Nested
    @DisplayName("lose")
    class Lose {

        private final DealLoseRequest loseRequest = DealLoseRequest.builder().reason("Competitor won").build();

        @Test
        @DisplayName("should mark deal as lost")
        void shouldMarkDealAsLost() {
            when(dealRepository.findById(1L)).thenReturn(Optional.of(deal));
            when(dealRepository.save(deal)).thenReturn(deal);
            when(dealMapper.toDto(deal)).thenReturn(dealDto);

            dealService.lose(1L, loseRequest);

            assertThat(deal.getStage()).isEqualTo(DealStage.LOST);
            assertThat(deal.getProbability()).isEqualTo(0);
            assertThat(deal.getLostReason()).isEqualTo("Competitor won");
        }

        @Test
        @DisplayName("should throw when deal is already WON")
        void shouldThrowWhenWonOnLose() {
            deal.setStage(DealStage.WON);
            when(dealRepository.findById(1L)).thenReturn(Optional.of(deal));

            assertThatThrownBy(() -> dealService.lose(1L, loseRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("won deal");
        }

        @Test
        @DisplayName("should throw when deal is already LOST")
        void shouldThrowWhenAlreadyLost() {
            deal.setStage(DealStage.LOST);
            when(dealRepository.findById(1L)).thenReturn(Optional.of(deal));

            assertThatThrownBy(() -> dealService.lose(1L, loseRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("already marked as LOST");
        }
    }

    @Nested
    @DisplayName("getPipelineSummary")
    class GetPipelineSummary {

        @Test
        @DisplayName("should return pipeline summary")
        void shouldReturnPipelineSummary() {
            List<PipelineSummaryProjection> mockResults = List.of(
                    createMockProjection(DealStage.PROSPECTING, 5L, BigDecimal.valueOf(100000), BigDecimal.valueOf(10000)),
                    createMockProjection(DealStage.QUALIFICATION, 3L, BigDecimal.valueOf(75000), BigDecimal.valueOf(15000)),
                    createMockProjection(DealStage.NEGOTIATION, 2L, BigDecimal.valueOf(200000), BigDecimal.valueOf(160000))
            );
            when(dealRepository.findPipelineSummary()).thenReturn(mockResults);

            List<PipelineSummaryDto> result = dealService.getPipelineSummary();

            assertThat(result).hasSize(3);
            assertThat(result.get(0).getStage()).isEqualTo(DealStage.PROSPECTING);
            assertThat(result.get(0).getCount()).isEqualTo(5L);
            assertThat(result.get(0).getTotalAmount()).isEqualTo(BigDecimal.valueOf(100000));
            assertThat(result.get(1).getStage()).isEqualTo(DealStage.QUALIFICATION);
            assertThat(result.get(2).getStage()).isEqualTo(DealStage.NEGOTIATION);
        }

        @Test
        @DisplayName("should return empty summary when no deals")
        void shouldReturnEmptySummary() {
            when(dealRepository.findPipelineSummary()).thenReturn(List.of());

            List<PipelineSummaryDto> result = dealService.getPipelineSummary();

            assertThat(result).isEmpty();
        }
    }
}
