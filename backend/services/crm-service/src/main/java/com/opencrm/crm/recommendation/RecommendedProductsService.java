package com.opencrm.crm.recommendation;

import com.opencrm.common.exception.ResourceNotFoundException;
import com.opencrm.crm.deal.Deal;
import com.opencrm.crm.deal.DealRepository;
import com.opencrm.crm.deal.DealStage;
import com.opencrm.crm.organization.Organization;
import com.opencrm.crm.organization.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Advisory product suggestions for one deal (ADR-015, DRG-488).
 *
 * <p>Reads the deal and its organization. Does not save, advance stage, mark won or lost, write a quote,
 * or change eligibility configuration. Ranking is {@link DeterministicProductRanker}; there is no Jev,
 * OpenRouter, or ai-service call.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendedProductsService {

    static final int PEER_WIN_LIMIT = 50;

    private static final Pageable PEER_WIN_PAGE = PageRequest.of(0, PEER_WIN_LIMIT);

    private final DealRepository dealRepository;
    private final OrganizationRepository organizationRepository;
    private final RecommendationEvidenceRepository evidenceRepository;
    private final EligibilityGate eligibilityGate;
    private final DeterministicProductRanker ranker;

    @Transactional(readOnly = true)
    public List<RecommendedProductDto> recommend(Long dealId) {
        Deal deal = dealRepository.findById(dealId)
                .orElseThrow(() -> new ResourceNotFoundException("Deal", dealId));
        if (deal.isDeleted()) {
            throw new ResourceNotFoundException("Deal", dealId);
        }

        Organization organization = readOrganization(deal);
        List<Deal> accountHistory = organization == null
                ? List.of()
                : dealRepository.findAllByOrganization_Id(organization.getId());
        List<Deal> peerWins = peerWins(deal, organization);

        List<RecommendedProductDto> ranked = ranker.rank(deal, organization, accountHistory, peerWins);
        List<String> candidateIds = new ArrayList<>(ranked.size());
        for (RecommendedProductDto item : ranked) {
            candidateIds.add(item.productId());
        }
        RecommendationSubject subject = new RecommendationSubject(
                deal.getId(),
                organization == null ? null : organization.getId(),
                organization == null ? null : organization.getIndustry(),
                deal.getType());
        Set<String> ineligible = eligibilityGate.ineligibleProductIds(subject, candidateIds);
        if (ineligible == null) {
            ineligible = Set.of();
        }

        List<RecommendedProductDto> kept = new ArrayList<>(ranked.size());
        for (RecommendedProductDto item : ranked) {
            if (!ineligible.contains(item.productId())) {
                kept.add(item);
            }
        }
        log.debug("Advisory recommendations for deal {}: {} of {} candidates kept", dealId, kept.size(), ranked.size());
        return List.copyOf(kept);
    }

    private Organization readOrganization(Deal deal) {
        Organization linked = deal.getOrganization();
        if (linked == null || linked.getId() == null) {
            return null;
        }
        return organizationRepository.findById(linked.getId())
                .filter(organization -> !organization.isDeleted())
                .orElse(null);
    }

    private List<Deal> peerWins(Deal deal, Organization organization) {
        if (organization == null || organization.getIndustry() == null || organization.getIndustry().isBlank()) {
            return List.of();
        }
        return evidenceRepository.findPeerWins(
                DealStage.WON,
                organization.getId(),
                deal.getId(),
                organization.getIndustry().trim(),
                PEER_WIN_PAGE);
    }
}
