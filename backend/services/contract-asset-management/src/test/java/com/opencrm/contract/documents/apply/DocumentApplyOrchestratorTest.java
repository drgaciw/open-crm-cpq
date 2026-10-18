package com.opencrm.contract.documents.apply;

import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.documents.ActionCode;
import com.opencrm.contract.domain.documents.AmendmentDocumentStatus;
import com.opencrm.contract.domain.documents.ApplicationUnitStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** AC-S4-1. DoD v0.3 Core 3 and v0.3 add 3 (state machine outcome). */
@Tag("unit")
class DocumentApplyOrchestratorTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final LocalDate END = LocalDate.of(2026, 12, 31);

    @Test
    void twoUnitsApplied() {
        ApplyFixture fixture = new ApplyFixture();
        Subscription first = fixture.subscription(1L, 4L, START, END);
        Subscription second = fixture.subscription(2L, 7L, START, END);
        fixture.unit(1, first, 4L);
        fixture.unit(2, second, 7L);
        fixture.line(1, ActionCode.ADD, null, new BigDecimal("1.0000"), START, END, "pb-1");
        fixture.line(2, ActionCode.ADD, null, new BigDecimal("2.0000"), START, END, "pb-1");

        ApplyResult result = fixture.apply();

        assertThat(result.replayed()).isFalse();
        assertThat(result.status()).isEqualTo(AmendmentDocumentStatus.APPLIED);
        assertThat(fixture.document.getStatus()).isEqualTo(AmendmentDocumentStatus.APPLIED);
        assertThat(fixture.units).allMatch(unit -> unit.getStatus() == ApplicationUnitStatus.APPLIED);
        assertThat(fixture.ledger).hasSize(2);
        assertThat(fixture.periods).hasSize(2);
        assertThat(fixture.binds).hasSize(2);
        assertThat(fixture.binds).extracting(ApplyFixture.LineBind::revisionNo).containsOnly(1);
        assertThat(fixture.eventsOfType(DocumentApplyOrchestrator.EVENT_UNIT_APPLIED)).isEqualTo(2);
        assertThat(fixture.eventsOfType(DocumentApplyOrchestrator.EVENT_DOCUMENT_APPLIED)).isEqualTo(1);
        assertThat(fixture.meters.counter(DocumentApplyOrchestrator.UNITS_METRIC, "result", "applied").count()).isEqualTo(2);
    }
}
