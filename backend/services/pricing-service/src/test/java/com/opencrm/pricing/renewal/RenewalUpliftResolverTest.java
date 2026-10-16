package com.opencrm.pricing.renewal;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class RenewalUpliftResolverTest {
    private static final LocalDate ACTION = LocalDate.of(2026, 8, 2);
    private static final LocalDate EFFECTIVE = LocalDate.of(2026, 11, 1);
    private static final LocalDate LINE_START = LocalDate.of(2026, 12, 15);

    private final RenewalUpliftResolver resolver = new RenewalUpliftResolver();

    @Test
    void accountSevenPercentOnContractedNetMatchesTheWorkedExample() {
        RenewalUpliftResult result = resolver.resolve(request("40.0000", "200", 12, scenarioPins(),
                account("acct-uplift-7", RenewalUpliftMode.UPLIFT_PCT, "7")));

        assertThat(result.resolution()).isEqualTo(RenewalUpliftResolution.PRICED);
        assertUnit(result.unitPrice(), "42.8000");
        assertThat(result.extendedAmount()).isEqualByComparingTo("102720.00");
        assertThat(result.extendedAmount().scale()).isEqualTo(2);
        assertThat(result.flatPeriodCount()).isEqualTo(1);
        assertThat(result.basis().value()).isEqualTo(RenewalUpliftBasis.CONTRACTED_NET);
        assertThat(result.basis().fromDefault()).isTrue();
        assertThat(result.basisAmount()).isEqualByComparingTo("40.0000");
        assertThat(result.priceAsOf().value()).isEqualTo(RenewalPriceAsOf.EFFECTIVE_DATE);
        assertThat(result.priceAsOf().fromDefault()).isTrue();
        assertThat(result.priceAsOfDate()).isEqualTo(EFFECTIVE);
        assertThat(result.priceBookVersionId()).isEqualTo("pb-52");
        assertThat(result.listUnitAtPriceAsOf()).isEqualByComparingTo("52.0000");
        assertThat(result.notBelowExitNet().value()).isTrue();
        assertThat(result.notBelowExitNet().fromDefault()).isTrue();
        assertThat(result.notAboveList().value()).isFalse();
        assertThat(result.capApplied()).isNull();
        assertThat(result.floorApplied()).isNull();
        assertThat(result.appliedRuleOrPolicyId()).isEqualTo("acct-uplift-7");
        assertThat(result.clauseId()).isNull();
        assertThat(result.rulesSuppliedPercent()).isFalse();
        assertThat(result.explanationCode()).isNull();
        assertThat(result.currency()).isEqualTo("USD");
    }

    @Test
    void thirtySixMonthTermStaysOneFlatPeriod() {
        RenewalUpliftResult result = resolver.resolve(request("40.0000", "200", 36, scenarioPins(),
                account("acct-uplift-7", RenewalUpliftMode.UPLIFT_PCT, "7")));

        assertUnit(result.unitPrice(), "42.8000");
        assertThat(result.extendedAmount()).isEqualByComparingTo("308160.00");
        assertThat(result.flatPeriodCount()).isEqualTo(1);
    }

    @Test
    void rulesFractionLeftAsPercentPointsIsNotTheChurnRate() {
        RenewalUpliftResult result = resolver.resolve(request("40.0000", "1", 12, scenarioPins(),
                account("acct-mode", RenewalUpliftMode.UPLIFT_PCT, null),
                rules("rule-007", "0.03", null)));

        assertUnit(result.unitPrice(), "40.0120");
        assertThat(result.rulesSuppliedPercent()).isTrue();
    }

    @Test
    void negativeExitNetIsAnUnresolvedBasis() {
        RenewalUpliftResult result = resolver.resolve(request("-1", "1", 12, scenarioPins(),
                account("acct", RenewalUpliftMode.UPLIFT_PCT, "7")));

        assertThat(result.resolution()).isEqualTo(RenewalUpliftResolution.UNRESOLVED_BASIS);
        assertThat(result.unitPrice()).isNull();
        assertThat(result.basisAmount()).isNull();
    }

    @Test
    void explicitZeroPercentIsAPricedZeroUplift() {
        RenewalUpliftResult result = resolver.resolve(request("40", "1", 12, scenarioPins(),
                account("acct-zero", RenewalUpliftMode.UPLIFT_PCT, "0")));

        assertThat(result.resolution()).isEqualTo(RenewalUpliftResolution.PRICED);
        assertUnit(result.unitPrice(), "40.0000");
    }

    @Test
    void halfUpRoundsTheCandidateAtScaleFour() {
        RenewalUpliftResult result = resolver.resolve(request("10", "1", 1, scenarioPins(),
                account("acct", RenewalUpliftMode.UPLIFT_PCT, "0.0055")));

        assertUnit(result.unitPrice(), "10.0006");
    }

    @Test
    void monthlyAmountRoundsHalfUpBeforeTermMonths() {
        RenewalUpliftResult result = resolver.resolve(request("10", "1", 3, scenarioPins(),
                account("acct", RenewalUpliftMode.UPLIFT_PCT, "3.35")));

        assertUnit(result.unitPrice(), "10.3350");
        assertThat(result.extendedAmount()).isEqualByComparingTo("31.02");
        assertThat(result.extendedAmount().scale()).isEqualTo(2);
    }

    @Test
    void unresolvedRenewalModeWhenNoRungSetsAMode() {
        RenewalUpliftResult result = resolver.resolve(request("40.0000", "200", 12, scenarioPins(),
                clause("clause-5", null, null, "5", null, null, null, null),
                rules("rule-007", "3", "ENABLE_12M_PRICE_PROTECTION")));

        assertThat(result.resolution()).isEqualTo(RenewalUpliftResolution.UNRESOLVED_RENEWAL_MODE);
        assertThat(result.unitPrice()).isNull();
        assertThat(result.extendedAmount()).isNull();
        assertThat(result.flatPeriodCount()).isNull();
        assertThat(result.mode()).isNull();
        assertThat(result.basis().value()).isEqualTo(RenewalUpliftBasis.CONTRACTED_NET);
        assertThat(result.basis().fromDefault()).isTrue();
        assertThat(result.priceAsOf().value()).isEqualTo(RenewalPriceAsOf.EFFECTIVE_DATE);
        assertThat(result.priceAsOfDate()).isEqualTo(EFFECTIVE);
        assertThat(result.maxIncreasePct().value()).isEqualByComparingTo("5");
        assertThat(result.maxIncreasePct().rung()).isEqualTo(RenewalUpliftRungKind.CLAUSE);
        assertThat(result.notBelowExitNet().value()).isTrue();
        assertThat(result.notBelowExitNet().fromDefault()).isTrue();
        assertThat(result.clauseId()).isEqualTo("clause-5");
        assertThat(result.appliedRuleOrPolicyId()).isNull();
        assertThat(result.percentPoints().sourceId()).isEqualTo("rule-007");
        assertThat(result.rulesSuppliedPercent()).isFalse();
        assertThat(result.retentionMarker()).isEqualTo("ENABLE_12M_PRICE_PROTECTION");
    }

    @Test
    void rulesRungCannotSupplyModeBasisCapFloorOrPriceAsOf() {
        RenewalUpliftResult result = resolver.resolve(request("40.0000", "1", 12, scenarioPins(),
                new RenewalUpliftRung(RenewalUpliftRungKind.RULES, "rule-007", RenewalUpliftMode.UPLIFT_PCT,
                        RenewalUpliftBasis.CURRENT_LIST, new BigDecimal("3"), new BigDecimal("1"), Boolean.TRUE,
                        Boolean.FALSE, new BigDecimal("50"), new BigDecimal("99"), RenewalPriceAsOf.ACTION_DATE,
                        "ENABLE_12M_PRICE_PROTECTION")));

        assertThat(result.resolution()).isEqualTo(RenewalUpliftResolution.UNRESOLVED_RENEWAL_MODE);
        assertThat(result.unitPrice()).isNull();
        assertThat(result.basis().value()).isEqualTo(RenewalUpliftBasis.CONTRACTED_NET);
        assertThat(result.priceAsOf().value()).isEqualTo(RenewalPriceAsOf.EFFECTIVE_DATE);
        assertThat(result.priceAsOfDate()).isEqualTo(EFFECTIVE);
        assertThat(result.maxIncreasePct()).isNull();
        assertThat(result.notAboveList().value()).isFalse();
        assertThat(result.notAboveList().fromDefault()).isTrue();
        assertThat(result.notBelowExitNet().value()).isTrue();
        assertThat(result.notBelowExitNet().fromDefault()).isTrue();
        assertThat(result.minIncreasePct()).isNull();
        assertThat(result.minUnitAmount()).isNull();
    }

    @Test
    void unresolvedUpliftWhenPercentIsAbsent() {
        RenewalUpliftResult result = resolver.resolve(request("40.0000", "200", 12, scenarioPins(),
                account("acct-uplift-7", RenewalUpliftMode.UPLIFT_PCT, null)));

        assertThat(result.resolution()).isEqualTo(RenewalUpliftResolution.UNRESOLVED_UPLIFT);
        assertThat(result.unitPrice()).isNull();
        assertThat(result.percentPoints()).isNull();
        assertThat(result.appliedRuleOrPolicyId()).isEqualTo("acct-uplift-7");
        assertThat(result.basis().value()).isEqualTo(RenewalUpliftBasis.CONTRACTED_NET);
        assertThat(result.priceAsOf().value()).isEqualTo(RenewalPriceAsOf.EFFECTIVE_DATE);
        assertThat(result.notBelowExitNet().value()).isTrue();
        assertThat(result.rulesSuppliedPercent()).isFalse();
    }

    @Test
    void unresolvedBasisForCurrentListEvenWhenThePinIsPresent() {
        RenewalUpliftResult result = resolver.resolve(request("40.0000", "200", 12, scenarioPins(),
                account("acct-uplift-7", RenewalUpliftMode.UPLIFT_PCT, "7", RenewalUpliftBasis.CURRENT_LIST)));

        assertThat(result.resolution()).isEqualTo(RenewalUpliftResolution.UNRESOLVED_BASIS);
        assertThat(result.unitPrice()).isNull();
        assertThat(result.basis().value()).isEqualTo(RenewalUpliftBasis.CURRENT_LIST);
        assertThat(result.basis().rung()).isEqualTo(RenewalUpliftRungKind.ACCOUNT);
        assertThat(result.basisAmount()).isNull();
        assertThat(result.listUnitAtPriceAsOf()).isEqualByComparingTo("52.0000");
        assertThat(result.appliedRuleOrPolicyId()).isEqualTo("acct-uplift-7");
    }

    @Test
    void unresolvedBasisForListLessContractedDiscount() {
        RenewalUpliftResult result = resolver.resolve(request("40.0000", "200", 12, scenarioPins(),
                account("acct-uplift-7", RenewalUpliftMode.UPLIFT_PCT, "7",
                        RenewalUpliftBasis.LIST_LESS_CONTRACTED_DISCOUNT)));

        assertThat(result.resolution()).isEqualTo(RenewalUpliftResolution.UNRESOLVED_BASIS);
        assertThat(result.unitPrice()).isNull();
        assertThat(result.basis().value()).isEqualTo(RenewalUpliftBasis.LIST_LESS_CONTRACTED_DISCOUNT);
    }

    @Test
    void unresolvedBasisWhenExitNetIsMissing() {
        RenewalUpliftResult result = resolver.resolve(request(null, "200", 12, scenarioPins(),
                account("acct-uplift-7", RenewalUpliftMode.UPLIFT_PCT, "7")));

        assertThat(result.resolution()).isEqualTo(RenewalUpliftResolution.UNRESOLVED_BASIS);
        assertThat(result.unitPrice()).isNull();
        assertThat(result.basisAmount()).isNull();
        assertThat(result.appliedRuleOrPolicyId()).isEqualTo("acct-uplift-7");
        assertThat(result.priceAsOf().value()).isEqualTo(RenewalPriceAsOf.EFFECTIVE_DATE);
    }

    @Test
    void unresolvedBasisWhenNotAboveListPinHasNoList() {
        RenewalPriceAsOfPins pins = new RenewalPriceAsOfPins(
                ACTION, "pb-aug", null, EFFECTIVE, "pb-52", new BigDecimal("52.0000"),
                EFFECTIVE, "pb-52", new BigDecimal("52.0000"));
        RenewalUpliftResult result = resolver.resolve(request("40.0000", "1", 12, pins,
                account("acct", RenewalUpliftMode.UPLIFT_PCT, "7"),
                new RenewalUpliftRung(RenewalUpliftRungKind.LINE, "line-1", null, null, null, null,
                        Boolean.TRUE, null, null, null, RenewalPriceAsOf.ACTION_DATE, null)));

        assertThat(result.resolution()).isEqualTo(RenewalUpliftResolution.UNRESOLVED_BASIS);
        assertThat(result.unitPrice()).isNull();
        assertThat(result.priceAsOf().value()).isEqualTo(RenewalPriceAsOf.ACTION_DATE);
        assertThat(result.priceAsOfDate()).isEqualTo(ACTION);
        assertThat(result.listUnitAtPriceAsOf()).isNull();
        assertThat(result.notAboveList().value()).isTrue();
        assertThat(result.capBound()).isNull();
    }

    @Test
    void unresolvedBasisWhenNotAboveListPinHasNoPriceBookVersion() {
        RenewalPriceAsOfPins pins = new RenewalPriceAsOfPins(
                ACTION, "  ", new BigDecimal("50"), EFFECTIVE, "pb-52", new BigDecimal("52"),
                EFFECTIVE, "pb-52", new BigDecimal("52"));
        RenewalUpliftResult result = resolver.resolve(request("40.0000", "1", 12, pins,
                account("acct", RenewalUpliftMode.UPLIFT_PCT, "7"),
                priceAsOf(RenewalUpliftRungKind.CLAUSE, "clause-list", RenewalPriceAsOf.ACTION_DATE, Boolean.TRUE)));

        assertThat(result.resolution()).isEqualTo(RenewalUpliftResolution.UNRESOLVED_BASIS);
        assertThat(result.unitPrice()).isNull();
        assertThat(result.priceBookVersionId()).isNull();
        assertThat(result.listUnitAtPriceAsOf()).isEqualByComparingTo("50");
        assertThat(result.clauseId()).isEqualTo("clause-list");
    }

    @Test
    void clauseCapLeavesTheAccountPercentThenClampsIt() {
        RenewalUpliftResult result = resolver.resolve(request("40.0000", "200", 12, scenarioPins(),
                account("acct-uplift-7", RenewalUpliftMode.UPLIFT_PCT, "7"),
                clause("clause-5", null, null, "5", null, null, null, null)));

        assertThat(result.resolution()).isEqualTo(RenewalUpliftResolution.PRICED);
        assertUnit(result.unitPrice(), "42.0000");
        assertThat(result.extendedAmount()).isEqualByComparingTo("100800.00");
        assertThat(result.capApplied().exact()).isEqualByComparingTo("42");
        assertThat(result.capApplied().normalized()).isEqualByComparingTo("42.0000");
        assertThat(result.capApplied().normalized().scale()).isEqualTo(4);
        assertThat(result.floorApplied()).isNull();
        assertThat(result.appliedRuleOrPolicyId()).isEqualTo("acct-uplift-7");
        assertThat(result.clauseId()).isEqualTo("clause-5");
        assertThat(result.maxIncreasePct().rung()).isEqualTo(RenewalUpliftRungKind.CLAUSE);
    }

    @Test
    void zeroMaxIncreaseCannotExceedTheExitNet() {
        RenewalUpliftResult result = resolver.resolve(request("40.0000", "1", 12, scenarioPins(),
                account("acct", RenewalUpliftMode.UPLIFT_PCT, "7"),
                clause("clause-0", null, null, "0", null, null, null, null)));

        assertUnit(result.unitPrice(), "40.0000");
        assertThat(result.resolution()).isEqualTo(RenewalUpliftResolution.PRICED);
        assertThat(result.capApplied().normalized()).isEqualByComparingTo("40.0000");
        assertThat(result.floorApplied()).isNull();
    }

    @Test
    void notAboveListAndMaxIncreaseUseTheLowerNormalizedCap() {
        RenewalUpliftResult result = resolver.resolve(request("40.0000", "1", 12, scenarioPins(),
                account("acct", RenewalUpliftMode.UPLIFT_PCT, "7"),
                clause("clause-list", null, null, "5", Boolean.TRUE, null, null, null)));

        assertUnit(result.unitPrice(), "42.0000");
        assertThat(result.capBound().exact()).isEqualByComparingTo("42");
        assertThat(result.listUnitAtPriceAsOf()).isEqualByComparingTo("52.0000");
    }

    @Test
    void flooredCapKeepsARoundedCandidateInsideTheCeiling() {
        RenewalUpliftResult result = resolver.resolve(request("40.0000", "1", 1, scenarioPins(),
                account("acct", RenewalUpliftMode.UPLIFT_PCT, "3.3334"),
                clause("clause-cap", null, null, "3.3333", null, null, null, null)));

        assertThat(result.capBound().exact()).isEqualByComparingTo("41.33332");
        assertThat(result.capBound().normalized()).isEqualByComparingTo("41.3333");
        assertThat(result.capBound().normalized().scale()).isEqualTo(4);
        assertUnit(result.unitPrice(), "41.3333");
        assertThat(result.capApplied().normalized()).isEqualByComparingTo("41.3333");
    }

    @Test
    void minIncreaseFloorRisesToTheCeiledBound() {
        RenewalUpliftResult result = resolver.resolve(request("40.0000", "1", 1, scenarioPins(),
                account("acct", RenewalUpliftMode.UPLIFT_PCT, "3"),
                clause("clause-floor", null, null, null, null, null, "3.3333", null)));

        assertThat(result.floorBound().exact()).isEqualByComparingTo("41.33332");
        assertThat(result.floorBound().normalized()).isEqualByComparingTo("41.3334");
        assertThat(result.floorBound().normalized().scale()).isEqualTo(4);
        assertUnit(result.unitPrice(), "41.3334");
        assertThat(result.floorApplied().normalized()).isEqualByComparingTo("41.3334");
        assertThat(result.capApplied()).isNull();
    }

    @Test
    void higherFloorWinsAndAHigherMinUnitIsRecordedWhenItBinds() {
        RenewalUpliftResult result = resolver.resolve(request("40.0000", "1", 1, scenarioPins(),
                account("acct", RenewalUpliftMode.UPLIFT_PCT, "7"),
                clause("clause-floor", null, null, null, null, null, "10", "45")));

        assertUnit(result.unitPrice(), "45.0000");
        assertThat(result.floorApplied().exact()).isEqualByComparingTo("45");
        assertThat(result.floorBound().normalized()).isEqualByComparingTo("45.0000");
    }

    @Test
    void defaultFloorRaisesANegativePercentToTheExitNet() {
        RenewalUpliftResult result = resolver.resolve(request("40.0000", "1", 12, scenarioPins(),
                account("acct", RenewalUpliftMode.UPLIFT_PCT, "-10")));

        assertUnit(result.unitPrice(), "40.0000");
        assertThat(result.floorApplied().normalized()).isEqualByComparingTo("40.0000");
        assertThat(result.notBelowExitNet().fromDefault()).isTrue();
    }

    @Test
    void clauseFloorBeatsAnAccountOptOut() {
        RenewalUpliftResult result = resolver.resolve(request("40.0000", "1", 12, scenarioPins(),
                new RenewalUpliftRung(RenewalUpliftRungKind.ACCOUNT, "acct", RenewalUpliftMode.UPLIFT_PCT, null,
                        new BigDecimal("-10"), null, null, Boolean.FALSE, null, null, null, null),
                clause("clause-protect", null, null, null, null, Boolean.TRUE, null, null)));

        assertUnit(result.unitPrice(), "40.0000");
        assertThat(result.notBelowExitNet().rung()).isEqualTo(RenewalUpliftRungKind.CLAUSE);
        assertThat(result.notBelowExitNet().value()).isTrue();
        assertThat(result.floorApplied()).isNotNull();
    }

    @Test
    void accountOptOutLetsTheUnitFallBelowTheExitNet() {
        RenewalUpliftResult result = resolver.resolve(request("40.0000", "1", 12, scenarioPins(),
                new RenewalUpliftRung(RenewalUpliftRungKind.ACCOUNT, "acct", RenewalUpliftMode.UPLIFT_PCT, null,
                        new BigDecimal("-10"), null, null, Boolean.FALSE, null, null, null, null)));

        assertUnit(result.unitPrice(), "36.0000");
        assertThat(result.floorApplied()).isNull();
        assertThat(result.floorBound()).isNull();
        assertThat(result.notBelowExitNet().value()).isFalse();
        assertThat(result.notBelowExitNet().rung()).isEqualTo(RenewalUpliftRungKind.ACCOUNT);
    }

    @Test
    void contradictoryScaleFourBoundsAreAProtectionConflict() {
        RenewalUpliftResult result = resolver.resolve(request("40.0000", "200", 12, scenarioPins(),
                account("acct", RenewalUpliftMode.UPLIFT_PCT, "7"),
                clause("clause-gap", null, null, "3.3333", null, null, "3.3333", null)));

        assertThat(result.resolution()).isEqualTo(RenewalUpliftResolution.PRICE_PROTECTION_CONFLICT);
        assertThat(result.unitPrice()).isNull();
        assertThat(result.extendedAmount()).isNull();
        assertThat(result.capBound().exact()).isEqualByComparingTo("41.33332");
        assertThat(result.capBound().normalized()).isEqualByComparingTo("41.3333");
        assertThat(result.floorBound().exact()).isEqualByComparingTo("41.33332");
        assertThat(result.floorBound().normalized()).isEqualByComparingTo("41.3334");
        assertThat(result.capApplied()).isNull();
        assertThat(result.floorApplied()).isNull();
        assertThat(result.clauseId()).isEqualTo("clause-gap");
        assertThat(result.appliedRuleOrPolicyId()).isEqualTo("acct");
        assertThat(result.basis().value()).isEqualTo(RenewalUpliftBasis.CONTRACTED_NET);
        assertThat(result.priceAsOf().value()).isEqualTo(RenewalPriceAsOf.EFFECTIVE_DATE);
    }

    @Test
    void contractedNetIgnoresWhichSideOfTheBookChangeIsPinned() {
        RenewalUpliftResult onEffective = resolver.resolve(request("40.0000", "200", 12, scenarioPins(),
                account("acct-uplift-7", RenewalUpliftMode.UPLIFT_PCT, "7")));
        RenewalUpliftResult onAction = resolver.resolve(request("40.0000", "200", 12, scenarioPins(),
                account("acct-uplift-7", RenewalUpliftMode.UPLIFT_PCT, "7"),
                priceAsOf(RenewalUpliftRungKind.LINE, "line-1", RenewalPriceAsOf.ACTION_DATE, null)));

        assertUnit(onEffective.unitPrice(), "42.8000");
        assertUnit(onAction.unitPrice(), "42.8000");
        assertThat(onEffective.priceAsOfDate()).isEqualTo(EFFECTIVE);
        assertThat(onEffective.priceBookVersionId()).isEqualTo("pb-52");
        assertThat(onEffective.listUnitAtPriceAsOf()).isEqualByComparingTo("52");
        assertThat(onAction.priceAsOfDate()).isEqualTo(ACTION);
        assertThat(onAction.priceBookVersionId()).isEqualTo("pb-50");
        assertThat(onAction.listUnitAtPriceAsOf()).isEqualByComparingTo("50");
    }

    @Test
    void notAboveListCapsWithTheListOnTheSelectedSideOfTheBookChange() {
        RenewalPriceAsOfPins pins = new RenewalPriceAsOfPins(
                ACTION, "pb-aug", new BigDecimal("41.0000"),
                EFFECTIVE, "pb-nov", new BigDecimal("52.0000"),
                EFFECTIVE, "pb-nov", new BigDecimal("52.0000"));
        RenewalUpliftRung mode = new RenewalUpliftRung(RenewalUpliftRungKind.ACCOUNT, "acct", RenewalUpliftMode.UPLIFT_PCT,
                null, new BigDecimal("7"), null, null, null, null, null, RenewalPriceAsOf.EFFECTIVE_DATE, null);
        RenewalUpliftResult effective = resolver.resolve(request("40.0000", "1", 12, pins, mode,
                priceAsOf(RenewalUpliftRungKind.CLAUSE, "clause-list", null, Boolean.TRUE)));
        RenewalUpliftResult action = resolver.resolve(request("40.0000", "1", 12, pins, mode,
                priceAsOf(RenewalUpliftRungKind.LINE, "line-1", RenewalPriceAsOf.ACTION_DATE, Boolean.TRUE)));

        assertUnit(effective.unitPrice(), "42.8000");
        assertThat(effective.capApplied()).isNull();
        assertThat(effective.priceAsOfDate()).isEqualTo(EFFECTIVE);
        assertThat(effective.priceBookVersionId()).isEqualTo("pb-nov");
        assertUnit(action.unitPrice(), "41.0000");
        assertThat(action.capApplied().normalized()).isEqualByComparingTo("41.0000");
        assertThat(action.priceAsOf().rung()).isEqualTo(RenewalUpliftRungKind.LINE);
        assertThat(action.priceAsOfDate()).isEqualTo(ACTION);
        assertThat(action.priceBookVersionId()).isEqualTo("pb-aug");
    }

    @Test
    void lineStartPinIsDistinctFromTheEffectiveDate() {
        RenewalUpliftResult result = resolver.resolve(request("40.0000", "1", 12, scenarioPins(),
                account("acct", RenewalUpliftMode.UPLIFT_PCT, "7"),
                priceAsOf(RenewalUpliftRungKind.LINE, "line-1", RenewalPriceAsOf.LINE_START, Boolean.TRUE)));

        assertUnit(result.unitPrice(), "42.8000");
        assertThat(result.priceAsOfDate()).isEqualTo(LINE_START);
        assertThat(result.priceBookVersionId()).isEqualTo("pb-dec");
        assertThat(result.listUnitAtPriceAsOf()).isEqualByComparingTo("60.0000");
        assertThat(result.capApplied()).isNull();
    }

    @Test
    void clausePercentBeatsALinePercent() {
        RenewalUpliftResult result = resolver.resolve(request("40.0000", "1", 12, scenarioPins(),
                new RenewalUpliftRung(RenewalUpliftRungKind.CLAUSE, "clause-5", null, null, new BigDecimal("5"),
                        null, null, null, null, null, null, null),
                new RenewalUpliftRung(RenewalUpliftRungKind.LINE, "line-7", null, null, new BigDecimal("7"),
                        null, null, null, null, null, null, null),
                new RenewalUpliftRung(RenewalUpliftRungKind.AGREEMENT, "agr-4", null, null, new BigDecimal("4"),
                        null, null, null, null, null, null, null),
                account("acct-mode", RenewalUpliftMode.UPLIFT_PCT, null)));

        assertUnit(result.unitPrice(), "42.0000");
        assertThat(result.percentPoints().rung()).isEqualTo(RenewalUpliftRungKind.CLAUSE);
        assertThat(result.appliedRuleOrPolicyId()).isEqualTo("clause-5");
        assertThat(result.clauseId()).isEqualTo("clause-5");
    }

    @Test
    void agreementPercentBeatsAccountWhenTheLineLeavesItUnset() {
        RenewalUpliftResult result = resolver.resolve(request("40.0000", "1", 12, scenarioPins(),
                new RenewalUpliftRung(RenewalUpliftRungKind.AGREEMENT, "agr-6", null, null, new BigDecimal("6"),
                        null, null, null, null, null, null, null),
                account("acct-mode", RenewalUpliftMode.UPLIFT_PCT, "7")));

        assertUnit(result.unitPrice(), "42.4000");
        assertThat(result.percentPoints().rung()).isEqualTo(RenewalUpliftRungKind.AGREEMENT);
        assertThat(result.appliedRuleOrPolicyId()).isEqualTo("agr-6");
    }

    @Test
    void linePercentBeatsAccountAndRules() {
        RenewalUpliftResult result = resolver.resolve(request("40.0000", "200", 12, scenarioPins(),
                new RenewalUpliftRung(RenewalUpliftRungKind.LINE, "line-3", null, null, new BigDecimal("3"),
                        null, null, null, null, null, null, null),
                account("acct-7", RenewalUpliftMode.UPLIFT_PCT, "7"),
                rules("rule-007", "8", null)));

        assertUnit(result.unitPrice(), "41.2000");
        assertThat(result.extendedAmount()).isEqualByComparingTo("98880.00");
        assertThat(result.percentPoints().rung()).isEqualTo(RenewalUpliftRungKind.LINE);
        assertThat(result.appliedRuleOrPolicyId()).isEqualTo("line-3");
        assertThat(result.mode().rung()).isEqualTo(RenewalUpliftRungKind.ACCOUNT);
        assertThat(result.rulesSuppliedPercent()).isFalse();
        assertThat(result.explanationCode()).isNull();
    }

    @Test
    void rulesPercentAppliesWhenHigherRungsLeaveItUnset() {
        RenewalUpliftResult result = resolver.resolve(request("40.0000", "200", 12, scenarioPins(),
                account("acct-mode", RenewalUpliftMode.UPLIFT_PCT, null),
                rules("rule-007", "3", "ENABLE_12M_PRICE_PROTECTION")));

        assertUnit(result.unitPrice(), "41.2000");
        assertThat(result.extendedAmount()).isEqualByComparingTo("98880.00");
        assertThat(result.percentPoints().rung()).isEqualTo(RenewalUpliftRungKind.RULES);
        assertThat(result.appliedRuleOrPolicyId()).isEqualTo("rule-007");
        assertThat(result.rulesSuppliedPercent()).isTrue();
        assertThat(result.explanationCode()).isEqualTo("RENEWAL_UPLIFT_POLICY");
        assertThat(RenewalUpliftResolver.RULES_EXPLANATION_CODE).isEqualTo("RENEWAL_UPLIFT_POLICY");
        assertThat(result.retentionMarker()).isEqualTo("ENABLE_12M_PRICE_PROTECTION");
        assertThat(result.capApplied()).isNull();
    }

    @Test
    void accountPercentBeatsTheRulesPercentAndKeepsTheRetentionMarker() {
        RenewalUpliftResult result = resolver.resolve(request("40.0000", "1", 12, scenarioPins(),
                account("acct-uplift-7", RenewalUpliftMode.UPLIFT_PCT, "7"),
                rules("rule-007", "3", "ENABLE_12M_PRICE_PROTECTION")));

        assertUnit(result.unitPrice(), "42.8000");
        assertThat(result.appliedRuleOrPolicyId()).isEqualTo("acct-uplift-7");
        assertThat(result.rulesSuppliedPercent()).isFalse();
        assertThat(result.explanationCode()).isNull();
        assertThat(result.retentionMarker()).isEqualTo("ENABLE_12M_PRICE_PROTECTION");
    }

    @ParameterizedTest
    @EnumSource(value = RenewalUpliftMode.class, mode = EnumSource.Mode.EXCLUDE, names = "UPLIFT_PCT")
    void otherModesAreOutsideThisSlice(RenewalUpliftMode mode) {
        assertThatThrownBy(() -> resolver.resolve(request("40.0000", "1", 12, scenarioPins(),
                account("acct", mode, "7"))))
                .isInstanceOf(RenewalUpliftModeOutOfSliceException.class)
                .extracting(ex -> ((RenewalUpliftModeOutOfSliceException) ex).mode())
                .isEqualTo(mode);
    }

    @Test
    void duplicateRungIsRejected() {
        RenewalUpliftRung first = account("acct-a", RenewalUpliftMode.UPLIFT_PCT, "7");
        RenewalUpliftRung second = account("acct-b", RenewalUpliftMode.UPLIFT_PCT, "8");
        assertThatThrownBy(() -> resolver.resolve(request("40", "1", 12, scenarioPins(), first, second)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ACCOUNT");
    }

    @Test
    void negativeQuantityIsRejected() {
        assertThatThrownBy(() -> resolver.resolve(request("40", "-1", 12, scenarioPins(),
                account("acct", RenewalUpliftMode.UPLIFT_PCT, "7"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sameInputsResolveTwiceWithoutInstanceState() {
        RenewalUpliftRequest request = request("40.0000", "200", 12, scenarioPins(),
                account("acct-uplift-7", RenewalUpliftMode.UPLIFT_PCT, "7"));
        assertThat(resolver.resolve(request)).isEqualTo(resolver.resolve(request));
        assertThat(RenewalUpliftResolver.class.getDeclaredFields())
                .allMatch(field -> Modifier.isStatic(field.getModifiers()));
        assertThat(RenewalUpliftResolver.class.getAnnotations()).isEmpty();
    }

    @Test
    void selectedDateAndVersionAreRequiredEvenWithoutAListCap() {
        RenewalPriceAsOfPins[] incomplete = {
                null,
                new RenewalPriceAsOfPins(ACTION, "a", null, null, "e", null, LINE_START, "l", null),
                new RenewalPriceAsOfPins(ACTION, "a", null, EFFECTIVE, null, null, LINE_START, "l", null),
                new RenewalPriceAsOfPins(ACTION, "a", null, EFFECTIVE, " ", null, LINE_START, "l", null)
        };
        for (RenewalPriceAsOfPins pins : incomplete) {
            RenewalUpliftResult result = resolver.resolve(request("40", "1", 12, pins,
                    account("acct", RenewalUpliftMode.UPLIFT_PCT, "7")));
            assertThat(result.resolution()).isEqualTo(RenewalUpliftResolution.UNRESOLVED_BASIS);
            assertThat(result.unitPrice()).isNull();
            assertThat(result.extendedAmount()).isNull();
        }
        RenewalPriceAsOfPins noList = new RenewalPriceAsOfPins(
                ACTION, "a", null, EFFECTIVE, "e", null, LINE_START, "l", null);
        assertUnit(resolver.resolve(request("40", "1", 12, noList,
                account("acct", RenewalUpliftMode.UPLIFT_PCT, "7"))).unitPrice(), "42.8000");
    }

    @Test
    void equalNormalizedCapsRecordTheLowerExactConstraint() {
        RenewalPriceAsOfPins pins = new RenewalPriceAsOfPins(
                ACTION, "a", new BigDecimal("52"), EFFECTIVE, "e", new BigDecimal("41.33331"),
                LINE_START, "l", new BigDecimal("52"));
        RenewalUpliftResult result = resolver.resolve(request("40", "1", 12, pins,
                account("acct", RenewalUpliftMode.UPLIFT_PCT, "7"),
                clause("clause", null, null, "3.333475", Boolean.TRUE, null, null, null)));
        assertUnit(result.unitPrice(), "41.3333");
        assertThat(result.capBound().exact()).isEqualByComparingTo("41.33331");
        assertThat(result.capApplied().exact()).isEqualByComparingTo("41.33331");
    }

    @Test
    void equalNormalizedFloorsRecordTheHigherExactConstraint() {
        RenewalUpliftResult result = resolver.resolve(request("40", "1", 12, scenarioPins(),
                account("acct", RenewalUpliftMode.UPLIFT_PCT, "3"),
                clause("clause", null, null, null, null, Boolean.FALSE, "3.333275", "41.33339")));
        assertUnit(result.unitPrice(), "41.3334");
        assertThat(result.floorBound().exact()).isEqualByComparingTo("41.33339");
        assertThat(result.floorApplied().exact()).isEqualByComparingTo("41.33339");
    }

    private static void assertUnit(BigDecimal actual, String expected) {
        assertThat(actual).isEqualByComparingTo(expected);
        assertThat(actual.scale()).isEqualTo(4);
    }

    private static RenewalUpliftRequest request(String exitNet, String quantity, Integer termMonths,
                                                 RenewalPriceAsOfPins pins, RenewalUpliftRung... rungs) {
        return new RenewalUpliftRequest("USD", decimal(exitNet), decimal(quantity), termMonths, pins, List.of(rungs));
    }

    private static RenewalPriceAsOfPins scenarioPins() {
        return new RenewalPriceAsOfPins(
                ACTION, "pb-50", new BigDecimal("50.0000"),
                EFFECTIVE, "pb-52", new BigDecimal("52.0000"),
                LINE_START, "pb-dec", new BigDecimal("60.0000"));
    }

    private static RenewalUpliftRung account(String id, RenewalUpliftMode mode, String percent) {
        return account(id, mode, percent, null);
    }

    private static RenewalUpliftRung account(String id, RenewalUpliftMode mode, String percent, RenewalUpliftBasis basis) {
        return new RenewalUpliftRung(RenewalUpliftRungKind.ACCOUNT, id, mode, basis, decimal(percent),
                null, null, null, null, null, null, null);
    }

    private static RenewalUpliftRung clause(String id, RenewalUpliftMode mode, RenewalUpliftBasis basis, String maxIncrease,
                                            Boolean notAboveList, Boolean notBelow, String minIncrease, String minUnit) {
        return new RenewalUpliftRung(RenewalUpliftRungKind.CLAUSE, id, mode, basis, null, decimal(maxIncrease),
                notAboveList, notBelow, decimal(minIncrease), decimal(minUnit), null, null);
    }

    private static RenewalUpliftRung rules(String id, String percent, String retention) {
        return new RenewalUpliftRung(RenewalUpliftRungKind.RULES, id, null, null, decimal(percent),
                null, null, null, null, null, null, retention);
    }

    private static RenewalUpliftRung priceAsOf(RenewalUpliftRungKind kind, String id, RenewalPriceAsOf priceAsOf,
                                               Boolean notAboveList) {
        return new RenewalUpliftRung(kind, id, null, null, null, null, notAboveList, null, null, null, priceAsOf, null);
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
