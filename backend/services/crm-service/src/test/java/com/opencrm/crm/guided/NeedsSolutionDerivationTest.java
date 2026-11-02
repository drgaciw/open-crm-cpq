package com.opencrm.crm.guided;

import com.opencrm.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Needs-based product derivation")
class NeedsSolutionDerivationTest {

    @Test
    @DisplayName("compliance wins over a small team and selects the governed suite")
    void complianceSelectsGovernedSuite() {
        NeedsSolutionDerivation.DerivedNeeds needs = NeedsSolutionDerivation.derive(Map.of(
                "priority", "compliance",
                "team-size", "small",
                "deployment", "cloud"));

        assertThat(needs.productId()).isEqualTo(NeedsSolutionDerivation.PRODUCT_GOVERNED);
        assertThat(needs.selections()).containsExactly(
                Map.entry("edition", "enterprise"),
                Map.entry("deployment", "private-cloud"),
                Map.entry("support", "premium"),
                Map.entry("data-residency", "required"));
    }

    @Test
    @DisplayName("on-prem wins over cost and keeps the on-prem option")
    void onPremSelectsGovernedSuite() {
        NeedsSolutionDerivation.DerivedNeeds needs = NeedsSolutionDerivation.derive(Map.of(
                "deployment", "on-prem",
                "priority", "cost",
                "team-size", "small"));

        assertThat(needs.productId()).isEqualTo(NeedsSolutionDerivation.PRODUCT_GOVERNED);
        assertThat(needs.selections()).containsEntry("deployment", "on-prem");
    }

    @Test
    @DisplayName("cost or a small team selects starter on cloud")
    void costOrSmallSelectsStarter() {
        NeedsSolutionDerivation.DerivedNeeds cost = NeedsSolutionDerivation.derive(Map.of(
                "priority", "cost",
                "team-size", "enterprise",
                "deployment", "hybrid"));
        NeedsSolutionDerivation.DerivedNeeds small = NeedsSolutionDerivation.derive(Map.of(
                "team-size", "small",
                "priority", "speed",
                "deployment", "hybrid"));

        assertThat(cost.productId()).isEqualTo(NeedsSolutionDerivation.PRODUCT_STARTER);
        assertThat(cost.selections()).containsExactly(
                Map.entry("edition", "standard"),
                Map.entry("deployment", "cloud"),
                Map.entry("support", "community"));
        assertThat(small.productId()).isEqualTo(NeedsSolutionDerivation.PRODUCT_STARTER);
        assertThat(small.selections()).containsEntry("deployment", "cloud");
    }

    @Test
    @DisplayName("an enterprise team selects the growth platform with premium support")
    void enterpriseSelectsGrowth() {
        NeedsSolutionDerivation.DerivedNeeds needs = NeedsSolutionDerivation.derive(Map.of(
                "team-size", "enterprise",
                "priority", "speed",
                "deployment", "hybrid"));

        assertThat(needs.productId()).isEqualTo(NeedsSolutionDerivation.PRODUCT_GROWTH);
        assertThat(needs.selections()).containsExactly(
                Map.entry("edition", "enterprise"),
                Map.entry("deployment", "hybrid"),
                Map.entry("support", "premium"));
    }

    @Test
    @DisplayName("missing answers use the professional growth default")
    void emptyAnswersUseDefault() {
        NeedsSolutionDerivation.DerivedNeeds needs = NeedsSolutionDerivation.derive(Map.of());

        assertThat(needs.productId()).isEqualTo(NeedsSolutionDerivation.PRODUCT_GROWTH);
        assertThat(needs.selections()).containsExactly(
                Map.entry("edition", "professional"),
                Map.entry("deployment", "cloud"),
                Map.entry("support", "standard"));
    }

    @Test
    @DisplayName("unknown answers are ignored and the same inputs stay deterministic")
    void unknownValuesAreIgnored() {
        Map<String, String> answers = new LinkedHashMap<>();
        answers.put("priority", "SPEED");
        answers.put("team-size", "mid");
        answers.put("notes", "ignore me");

        NeedsSolutionDerivation.DerivedNeeds first = NeedsSolutionDerivation.derive(answers);
        NeedsSolutionDerivation.DerivedNeeds second = NeedsSolutionDerivation.derive(answers);

        assertThat(first).isEqualTo(second);
        assertThat(first.selections()).containsEntry("edition", "professional");
    }

    @Test
    @DisplayName("only catalog questions and values are accepted at the answer boundary")
    void requireKnownRejectsUnknown() {
        assertThatThrownBy(() -> NeedsSolutionDerivation.requireKnown("sku", "abc"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("GUIDED_QUESTION_UNKNOWN");
        assertThatThrownBy(() -> NeedsSolutionDerivation.requireKnown("deployment", "laptop"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo("GUIDED_ANSWER_UNKNOWN");
    }

    @Test
    @DisplayName("the catalog is the three needs questions")
    void catalogListsQuestions() {
        assertThat(NeedsSolutionDerivation.catalog()).extracting(NeedsSolutionDerivation.Question::key)
                .containsExactly("deployment", "team-size", "priority");
    }
}
