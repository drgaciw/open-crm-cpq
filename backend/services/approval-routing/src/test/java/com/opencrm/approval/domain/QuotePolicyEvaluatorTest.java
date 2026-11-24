package com.opencrm.approval.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EvaluateQuote verdicts and ExplainPolicyBreach (BC-04-02, DRG-442).
 */
@DisplayName("EvaluateQuote")
class QuotePolicyEvaluatorTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 11, 24);

    @Test
    @DisplayName("Pass when discount, margin, term and clauses are inside every applicable limit")
    void passWhenEveryThresholdHolds() {
        QuotePolicyDecision decision = QuotePolicyEvaluator.evaluateQuote(facts("10", "40", 12, List.of()),
                List.of(policy(limits("25", "20", "36", "NET-90"))));

        assertThat(decision.verdict()).isEqualTo(PolicyVerdict.PASS);
        assertThat(decision.explainPolicyBreaches()).isEmpty();
        assertThat(decision.appliedPolicyIds()).containsExactly(7L);
    }

    @Test
    @DisplayName("RequiresApproval when discount exceeds the maximum, and the explanation is actual against limit")
    void requiresApprovalWhenDiscountExceedsLimit() {
        QuotePolicyDecision decision = QuotePolicyEvaluator.evaluateQuote(facts("32", "40", 12, List.of()),
                List.of(policy(limits("25", "20", "36", null))));

        assertThat(decision.verdict()).isEqualTo(PolicyVerdict.REQUIRES_APPROVAL);
        assertThat(decision.explainPolicyBreaches()).singleElement().satisfies(breach -> {
            assertThat(breach.thresholdType()).isEqualTo(ThresholdType.DISCOUNT_PERCENT);
            assertThat(breach.actual()).isEqualTo("32");
            assertThat(breach.limit()).isEqualTo("25");
            assertThat(breach.effect()).isEqualTo(BreachEffect.REQUIRES_APPROVAL);
            assertThat(breach.policyId()).isEqualTo(7L);
            assertThat(breach.policyVersion()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("RequiresApproval when the term or a restricted clause is breached, including a value equal to the limit as a pass")
    void requiresApprovalForTermAndClauseAndTreatsTheLimitAsInside() {
        QuotePolicyDecision atLimit = QuotePolicyEvaluator.evaluateQuote(facts("25", "20", 36, List.of("standard")),
                List.of(policy(limits("25", "20", "36", "NET-90"))));
        assertThat(atLimit.verdict()).isEqualTo(PolicyVerdict.PASS);

        QuotePolicyDecision term = QuotePolicyEvaluator.evaluateQuote(facts("10", "40", 48, List.of()),
                List.of(policy(limits("25", "20", "36", null))));
        assertThat(term.verdict()).isEqualTo(PolicyVerdict.REQUIRES_APPROVAL);
        assertThat(term.explainPolicyBreaches()).singleElement().satisfies(breach -> {
            assertThat(breach.thresholdType()).isEqualTo(ThresholdType.TERM_MONTHS);
            assertThat(breach.actual()).isEqualTo("48");
            assertThat(breach.limit()).isEqualTo("36");
        });

        QuotePolicyDecision clause = QuotePolicyEvaluator.evaluateQuote(facts("10", "40", 12, List.of("net-90")),
                List.of(policy(limits("25", "20", "36", "NET-90"))));
        assertThat(clause.verdict()).isEqualTo(PolicyVerdict.REQUIRES_APPROVAL);
        assertThat(clause.explainPolicyBreaches()).singleElement().satisfies(breach -> {
            assertThat(breach.thresholdType()).isEqualTo(ThresholdType.NON_STANDARD_CLAUSE);
            assertThat(breach.actual()).isEqualTo("net-90");
            assertThat(breach.limit()).isEqualTo("NET-90");
        });
    }

    @Test
    @DisplayName("Blocked when margin is below the floor, and a block dominates an approval breach")
    void blockedWhenMarginIsBelowTheFloor() {
        QuotePolicyDecision margin = QuotePolicyEvaluator.evaluateQuote(facts("10", "15", 12, List.of()),
                List.of(policy(limits("25", "20", "36", null))));
        assertThat(margin.verdict()).isEqualTo(PolicyVerdict.BLOCKED);
        assertThat(margin.explainPolicyBreaches()).singleElement().satisfies(breach -> {
            assertThat(breach.thresholdType()).isEqualTo(ThresholdType.MARGIN_FLOOR);
            assertThat(breach.actual()).isEqualTo("15");
            assertThat(breach.limit()).isEqualTo("20");
            assertThat(breach.effect()).isEqualTo(BreachEffect.BLOCKED);
            assertThat(breach.reasonCode()).isEqualTo("MARGIN_BELOW_FLOOR");
        });

        QuotePolicyDecision both = QuotePolicyEvaluator.evaluateQuote(facts("32", "15", 12, List.of()),
                List.of(policy(limits("25", "20", "36", null))));
        assertThat(both.verdict()).isEqualTo(PolicyVerdict.BLOCKED);
        assertThat(both.explainPolicyBreaches()).extracting(PolicyBreach::thresholdType)
                .containsExactly(ThresholdType.DISCOUNT_PERCENT, ThresholdType.MARGIN_FLOOR);
        assertThat(both.explainPolicyBreaches()).extracting(PolicyBreach::actual).containsExactly("32", "15");
        assertThat(both.explainPolicyBreaches()).extracting(PolicyBreach::limit).containsExactly("25", "20");
    }

    @Test
    @DisplayName("Blocked when a governed fact was not measured, with a reason code per fact")
    void blockedWhenTheActualValueIsUnmeasured() {
        QuotePolicyDecision decision = QuotePolicyEvaluator.evaluateQuote(
                facts(null, null, null, List.of()),
                List.of(policy(limits("25", "20", "36", null))));

        assertThat(decision.verdict()).isEqualTo(PolicyVerdict.BLOCKED);
        assertThat(decision.explainPolicyBreaches()).allSatisfy(breach -> {
            assertThat(breach.actual()).isEqualTo("unmeasured");
            assertThat(breach.effect()).isEqualTo(BreachEffect.BLOCKED);
        });
        assertThat(decision.explainPolicyBreaches()).extracting(PolicyBreach::limit)
                .containsExactly("25", "20", "36");
        assertThat(decision.explainPolicyBreaches()).extracting(PolicyBreach::reasonCode)
                .containsExactly("DISCOUNT_UNKNOWN", "MARGIN_UNKNOWN", "TERM_UNKNOWN");
    }

    @Test
    @DisplayName("Missing marginPercent is Blocked with MARGIN_UNKNOWN")
    void missingMarginIsBlockedAsMarginUnknown() {
        QuotePolicyDecision decision = QuotePolicyEvaluator.evaluateQuote(
                facts("10", null, 12, List.of()),
                List.of(policy(limits("25", "20", "36", null))));

        assertThat(decision.verdict()).isEqualTo(PolicyVerdict.BLOCKED);
        assertThat(decision.explainPolicyBreaches()).singleElement().satisfies(breach -> {
            assertThat(breach.thresholdType()).isEqualTo(ThresholdType.MARGIN_FLOOR);
            assertThat(breach.actual()).isEqualTo("unmeasured");
            assertThat(breach.limit()).isEqualTo("20");
            assertThat(breach.reasonCode()).isEqualTo("MARGIN_UNKNOWN");
        });
    }

    @Test
    @DisplayName("Margin above the floor passes, and a discount breach then requires approval only")
    void marginAboveTheFloorDoesNotBlock() {
        QuotePolicyDecision above = QuotePolicyEvaluator.evaluateQuote(
                facts("10", "21", 12, List.of()),
                List.of(policy(limits("25", "20", "36", null))));
        assertThat(above.verdict()).isEqualTo(PolicyVerdict.PASS);
        assertThat(above.explainPolicyBreaches()).isEmpty();

        QuotePolicyDecision discountOnly = QuotePolicyEvaluator.evaluateQuote(
                facts("32", "21", 12, List.of()),
                List.of(policy(limits("25", "20", "36", null))));
        assertThat(discountOnly.verdict()).isEqualTo(PolicyVerdict.REQUIRES_APPROVAL);
        assertThat(discountOnly.explainPolicyBreaches()).singleElement().satisfies(breach -> {
            assertThat(breach.thresholdType()).isEqualTo(ThresholdType.DISCOUNT_PERCENT);
            assertThat(breach.reasonCode()).isNull();
        });
    }

    @Test
    @DisplayName("Pass when no policy applies, and ignore deprecated, expired, and out-of-scope versions")
    void ignoresPoliciesThatDoNotApply() {
        CommercialPolicy expired = policy(limits("1", "99", "1", "ANY"));
        expired.setEffectiveDate(AS_OF.minusMonths(6));
        expired.setExpirationDate(AS_OF.minusDays(1));
        CommercialPolicy otherRegion = policy(limits("1", "99", "1", "ANY"));
        otherRegion.setId(8L);
        otherRegion.setRegion("EMEA");
        CommercialPolicy deprecated = policy(limits("1", "99", "1", "ANY"));
        deprecated.setId(9L);
        deprecated.setStatus(PolicyStatus.DEPRECATED);

        QuotePolicyDecision decision = QuotePolicyEvaluator.evaluateQuote(
                facts("90", "1", 120, List.of("ANY")),
                List.of(expired, otherRegion, deprecated));

        assertThat(decision.verdict()).isEqualTo(PolicyVerdict.PASS);
        assertThat(decision.explainPolicyBreaches()).isEmpty();
        assertThat(decision.appliedPolicyIds()).isEmpty();
    }

    private static final Instant NOW = Instant.parse("2026-11-24T18:00:00Z");

    @Test
    @DisplayName("an active exception for this quote version and policy satisfies the breach")
    void activeExceptionSatisfiesEvaluation() {
        PolicyExceptionGrant active = grant(3L, "tenant-1", NOW.plusSeconds(3600));

        QuotePolicyDecision decision = QuotePolicyEvaluator.evaluateQuote(
                facts("32", "40", 12, List.of()),
                List.of(policy(limits("25", "20", "36", null))),
                List.of(active),
                NOW);

        assertThat(decision.verdict()).isEqualTo(PolicyVerdict.PASS);
        assertThat(decision.explainPolicyBreaches()).isEmpty();
    }

    @Test
    @DisplayName("an expired exception does not satisfy evaluation")
    void expiredExceptionFailsEvaluation() {
        PolicyExceptionGrant expired = grant(3L, "tenant-1", NOW.minusSeconds(1));

        QuotePolicyDecision decision = QuotePolicyEvaluator.evaluateQuote(
                facts("32", "40", 12, List.of()),
                List.of(policy(limits("25", "20", "36", null))),
                List.of(expired),
                NOW);

        assertThat(decision.verdict()).isEqualTo(PolicyVerdict.REQUIRES_APPROVAL);
        assertThat(decision.explainPolicyBreaches()).singleElement()
                .satisfies(breach -> assertThat(breach.thresholdType()).isEqualTo(ThresholdType.DISCOUNT_PERCENT));
    }

    @Test
    @DisplayName("an exception for another quote version does not satisfy evaluation")
    void wrongVersionExceptionFailsEvaluation() {
        PolicyExceptionGrant otherVersion = grant(2L, "tenant-1", NOW.plusSeconds(3600));

        QuotePolicyDecision decision = QuotePolicyEvaluator.evaluateQuote(
                facts("32", "40", 12, List.of()),
                List.of(policy(limits("25", "20", "36", null))),
                List.of(otherVersion),
                NOW);

        assertThat(decision.verdict()).isEqualTo(PolicyVerdict.REQUIRES_APPROVAL);
        assertThat(decision.explainPolicyBreaches()).isNotEmpty();
    }

    @Test
    @DisplayName("an active exception for another tenant does not satisfy evaluation")
    void otherTenantExceptionFailsEvaluation() {
        PolicyExceptionGrant otherTenant = grant(3L, "tenant-2", NOW.plusSeconds(3600));

        QuotePolicyDecision decision = QuotePolicyEvaluator.evaluateQuote(
                facts("32", "40", 12, List.of()),
                List.of(policy(limits("25", "20", "36", null))),
                List.of(otherTenant),
                NOW);

        assertThat(decision.verdict()).isEqualTo(PolicyVerdict.REQUIRES_APPROVAL);
    }

    private static PolicyExceptionGrant grant(long quoteVersion, String tenantId, Instant expiresAt) {
        return new PolicyExceptionGrant("42", quoteVersion, 7L, tenantId, expiresAt);
    }

    private static QuoteVersionFacts facts(String discount, String margin, Integer term, List<String> clauses) {
        return new QuoteVersionFacts(
                "42",
                3L,
                "tenant-1",
                "Enterprise",
                "Software",
                "AMER",
                discount == null ? null : new BigDecimal(discount),
                margin == null ? null : new BigDecimal(margin),
                term,
                clauses,
                AS_OF);
    }

    private static CommercialPolicy policy(List<PolicyThreshold> thresholds) {
        CommercialPolicy policy = CommercialPolicy.builder()
                .name("Enterprise software")
                .status(PolicyStatus.ACTIVE)
                .policyVersion(1)
                .segment("Enterprise")
                .productFamily("Software")
                .region("AMER")
                .effectiveDate(AS_OF.minusDays(1))
                .expirationDate(AS_OF.plusDays(1))
                .build();
        policy.setId(7L);
        for (PolicyThreshold threshold : thresholds) {
            policy.addThreshold(threshold);
        }
        return policy;
    }

    private static List<PolicyThreshold> limits(String discount, String margin, String term, String clause) {
        List<PolicyThreshold> thresholds = new java.util.ArrayList<>();
        thresholds.add(PolicyThreshold.builder()
                .thresholdType(ThresholdType.DISCOUNT_PERCENT)
                .limitValue(new BigDecimal(discount))
                .build());
        thresholds.add(PolicyThreshold.builder()
                .thresholdType(ThresholdType.MARGIN_FLOOR)
                .limitValue(new BigDecimal(margin))
                .build());
        thresholds.add(PolicyThreshold.builder()
                .thresholdType(ThresholdType.TERM_MONTHS)
                .limitValue(new BigDecimal(term))
                .build());
        if (clause != null) {
            thresholds.add(PolicyThreshold.builder()
                    .thresholdType(ThresholdType.NON_STANDARD_CLAUSE)
                    .clauseCode(clause)
                    .build());
        }
        return thresholds;
    }
}
