package com.opencrm.crm.deal;

import com.opencrm.common.exception.BusinessException;
import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.crm.contact.Contact;
import com.opencrm.crm.contact.ContactRepository;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DealServiceTest {

    @Mock
    private DealRepository dealRepository;

    @Mock
    private ContactRepository contactRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private DealMapper dealMapper;

    @Mock
    private DealCurrencyConverter currencyConverter;

    @Mock
    private CrmDomainEvents domainEvents;

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
            var inOrder = inOrder(currencyConverter, dealRepository, domainEvents);
            inOrder.verify(currencyConverter).freeze(deal);
            inOrder.verify(dealRepository).save(deal);
            inOrder.verify(domainEvents).dealCreated(deal);
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
        void updateToWonUsesClosePathAfterMappingMoney() {
            assertTerminalUpdate(DealStage.WON, 100);
        }

        @Test
        void updateToLostUsesClosePathAfterMappingMoney() {
            assertTerminalUpdate(DealStage.LOST, 0);
        }

        @Test
        @DisplayName("should reject an update to WON when no accepted quote is recorded")
        void shouldRejectUpdateToWonWithoutAcceptedQuote() {
            updateRequest.setStage(DealStage.WON);
            when(dealRepository.findById(1L)).thenReturn(Optional.of(deal));
            doAnswer(inv -> {
                Deal target = inv.getArgument(1);
                target.setStage(DealStage.WON);
                return null;
            }).when(dealMapper).updateEntity(updateRequest, deal);

            assertThatThrownBy(() -> dealService.update(1L, updateRequest))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo("OPPORTUNITY_WON_REQUIRES_ACCEPTED_QUOTE"));
            verify(dealRepository, never()).save(any());
            verify(domainEvents, never()).dealTransition(any(), any());
        }

        private void assertTerminalUpdate(DealStage stage, int probability) {
            updateRequest.setStage(stage);
            if (stage == DealStage.WON) {
                deal.setAcceptedQuoteId("quote-accepted");
            }
            when(dealRepository.findById(1L)).thenReturn(Optional.of(deal));
            doAnswer(inv -> {
                Deal target = inv.getArgument(1);
                target.setStage(stage);
                target.setAmount(new BigDecimal("2000.00"));
                target.setCurrency("GBP");
                return null;
            }).when(dealMapper).updateEntity(updateRequest, deal);
            doAnswer(inv -> {
                Deal closed = inv.getArgument(0);
                assertThat(closed.getAmount()).isEqualByComparingTo("2000.00");
                assertThat(closed.getCurrency()).isEqualTo("GBP");
                assertThat(closed.getProbability()).isEqualTo(probability);
                return null;
            }).when(currencyConverter).freeze(deal);
            when(dealRepository.save(deal)).thenReturn(deal);
            when(dealMapper.toDto(deal)).thenReturn(dealDto);

            dealService.update(1L, updateRequest);

            verify(currencyConverter).freeze(deal);
            verify(currencyConverter, never()).freezeIfNeeded(any(), any(), any());
            verify(domainEvents).dealTransition(deal, DealStage.PROSPECTING);
        }

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
        @DisplayName("should re-check the base amount with the amount and currency from before the edit (ADR-032 D-03)")
        void shouldPassPreviousAmountAndCurrencyToConverter() {
            deal.setAmount(new BigDecimal("1000.00"));
            deal.setCurrency("EUR");
            when(dealRepository.findById(1L)).thenReturn(Optional.of(deal));
            doAnswer(inv -> {
                Deal target = inv.getArgument(1);
                target.setAmount(new BigDecimal("2000.00"));
                target.setCurrency("GBP");
                return null;
            }).when(dealMapper).updateEntity(updateRequest, deal);
            when(dealRepository.save(deal)).thenReturn(deal);
            when(dealMapper.toDto(deal)).thenReturn(dealDto);

            dealService.update(1L, updateRequest);

            verify(currencyConverter).freezeIfNeeded(deal, new BigDecimal("1000.00"), "EUR");
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
        @DisplayName("should reject an amount change on a won deal without re-freezing")
        void shouldRejectAmountChangeOnWonDeal() {
            deal.setStage(DealStage.WON);
            deal.setAmount(new BigDecimal("1000.00"));
            deal.setCurrency("EUR");
            updateRequest.setAmount(new BigDecimal("2000.00"));
            when(dealRepository.findById(1L)).thenReturn(Optional.of(deal));

            assertThatThrownBy(() -> dealService.update(1L, updateRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("amount or currency")
                    .hasMessageContaining("WON or LOST");

            verify(dealMapper, never()).updateEntity(any(), any());
            verify(currencyConverter, never()).freezeIfNeeded(any(), any(), any());
            verify(currencyConverter, never()).freeze(any());
            verify(dealRepository, never()).save(any());
        }

        @Test
        @DisplayName("should reject a currency change on a lost deal without re-freezing")
        void shouldRejectCurrencyChangeOnLostDeal() {
            deal.setStage(DealStage.LOST);
            deal.setAmount(new BigDecimal("1000.00"));
            deal.setCurrency("EUR");
            updateRequest.setCurrency("USD");
            when(dealRepository.findById(1L)).thenReturn(Optional.of(deal));

            assertThatThrownBy(() -> dealService.update(1L, updateRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("amount or currency");

            verify(dealMapper, never()).updateEntity(any(), any());
            verify(currencyConverter, never()).freezeIfNeeded(any(), any(), any());
            verify(dealRepository, never()).save(any());
        }

        @Test
        @DisplayName("should keep a closed deal's frozen conversion on a non-financial edit")
        void shouldNotRefreezeClosedDealOnNonFinancialEdit() {
            deal.setStage(DealStage.WON);
            deal.setAmount(new BigDecimal("1000.00"));
            deal.setCurrency("EUR");
            when(dealRepository.findById(1L)).thenReturn(Optional.of(deal));
            when(dealRepository.save(deal)).thenReturn(deal);
            when(dealMapper.toDto(deal)).thenReturn(dealDto);

            DealDto result = dealService.update(1L, updateRequest);

            assertThat(result).isNotNull();
            verify(dealMapper).updateEntity(updateRequest, deal);
            verify(currencyConverter, never()).freezeIfNeeded(any(), any(), any());
            verify(currencyConverter, never()).freeze(any());
        }

        @Test
        @DisplayName("should allow a closed-deal update that repeats the same amount and currency")
        void shouldAllowClosedDealUpdateWithSameMoney() {
            deal.setStage(DealStage.LOST);
            deal.setAmount(new BigDecimal("1000.00"));
            deal.setCurrency("EUR");
            updateRequest.setAmount(new BigDecimal("1000.0"));
            updateRequest.setCurrency("eur");
            when(dealRepository.findById(1L)).thenReturn(Optional.of(deal));
            when(dealRepository.save(deal)).thenReturn(deal);
            when(dealMapper.toDto(deal)).thenReturn(dealDto);

            dealService.update(1L, updateRequest);

            verify(dealMapper).updateEntity(updateRequest, deal);
            verify(currencyConverter, never()).freezeIfNeeded(any(), any(), any());
            verify(currencyConverter, never()).freeze(any());
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
        @DisplayName("should mark deal as won when an accepted quote is already recorded")
        void shouldMarkDealAsWon() {
            deal.setStage(DealStage.NEGOTIATION);
            deal.setAcceptedQuoteId("quote-uuid-1");
            when(dealRepository.findById(1L)).thenReturn(Optional.of(deal));
            when(dealRepository.save(deal)).thenReturn(deal);
            when(dealMapper.toDto(deal)).thenReturn(dealDto);

            dealService.win(1L);

            verify(currencyConverter).freeze(deal);
            assertThat(deal.getStage()).isEqualTo(DealStage.WON);
            assertThat(deal.getProbability()).isEqualTo(100);
            assertThat(deal.getWonAt()).isNotNull();
        }

        @Test
        @DisplayName("should reject a win when no accepted quote is recorded")
        void shouldRejectWinWithoutAcceptedQuote() {
            deal.setStage(DealStage.NEGOTIATION);
            when(dealRepository.findById(1L)).thenReturn(Optional.of(deal));

            assertThatThrownBy(() -> dealService.win(1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo("OPPORTUNITY_WON_REQUIRES_ACCEPTED_QUOTE"));
            verify(dealRepository, never()).save(any());
        }

        @Test
        @DisplayName("should reject a win when the recorded accepted quote id is blank")
        void shouldRejectWinWhenAcceptedQuoteIsBlank() {
            deal.setStage(DealStage.NEGOTIATION);
            deal.setAcceptedQuoteId("  ");
            when(dealRepository.findById(1L)).thenReturn(Optional.of(deal));

            assertThatThrownBy(() -> dealService.win(1L))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo("OPPORTUNITY_WON_REQUIRES_ACCEPTED_QUOTE"));
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

            verify(currencyConverter).freeze(deal);
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
        @DisplayName("should return pipeline summary in the base currency")
        void shouldReturnPipelineSummary() {
            List<PipelineSummaryProjection> mockResults = List.of(
                    projection(DealStage.PROSPECTING, 5L, BigDecimal.valueOf(100000), BigDecimal.valueOf(10000), 0L),
                    projection(DealStage.QUALIFICATION, 3L, BigDecimal.valueOf(75000), BigDecimal.valueOf(15000), 1L),
                    projection(DealStage.NEGOTIATION, 2L, BigDecimal.valueOf(200000), BigDecimal.valueOf(160000), 0L)
            );
            when(currencyConverter.baseCurrency()).thenReturn("USD");
            when(dealRepository.findPipelineSummary("USD")).thenReturn(mockResults);

            List<PipelineSummaryDto> result = dealService.getPipelineSummary();

            assertThat(result).hasSize(3);
            assertThat(result.get(0).getStage()).isEqualTo(DealStage.PROSPECTING);
            assertThat(result.get(0).getCount()).isEqualTo(5L);
            assertThat(result.get(0).getTotalAmount()).isEqualTo(BigDecimal.valueOf(100000));
            assertThat(result).allSatisfy(row -> assertThat(row.getBaseCurrency()).isEqualTo("USD"));
            assertThat(result.get(1).getStage()).isEqualTo(DealStage.QUALIFICATION);
            assertThat(result.get(1).getUnconvertedCount()).isEqualTo(1L);
            assertThat(result.get(2).getStage()).isEqualTo(DealStage.NEGOTIATION);
        }

        @Test
        @DisplayName("should report zero unconverted deals when the projection returns null")
        void shouldDefaultUnconvertedCountToZero() {
            when(currencyConverter.baseCurrency()).thenReturn("EUR");
            when(dealRepository.findPipelineSummary("EUR")).thenReturn(List.of(
                    projection(DealStage.PROPOSAL, 1L, BigDecimal.TEN, BigDecimal.ONE, null)));

            List<PipelineSummaryDto> result = dealService.getPipelineSummary();

            assertThat(result.get(0).getUnconvertedCount()).isZero();
            assertThat(result.get(0).getBaseCurrency()).isEqualTo("EUR");
        }

        @Test
        @DisplayName("should return empty summary when no deals")
        void shouldReturnEmptySummary() {
            when(currencyConverter.baseCurrency()).thenReturn("USD");
            when(dealRepository.findPipelineSummary("USD")).thenReturn(List.of());

            List<PipelineSummaryDto> result = dealService.getPipelineSummary();

            assertThat(result).isEmpty();
        }

        private static PipelineSummaryProjection projection(DealStage stage, Long count, BigDecimal totalAmount,
                                                            BigDecimal weightedAmount, Long unconvertedCount) {
            return new PipelineSummaryRow(stage, count, totalAmount, weightedAmount, unconvertedCount);
        }

        private record PipelineSummaryRow(DealStage stage, Long count, BigDecimal totalAmount,
                                          BigDecimal weightedAmount, Long unconvertedCount)
                implements PipelineSummaryProjection {
            @Override
            public DealStage getStage() {
                return stage;
            }

            @Override
            public Long getCount() {
                return count;
            }

            @Override
            public BigDecimal getTotalAmount() {
                return totalAmount;
            }

            @Override
            public BigDecimal getWeightedAmount() {
                return weightedAmount;
            }

            @Override
            public Long getUnconvertedCount() {
                return unconvertedCount;
            }
        }
    }
}
