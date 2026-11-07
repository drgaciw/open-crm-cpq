package com.opencrm.contract.documents.apply;

import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.documents.ActionCode;
import com.opencrm.contract.domain.documents.AmendmentDocumentStatus;
import com.opencrm.contract.domain.documents.ApplicationUnitStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * DRG-623 residual: null contract_id is TARGET_CONTRACT_UNSET; wrong contract/tenant stays mismatch;
 * missing subscription stays V3; #749 hold still refuses before unit membership writes.
 */
@Tag("unit")
class DocumentApplyTargetContractMembershipTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final LocalDate END = LocalDate.of(2026, 12, 31);

    @Test
    void unsetContractBlocksWithoutRatesLedgerOrRevisionMutation() {
        ApplyFixture fixture = new ApplyFixture();
        Subscription subscription = fixture.subscription(1L, 3L, START, END);
        fixture.unit(1, subscription, 3L);
        fixture.line(1, ActionCode.ADD, null, BigDecimal.ONE, START, END, "pb-1");
        when(fixture.gateway.targetContractMembership(anyLong(), any(), any()))
                .thenReturn(TargetContractMembership.UNSET);

        ApplyResult result = fixture.apply();

        assertThat(result.status()).isEqualTo(AmendmentDocumentStatus.APPLY_BLOCKED);
        assertThat(fixture.units.getFirst().getStatus()).isEqualTo(ApplicationUnitStatus.BLOCKED);
        assertThat(fixture.units.getFirst().getBlockReason()).isEqualTo("TARGET_CONTRACT_UNSET");
        assertThat(fixture.ledger).isEmpty();
        assertThat(fixture.periods).isEmpty();
        assertThat(fixture.binds).isEmpty();
        assertThat(subscription.getCommercialRevision()).isEqualTo(3L);
        var blocked = fixture.events.stream()
                .filter(event -> DocumentApplyOrchestrator.EVENT_UNIT_BLOCKED.equals(event.eventType()))
                .toList();
        assertThat(blocked).hasSize(1);
        assertThat(blocked.getFirst().reasonCode()).isEqualTo("TARGET_CONTRACT_UNSET");
        assertThat(blocked.getFirst().payloadRef()).contains("no contract link");
        assertThat(fixture.eventsOfType(DocumentApplyOrchestrator.EVENT_DOCUMENT_BLOCKED)).isEqualTo(1);

        ApplyResult replay = fixture.apply();
        assertThat(replay.replayed()).isTrue();
        assertThat(fixture.eventsOfType(DocumentApplyOrchestrator.EVENT_UNIT_BLOCKED)).isEqualTo(1);
        assertThat(subscription.getCommercialRevision()).isEqualTo(3L);
    }

    @Test
    void mismatchContractStaysRefusedAsMismatch() {
        ApplyFixture fixture = readyUnit();
        when(fixture.gateway.targetContractMembership(anyLong(), any(), any()))
                .thenReturn(TargetContractMembership.MISMATCH);

        ApplyResult result = fixture.apply();

        assertThat(result.status()).isEqualTo(AmendmentDocumentStatus.APPLY_BLOCKED);
        assertThat(fixture.units.getFirst().getBlockReason()).isEqualTo("TARGET_CONTRACT_MISMATCH");
        assertThat(fixture.ledger).isEmpty();
        assertThat(fixture.periods).isEmpty();
        assertThat(fixture.events.stream()
                .filter(event -> DocumentApplyOrchestrator.EVENT_UNIT_BLOCKED.equals(event.eventType()))
                .map(event -> event.reasonCode()))
                .containsExactly("TARGET_CONTRACT_MISMATCH");
    }

    @Test
    void missingSubscriptionStaysV3AndIsDistinctFromUnset() {
        ApplyFixture fixture = new ApplyFixture();
        fixture.unit(1, fixture.subscription(1L, 3L, START, END), 3L);
        fixture.units.getFirst().setTargetRef(java.util.UUID.randomUUID().toString());
        fixture.line(1, ActionCode.ADD, null, BigDecimal.ONE, START, END, "pb-1");
        when(fixture.gateway.targetContractMembership(anyLong(), any(), any()))
                .thenReturn(TargetContractMembership.UNSET);

        ApplyResult result = fixture.apply();

        assertThat(fixture.units.getFirst().getBlockReason()).isEqualTo("V3");
        assertThat(result.status()).isEqualTo(AmendmentDocumentStatus.APPLY_BLOCKED);
        assertThat(fixture.events.stream()
                .filter(event -> DocumentApplyOrchestrator.EVENT_UNIT_BLOCKED.equals(event.eventType()))
                .map(event -> event.reasonCode()))
                .containsExactly("V3");
    }

    @Test
    void matchedMembershipStillAppliesWhenReady() {
        ApplyFixture fixture = new ApplyFixture();
        Subscription subscription = fixture.subscription(1L, 3L, START, END);
        fixture.unit(1, subscription, 3L);
        fixture.line(1, ActionCode.ADD, null, BigDecimal.ONE, START, END, "pb-1");

        ApplyResult result = fixture.apply();

        assertThat(result.status()).isEqualTo(AmendmentDocumentStatus.APPLIED);
        assertThat(fixture.units.getFirst().getStatus()).isEqualTo(ApplicationUnitStatus.APPLIED);
        assertThat(fixture.ledger).hasSize(1);
        assertThat(fixture.periods).hasSize(1);
        assertThat(subscription.getCommercialRevision()).isEqualTo(4L);
    }

    @Test
    void multiUnitUnsetPreservesAppliedSiblingAndDocumentPartialOutcome() {
        ApplyFixture fixture = new ApplyFixture();
        Subscription first = fixture.subscription(1L, 4L, START, END);
        Subscription second = fixture.subscription(2L, 7L, START, END);
        fixture.unit(1, first, 4L);
        fixture.unit(2, second, 7L);
        fixture.line(1, ActionCode.ADD, null, BigDecimal.ONE, START, END, "pb-1");
        fixture.line(2, ActionCode.ADD, null, BigDecimal.ONE, START, END, "pb-1");
        when(fixture.gateway.targetContractMembership(anyLong(), any(), any())).thenAnswer(invocation -> {
            long id = invocation.getArgument(0);
            return id == 2L ? TargetContractMembership.UNSET : TargetContractMembership.MATCHED;
        });

        ApplyResult result = fixture.apply();

        assertThat(result.status()).isEqualTo(AmendmentDocumentStatus.PARTIALLY_APPLIED);
        assertThat(fixture.units.get(0).getStatus()).isEqualTo(ApplicationUnitStatus.APPLIED);
        assertThat(fixture.units.get(1).getStatus()).isEqualTo(ApplicationUnitStatus.BLOCKED);
        assertThat(fixture.units.get(1).getBlockReason()).isEqualTo("TARGET_CONTRACT_UNSET");
        assertThat(fixture.ledger).hasSize(1);
        assertThat(fixture.periods).hasSize(1);
        assertThat(first.getCommercialRevision()).isEqualTo(5L);
        assertThat(second.getCommercialRevision()).isEqualTo(7L);
        assertThat(fixture.eventsOfType(DocumentApplyOrchestrator.EVENT_UNIT_BLOCKED)).isEqualTo(1);
        assertThat(fixture.eventsOfType(DocumentApplyOrchestrator.EVENT_DOCUMENT_PARTIAL)).isEqualTo(1);

        assertThat(fixture.apply().replayed()).isTrue();
        assertThat(fixture.eventsOfType(DocumentApplyOrchestrator.EVENT_UNIT_BLOCKED)).isEqualTo(1);
        assertThat(fixture.ledger).hasSize(1);
        assertThat(second.getCommercialRevision()).isEqualTo(7L);
    }

    @Test
    void assetLifecycleHoldRefusesBeforeUnsetMembershipBlock() {
        ApplyFixture fixture = new ApplyFixture(false);
        Subscription subscription = fixture.subscription(1L, 3L, START, END);
        fixture.unit(1, subscription, 3L);
        fixture.line(1, ActionCode.ADD, null, BigDecimal.ONE, START, END, "pb-1");
        when(fixture.gateway.targetContractMembership(anyLong(), any(), any()))
                .thenReturn(TargetContractMembership.UNSET);

        assertThatThrownBy(fixture::apply).isInstanceOfSatisfying(DocumentApplyException.class, ex -> {
            assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(ex.getErrorCode()).isEqualTo("ASSET_LIFECYCLE_HOLD");
        });

        assertThat(fixture.units.getFirst().getStatus()).isEqualTo(ApplicationUnitStatus.PENDING);
        assertThat(fixture.units.getFirst().getBlockReason()).isNull();
        assertThat(fixture.ledger).isEmpty();
        assertThat(fixture.periods).isEmpty();
        assertThat(subscription.getCommercialRevision()).isEqualTo(3L);
        assertThat(fixture.events).allSatisfy(event -> assertThat(event.reasonCode()).isEqualTo("ASSET_LIFECYCLE_HOLD"));
        assertThat(new AssetLifecycleReadiness().isReady()).isFalse();
    }

    private static ApplyFixture readyUnit() {
        ApplyFixture fixture = new ApplyFixture();
        fixture.unit(1, fixture.subscription(1L, 3L, START, END), 3L);
        fixture.line(1, ActionCode.ADD, null, BigDecimal.ONE, START, END, "pb-1");
        return fixture;
    }
}

