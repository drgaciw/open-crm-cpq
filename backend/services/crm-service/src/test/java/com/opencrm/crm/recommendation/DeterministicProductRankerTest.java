package com.opencrm.crm.recommendation;

import com.opencrm.crm.deal.Deal;
import com.opencrm.crm.deal.DealStage;
import com.opencrm.crm.deal.DealType;
import com.opencrm.crm.organization.Organization;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DeterministicProductRanker")
class DeterministicProductRankerTest {

    private final DeterministicProductRanker ranker = new DeterministicProductRanker();

    @Test
    @DisplayName("ranks catalog, account wins, and peer wins without changing the deal")
    void ranksSignalsAndLeavesTheDealAlone() {
        Deal deal = deal(3L, "Analytics rollout", DealType.NEW_BUSINESS, DealStage.PROPOSAL);
        Organization organization = Organization.builder().id(7L).name("Acme").industry(" Software ").deleted(false).build();
        deal.setOrganization(organization);
        List<Deal> account = new ArrayList<>(List.of(
                deal(3L, "self", DealType.NEW_BUSINESS, DealStage.WON),
                deleted(98L, DealType.RENEWAL),
                deal(99L, "seats", DealType.UPSELL, DealStage.WON)));
        List<Deal> peers = List.of(deal(100L, "peer", DealType.UPSELL, DealStage.WON));

        List<RecommendedProductDto> ranked = ranker.rank(deal, organization, account, peers);

        assertThat(deal.getStage()).isEqualTo(DealStage.PROPOSAL);
        assertThat(account).extracting(Deal::getId).containsExactly(3L, 98L, 99L);
        assertThat(ranked).extracting(RecommendedProductDto::productId).containsExactly(
                "addon-analytics",
                "services-onboarding",
                "subscription-platform",
                "license-expansion");
        assertThat(ranked).extracting(RecommendedProductDto::productId).doesNotContain("support-success-plan");
        assertThat(confidence(ranked, "addon-analytics")).isEqualByComparingTo("1.00");
        assertThat(confidence(ranked, "services-onboarding")).isEqualByComparingTo("0.65");
        assertThat(confidence(ranked, "subscription-platform")).isEqualByComparingTo("0.65");
        assertThat(confidence(ranked, "license-expansion")).isEqualByComparingTo("0.50");
        assertThat(ranked).allSatisfy(item -> {
            assertThat(item.rationale()).isNotBlank();
            assertThat(item.confidence()).isBetween(BigDecimal.ZERO, BigDecimal.ONE);
        });
        assertThat(rationale(ranked, "addon-analytics"))
                .contains("Advisory suggestion")
                .contains("analytics")
                .contains("software")
                .contains("This account has 1 won deal");

        List<Deal> reversed = new ArrayList<>(List.of(account.get(2), account.get(0), account.get(1)));
        assertThat(ranker.rank(deal, organization, reversed, peers)).isEqualTo(ranked);
        assertThat(ranker.rank(deal, organization, account, peers)).isEqualTo(ranked);
    }

    @Test
    @DisplayName("a deal with no catalog, account, or win signal still returns general companions")
    void fallsBackToGeneralCompanions() {
        Deal deal = deal(3L, "x", null, DealStage.QUALIFICATION);

        List<RecommendedProductDto> ranked = ranker.rank(deal, null, List.of(), List.of());

        assertThat(ranked).extracting(RecommendedProductDto::productId).containsExactly(
                "services-onboarding",
                "subscription-platform",
                "support-success-plan");
        assertThat(ranked).allSatisfy(item -> {
            assertThat(item.confidence()).isEqualByComparingTo("0.40");
            assertThat(item.rationale()).contains("general companion").isNotBlank();
        });
    }

    @Test
    @DisplayName("a keyword match is enough to surface one product")
    void keywordAloneSurfacesTheMatchingProduct() {
        Deal deal = deal(3L, "Retention review", null, DealStage.PROSPECTING);

        List<RecommendedProductDto> ranked = ranker.rank(deal, null, List.of(), List.of());

        assertThat(ranked).extracting(RecommendedProductDto::productId).containsExactly("support-success-plan");
        assertThat(ranked.get(0).confidence()).isEqualByComparingTo("0.50");
        assertThat(ranked.get(0).rationale()).contains("retention");
    }

    private static BigDecimal confidence(List<RecommendedProductDto> ranked, String productId) {
        return ranked.stream()
                .filter(item -> item.productId().equals(productId))
                .findFirst()
                .orElseThrow()
                .confidence();
    }

    private static String rationale(List<RecommendedProductDto> ranked, String productId) {
        return ranked.stream()
                .filter(item -> item.productId().equals(productId))
                .findFirst()
                .orElseThrow()
                .rationale();
    }

    private static Deal deal(Long id, String name, DealType type, DealStage stage) {
        Deal deal = Deal.builder()
                .id(id)
                .name(name)
                .stage(stage)
                .deleted(false)
                .build();
        deal.setType(type);
        return deal;
    }

    private static Deal deleted(Long id, DealType type) {
        Deal deal = deal(id, "gone", type, DealStage.WON);
        deal.setDeleted(true);
        return deal;
    }
}
