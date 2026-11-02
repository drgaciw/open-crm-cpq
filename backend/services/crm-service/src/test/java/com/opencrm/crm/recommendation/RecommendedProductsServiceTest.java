package com.opencrm.crm.recommendation;

import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.crm.deal.Deal;
import com.opencrm.crm.deal.DealRepository;
import com.opencrm.crm.deal.DealStage;
import com.opencrm.crm.deal.DealType;
import com.opencrm.crm.organization.Organization;
import com.opencrm.crm.organization.OrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecommendedProductsService")
class RecommendedProductsServiceTest {

    private static final long DEAL_ID = 3L;

    @Mock
    private DealRepository dealRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private RecommendationEvidenceRepository evidenceRepository;

    private final DeterministicProductRanker ranker = new DeterministicProductRanker();

    private Deal deal;
    private Organization organization;

    @BeforeEach
    void setUp() {
        organization = Organization.builder()
                .id(7L)
                .name("Acme")
                .industry("Software")
                .deleted(false)
                .build();
        deal = Deal.builder()
                .id(DEAL_ID)
                .name("Analytics rollout")
                .stage(DealStage.PROPOSAL)
                .type(DealType.NEW_BUSINESS)
                .amount(new BigDecimal("50000"))
                .organization(organization)
                .deleted(false)
                .build();
    }

    @Test
    @DisplayName("recommend is a read-only transaction")
    void recommendIsReadOnly() throws Exception {
        Transactional transactional = RecommendedProductsService.class
                .getMethod("recommend", Long.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isTrue();
    }

    @Test
    @DisplayName("a gate that rejects one id removes that id and leaves the others")
    void dropsTheIdTheGateRejects() {
        when(dealRepository.findById(DEAL_ID)).thenReturn(Optional.of(deal));
        when(organizationRepository.findById(7L)).thenReturn(Optional.of(organization));
        when(dealRepository.findAllByOrganization_Id(7L)).thenReturn(List.of(deal));
        when(evidenceRepository.findPeerWins(any(), any(), any(), any(), any())).thenReturn(List.of());

        List<RecommendedProductDto> all = service((subject, ids) -> Set.of()).recommend(DEAL_ID);
        assertThat(all).hasSizeGreaterThan(1);
        String rejected = all.get(0).productId();
        List<RecommendedProductDto> expected = all.subList(1, all.size());

        List<RecommendedProductDto> filtered = service((subject, ids) -> Set.of(rejected)).recommend(DEAL_ID);

        assertThat(filtered).extracting(RecommendedProductDto::productId).doesNotContain(rejected);
        assertThat(filtered).containsExactlyElementsOf(expected);
        assertThat(filtered).allSatisfy(item -> assertThat(item.rationale()).isNotBlank());
        assertThat(deal.getStage()).isEqualTo(DealStage.PROPOSAL);
        assertThat(deal.getAmount()).isEqualByComparingTo("50000");
        verify(dealRepository, never()).save(any());
        verify(organizationRepository, never()).save(any());
    }

    @Test
    @DisplayName("missing and deleted deals are not found and are not ranked")
    void missingOrDeletedDealIsNotFound() {
        when(dealRepository.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service((subject, ids) -> Set.of()).recommend(9L))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(organizationRepository, evidenceRepository);

        Deal deleted = Deal.builder().id(4L).name("Gone").stage(DealStage.PROPOSAL).deleted(true).build();
        when(dealRepository.findById(4L)).thenReturn(Optional.of(deleted));
        assertThatThrownBy(() -> service((subject, ids) -> Set.of()).recommend(4L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(dealRepository, never()).save(any());
    }

    @Test
    @DisplayName("peer wins are read only when the organization repository returns a live industry")
    void readsPeerWinsForALiveIndustry() {
        when(dealRepository.findById(DEAL_ID)).thenReturn(Optional.of(deal));
        when(organizationRepository.findById(7L)).thenReturn(Optional.of(organization));
        when(dealRepository.findAllByOrganization_Id(7L)).thenReturn(List.of());
        when(evidenceRepository.findPeerWins(eq(DealStage.WON), eq(7L), eq(DEAL_ID), eq("Software"), any(Pageable.class)))
                .thenReturn(List.of());

        assertThat(service((subject, ids) -> Set.of()).recommend(DEAL_ID)).isNotEmpty();

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(evidenceRepository).findPeerWins(eq(DealStage.WON), eq(7L), eq(DEAL_ID), eq("Software"), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(RecommendedProductsService.PEER_WIN_LIMIT);
        assertThat(pageable.getValue().getSort().isSorted()).isFalse();
    }

    @Test
    @DisplayName("a deleted organization contributes no account history and no peer wins")
    void deletedOrganizationIsIgnored() {
        organization.setDeleted(true);
        when(dealRepository.findById(DEAL_ID)).thenReturn(Optional.of(deal));
        when(organizationRepository.findById(7L)).thenReturn(Optional.of(organization));

        List<RecommendedProductDto> ranked = service((subject, ids) -> Set.of()).recommend(DEAL_ID);

        assertThat(ranked).isNotEmpty();
        verify(dealRepository, never()).findAllByOrganization_Id(any());
        verifyNoInteractions(evidenceRepository);
        verify(dealRepository, never()).save(any());
    }

    private RecommendedProductsService service(EligibilityGate gate) {
        return new RecommendedProductsService(
                dealRepository, organizationRepository, evidenceRepository, gate, ranker);
    }
}
