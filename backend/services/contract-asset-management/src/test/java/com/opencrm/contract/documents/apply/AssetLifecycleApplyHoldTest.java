package com.opencrm.contract.documents.apply;

import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.documents.ActionCode;
import com.opencrm.contract.domain.documents.AmendmentDocumentStatus;
import com.opencrm.contract.domain.documents.ApplicationUnitStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@Tag("unit")
class AssetLifecycleApplyHoldTest {
    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final LocalDate END = LocalDate.of(2026, 12, 31);

    @Test
    void productionReadinessIsHardHeld() {
        assertThat(new AssetLifecycleReadiness().isReady()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = ActionCode.class, names = {"ADD", "UPDATE", "REMOVE", "TERMINATE", "SWAP", "UPGRADE"})
    void allSupportedAssetMutationsAreHeld(ActionCode action) {
        ApplyFixture fixture = oneUnit(false);
        fixture.line(1, action, "source", BigDecimal.ONE, START, END, "pb-1");
        assertHeld(fixture);
        assertThat(fixture.units.getFirst().getStatus()).isEqualTo(ApplicationUnitStatus.PENDING);
        assertThat(fixture.ledger).isEmpty();
        assertThat(fixture.periods).isEmpty();
    }

    @Test
    void missingLineEvidenceIsHeld() {
        ApplyFixture fixture = oneUnit(false);
        assertHeld(fixture);
        assertThat(fixture.units.getFirst().getStatus()).isEqualTo(ApplicationUnitStatus.PENDING);
    }

    @Test
    void unsupportedOnlyUnitKeepsItsExistingBlockedResult() {
        ApplyFixture fixture = oneUnit(false);
        fixture.line(1, ActionCode.NO_CHANGE, null, BigDecimal.ONE, START, END, "pb-1");
        ApplyResult result = fixture.apply();
        assertThat(result.status()).isEqualTo(AmendmentDocumentStatus.APPLY_BLOCKED);
        assertThat(fixture.units.getFirst().getBlockReason()).isEqualTo("ACTION_NOT_IN_SLICE");
    }

    @Test
    void completedIdenticalReplayWorksWhileHoldIsActive() {
        ApplyFixture fixture = oneUnit(true);
        fixture.line(1, ActionCode.ADD, null, BigDecimal.ONE, START, END, "pb-1");
        assertThat(fixture.apply().replayed()).isFalse();
        when(fixture.readiness.isReady()).thenReturn(false);
        assertThat(fixture.apply().replayed()).isTrue();
        assertThat(fixture.ledger).hasSize(1);
    }

    @Test
    void lockedRecheckHoldsNewAssetWorkEvenAfterAnUnsupportedSiblingWasBlocked() {
        ApplyFixture fixture = oneUnit(false);
        Subscription second = fixture.subscription(2L, 4L, START, END);
        fixture.unit(2, second, 4L);
        fixture.line(1, ActionCode.NO_CHANGE, null, BigDecimal.ONE, START, END, "pb-1");
        fixture.line(2, ActionCode.NO_CHANGE, null, BigDecimal.ONE, START, END, "pb-1");
        fixture.afterDocumentUnlock = call -> {
            if (call == 2) {
                ApplyLine old = fixture.lines.get(1);
                fixture.lines.set(1, new ApplyLine(old.id(), old.lineNo(), old.unitNo(), ActionCode.ADD,
                        old.sourceAcceptedLineUuid(), old.endQty(), old.effectiveFrom(), old.effectiveTo(),
                        old.pricebookVersion(), old.productExternalId(), old.unitPrice()));
            }
        };
        assertHeld(fixture);
        assertThat(fixture.document.getStatus()).isEqualTo(AmendmentDocumentStatus.EXECUTED);
        assertThat(fixture.units.getFirst().getStatus()).isEqualTo(ApplicationUnitStatus.BLOCKED);
        assertThat(fixture.units.get(1).getStatus()).isEqualTo(ApplicationUnitStatus.PENDING);
        assertThat(fixture.ledger).isEmpty();
        assertThat(fixture.periods).isEmpty();
        assertThat(fixture.eventsOfType(DocumentApplyOrchestrator.EVENT_REFUSED)).isEqualTo(1);
    }

    @Test
    void defaultHoldRefusesBeforeAnyUnitWriteAndLeavesTheAttemptRetryable() {
        ApplyFixture fixture = new ApplyFixture(false);
        Subscription first = fixture.subscription(1L, 3L, START, END);
        Subscription second = fixture.subscription(2L, 4L, START, END);
        fixture.unit(1, first, 3L);
        fixture.unit(2, second, 4L);
        fixture.line(1, ActionCode.ADD, null, BigDecimal.ONE, START, END, "pb-1");
        fixture.line(2, ActionCode.UPDATE, "line-2", BigDecimal.ONE, START, END, "pb-1");

        for (int attempt = 0; attempt < 2; attempt++) {
            assertThatThrownBy(fixture::apply).isInstanceOfSatisfying(DocumentApplyException.class, ex -> {
                assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                assertThat(ex.getErrorCode()).isEqualTo("ASSET_LIFECYCLE_HOLD");
            });
        }

        assertThat(fixture.document.getStatus()).isEqualTo(AmendmentDocumentStatus.EXECUTED);
        assertThat(fixture.units).allSatisfy(unit -> assertThat(unit.getStatus()).isEqualTo(ApplicationUnitStatus.PENDING));
        assertThat(first.getCommercialRevision()).isEqualTo(3L);
        assertThat(second.getCommercialRevision()).isEqualTo(4L);
        assertThat(fixture.ledger).isEmpty();
        assertThat(fixture.periods).isEmpty();
        assertThat(fixture.binds).isEmpty();
        assertThat(fixture.eventsOfType(DocumentApplyOrchestrator.EVENT_REFUSED)).isEqualTo(2);
        assertThat(fixture.events).allSatisfy(event -> assertThat(event.reasonCode()).isEqualTo("ASSET_LIFECYCLE_HOLD"));
    }

    private static ApplyFixture oneUnit(boolean ready) {
        ApplyFixture fixture = new ApplyFixture(ready);
        Subscription subscription = fixture.subscription(1L, 3L, START, END);
        fixture.unit(1, subscription, 3L);
        return fixture;
    }

    private static void assertHeld(ApplyFixture fixture) {
        assertThatThrownBy(fixture::apply).isInstanceOfSatisfying(DocumentApplyException.class, ex -> {
            assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(ex.getErrorCode()).isEqualTo("ASSET_LIFECYCLE_HOLD");
        });
    }
}
