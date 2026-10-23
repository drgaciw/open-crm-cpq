package com.opencrm.contract.documents.apply;

import com.opencrm.contract.documents.AmendmentStateMachine;
import com.opencrm.contract.documents.events.AmendmentEventAppend;
import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.documents.ActionCode;
import com.opencrm.contract.domain.documents.AmendmentDocumentStatus;
import com.opencrm.contract.domain.documents.ApplicationUnitStatus;
import com.opencrm.contract.domain.documents.DocumentApplyLedgerEntry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DRG-621. A later unit's digest conflict must keep earlier commits, block that unit,
 * record PARTIALLY_APPLIED, and return the result (controller HTTP 200). A conflict that
 * blocks every unit still refuses with 409 and leaves the document EXECUTED.
 * DoD v0.3 items 3, 13, 14, 17, and 18.
 */
@Tag("unit")
class DocumentApplyMidLoopConflictTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final LocalDate END = LocalDate.of(2026, 12, 31);
    private static final String WRONG_DIGEST = "ab".repeat(32);

    @Test
    void unitTwoConflictKeepsUnitOneRecordsPartialAndReturnsTheOutcome() {
        ApplyFixture fixture = threeUnitsWithUnitTwoConflict();

        ApplyResult result = fixture.apply();

        assertThat(result.replayed()).isFalse();
        assertThat(result.status()).isEqualTo(AmendmentDocumentStatus.PARTIALLY_APPLIED);
        assertThat(fixture.document.getStatus()).isEqualTo(AmendmentDocumentStatus.PARTIALLY_APPLIED);
        assertThat(AmendmentStateMachine.apply(AmendmentDocumentStatus.EXECUTED, AmendmentDocumentStatus.PARTIALLY_APPLIED)
                .allowed()).isTrue();
        assertThat(fixture.units).extracting(unit -> unit.getStatus()).containsExactly(
                ApplicationUnitStatus.APPLIED, ApplicationUnitStatus.BLOCKED, ApplicationUnitStatus.APPLIED);
        assertThat(fixture.ledger).hasSize(3);
        assertThat(fixture.ledger.get(fixture.document.getId() + ":2").getApplyKeyDigest()).isEqualTo(WRONG_DIGEST);
        assertThat(fixture.periods).hasSize(2);
        assertThat(fixture.periods).extracting(period -> period.getSubscriptionId()).containsExactly(1L, 3L);

        List<AmendmentEventAppend> blocked = fixture.events.stream()
                .filter(event -> DocumentApplyOrchestrator.EVENT_UNIT_BLOCKED.equals(event.eventType()))
                .toList();
        assertThat(blocked).hasSize(1);
        assertThat(blocked.get(0).reasonCode()).isEqualTo("DIGEST_CONFLICT");
        assertThat(blocked.get(0).unitNo()).isEqualTo(2);
        assertThat(fixture.events.stream().filter(event -> "DIGEST_CONFLICT".equals(event.reasonCode()))).hasSize(1);
        assertThat(fixture.events.stream()
                .filter(event -> DocumentApplyOrchestrator.EVENT_UNIT_APPLIED.equals(event.eventType()))
                .map(AmendmentEventAppend::unitNo)).containsExactly(1, 3);
        assertThat(fixture.eventsOfType(DocumentApplyOrchestrator.EVENT_DOCUMENT_PARTIAL)).isEqualTo(1);
        assertThat(fixture.eventsOfType(DocumentApplyOrchestrator.EVENT_REFUSED)).isZero();
        assertThat(fixture.meters.counter(DocumentApplyOrchestrator.ALERT_METRIC, "status", "PARTIALLY_APPLIED").count())
                .isEqualTo(1);
    }

    @Test
    void replayAfterMidLoopConflictDoesNotApplyAgain() {
        ApplyFixture fixture = threeUnitsWithUnitTwoConflict();
        fixture.apply();
        int events = fixture.events.size();
        int periods = fixture.periods.size();
        int ledger = fixture.ledger.size();

        ApplyResult replay = fixture.apply();

        assertThat(replay.replayed()).isTrue();
        assertThat(replay.status()).isEqualTo(AmendmentDocumentStatus.PARTIALLY_APPLIED);
        assertThat(fixture.document.getStatus()).isEqualTo(AmendmentDocumentStatus.PARTIALLY_APPLIED);
        assertThat(fixture.units).extracting(unit -> unit.getStatus()).containsExactly(
                ApplicationUnitStatus.APPLIED, ApplicationUnitStatus.BLOCKED, ApplicationUnitStatus.APPLIED);
        assertThat(fixture.events).hasSize(events);
        assertThat(fixture.periods).hasSize(periods);
        assertThat(fixture.ledger).hasSize(ledger);
        assertThat(fixture.ledger.get(fixture.document.getId() + ":2").getApplyKeyDigest()).isEqualTo(WRONG_DIGEST);
        assertThat(fixture.eventsOfType(DocumentApplyOrchestrator.EVENT_REFUSED)).isZero();
        assertThat(fixture.eventsOfType(DocumentApplyOrchestrator.EVENT_UNIT_BLOCKED)).isEqualTo(1);
        assertThat(fixture.meters.counter(DocumentApplyOrchestrator.ALERT_METRIC, "status", "PARTIALLY_APPLIED").count())
                .isEqualTo(1);
    }

    private static ApplyFixture threeUnitsWithUnitTwoConflict() {
        ApplyFixture fixture = new ApplyFixture();
        Subscription first = fixture.subscription(1L, 4L, START, END);
        Subscription second = fixture.subscription(2L, 7L, START, END);
        Subscription third = fixture.subscription(3L, 5L, START, END);
        fixture.unit(1, first, 4L);
        fixture.unit(2, second, 7L);
        fixture.unit(3, third, 5L);
        fixture.line(1, ActionCode.ADD, null, BigDecimal.ONE, START, END, "pb-1");
        fixture.line(2, ActionCode.ADD, null, BigDecimal.ONE, START, END, "pb-1");
        fixture.line(3, ActionCode.ADD, null, BigDecimal.ONE, START, END, "pb-1");
        fixture.ledger.put(fixture.document.getId() + ":2", DocumentApplyLedgerEntry.builder()
                .documentId(fixture.document.getId())
                .unitNo(2)
                .tenantId(fixture.tenantId)
                .applyKeyDigest(WRONG_DIGEST)
                .createdBy("earlier")
                .build());
        return fixture;
    }
}
