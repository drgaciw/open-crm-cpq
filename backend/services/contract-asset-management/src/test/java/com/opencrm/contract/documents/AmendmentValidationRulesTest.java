package com.opencrm.contract.documents;

import com.opencrm.contract.domain.documents.ActionCode;
import com.opencrm.contract.domain.documents.BusinessCategory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** V1–V14 and the L4 category derivation. DoD v0.3 items 3 and 4. */
class AmendmentValidationRulesTest {

    @Test
    void categoryFollowsActionAndDeltaSign() {
        assertThat(AmendmentValidationRules.categoryFor(ActionCode.ADD, BigDecimal.ONE))
                .isEqualTo(BusinessCategory.UPSELL);
        assertThat(AmendmentValidationRules.categoryFor(ActionCode.UPDATE, BigDecimal.ONE))
                .isEqualTo(BusinessCategory.UPSELL);
        assertThat(AmendmentValidationRules.categoryFor(ActionCode.UPDATE, new BigDecimal("-1")))
                .isEqualTo(BusinessCategory.DOWNSELL);
        assertThat(AmendmentValidationRules.categoryFor(ActionCode.UPDATE, BigDecimal.ZERO))
                .isEqualTo(BusinessCategory.TERMS_AND_CONDITIONS);
        assertThat(AmendmentValidationRules.categoryFor(ActionCode.REMOVE, BigDecimal.ONE.negate()))
                .isEqualTo(BusinessCategory.CANCELLATION);
    }

    @Test
    void acS3_8_backdatingWithoutReasonFailsV6() {
        SubmitFacts facts = SubmitFacts.passing();
        facts.effectiveDate = facts.actionDate.minusDays(1);
        facts.backdatingReason = null;
        assertThat(ruleIds(AmendmentValidationRules.onSubmit(facts))).contains("V6");
    }

    @Test
    void passingSubmitHasNoFailures() {
        assertThat(AmendmentValidationRules.onSubmit(SubmitFacts.passing())).isEmpty();
    }

    @Test
    void submitNamesEachFailingRuleAndLeavesTheFactsUnchanged() {
        SubmitFacts facts = SubmitFacts.passing();
        facts.intentName = "RENEW";
        facts.category = BusinessCategory.CANCELLATION;
        facts.subscriptionStatus = "SUSPENDED";
        facts.observedRevision = 9L;
        facts.action = ActionCode.UPDATE;
        facts.sourcePresent = false;
        facts.lineStart = facts.contractStart.minusDays(1);
        facts.endQty = new BigDecimal("99");
        facts.policyPresent = false;
        facts.pinsPresent = false;
        facts.overrideRequested = true;
        facts.overrideComplete = false;
        facts.removeAfterAdd = false;
        facts.hasRemove = true;
        facts.unitKeysUnique = false;

        assertThat(ruleIds(AmendmentValidationRules.onSubmit(facts)))
                .contains("V1", "V2", "V3", "V4", "V5", "V7", "V8", "V9", "V12", "V13", "V14");
        assertThat(facts.intentName).isEqualTo("RENEW");
    }

    @Test
    void computedZeroTotalsAreDistinctFromUnknownNull() {
        assertThat(AmendmentValidationRules.computedTotals(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)).isEmpty();
        assertThat(ruleIds(AmendmentValidationRules.computedTotals(
                null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO))).contains("V8");
    }

    @Test
    void executeChecksDigestEqualityAndTheSingleExecutedRevision() {
        ExecuteFacts ok = ExecuteFacts.passing();
        assertThat(AmendmentValidationRules.onExecute(ok)).isEmpty();

        ExecuteFacts mismatch = ExecuteFacts.passing();
        mismatch.digestMatches = false;
        mismatch.executedRevisionAlreadyExists = true;
        assertThat(ruleIds(AmendmentValidationRules.onExecute(mismatch))).contains("V10", "V11");
    }

    private static List<String> ruleIds(List<RuleFailure> failures) {
        return failures.stream().map(RuleFailure::ruleId).toList();
    }
}
