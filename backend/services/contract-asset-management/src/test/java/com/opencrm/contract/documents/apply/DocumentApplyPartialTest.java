package com.opencrm.contract.documents.apply;

import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.documents.ActionCode;
import com.opencrm.contract.domain.documents.AmendmentDocumentStatus;
import com.opencrm.contract.domain.documents.ApplicationUnitStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AC-S4-3. OQ5: earlier units stay applied, the failing unit is blocked, the document is
 * PARTIALLY_APPLIED, and a later call is a separate invocation. DoD Core 3, v0.3 add 2, v0.3 add 3.
 */
@Tag("unit")
class DocumentApplyPartialTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final LocalDate END = LocalDate.of(2026, 12, 31);

    @Test
    void staleSecondUnitLeavesTheFirstAppliedWithoutRollbackOrAnotherPass() throws Exception {
        ApplyFixture fixture = new ApplyFixture();
        Subscription first = fixture.subscription(1L, 4L, START, END);
        Subscription second = fixture.subscription(2L, 9L, START, END);
        fixture.unit(1, first, 4L);
        fixture.unit(2, second, 3L);
        fixture.line(1, ActionCode.ADD, null, BigDecimal.ONE, START, END, "pb-1");
        fixture.line(2, ActionCode.ADD, null, BigDecimal.ONE, START, END, "pb-1");

        ApplyResult result = fixture.apply();

        assertThat(result.status()).isEqualTo(AmendmentDocumentStatus.PARTIALLY_APPLIED);
        assertThat(fixture.units.get(0).getStatus()).isEqualTo(ApplicationUnitStatus.APPLIED);
        assertThat(fixture.units.get(1).getStatus()).isEqualTo(ApplicationUnitStatus.BLOCKED);
        assertThat(fixture.ledger).hasSize(1);
        assertThat(fixture.ledger.containsKey(fixture.document.getId() + ":1")).isTrue();
        assertThat(fixture.ledger.containsKey(fixture.document.getId() + ":2")).isFalse();
        assertThat(fixture.periods).hasSize(1);
        assertThat(fixture.periods.get(0).getSubscriptionId()).isEqualTo(1L);
        var blocked = fixture.events.stream()
                .filter(event -> DocumentApplyOrchestrator.EVENT_UNIT_BLOCKED.equals(event.eventType()))
                .toList();
        assertThat(blocked).hasSize(1);
        assertThat(blocked.get(0).reasonCode()).isEqualTo("V3");
        assertThat(blocked.get(0).unitNo()).isEqualTo(2);
        assertThat(blocked.get(0).payloadRef()).contains("observed commercial revision");
        assertThat(fixture.eventsOfType(DocumentApplyOrchestrator.EVENT_DOCUMENT_PARTIAL)).isEqualTo(1);
        assertThat(fixture.meters.counter(DocumentApplyOrchestrator.ALERT_METRIC, "status", "PARTIALLY_APPLIED").count())
                .isEqualTo(1);
        String source = Files.readString(orchestratorSource());
        assertThat(source).doesNotContain("retry");

        int periods = fixture.periods.size();
        ApplyResult replay = fixture.apply();
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.unitOutcomes().get(1).reason()).isEqualTo("V3");
        assertThat(fixture.units.get(0).getStatus()).isEqualTo(ApplicationUnitStatus.APPLIED);
        assertThat(fixture.units.get(1).getStatus()).isEqualTo(ApplicationUnitStatus.BLOCKED);
        assertThat(fixture.periods).hasSize(periods);
        assertThat(fixture.ledger).hasSize(1);
        assertThat(fixture.eventsOfType(DocumentApplyOrchestrator.EVENT_UNIT_BLOCKED)).isEqualTo(1);
        assertThat(fixture.eventsOfType(DocumentApplyOrchestrator.EVENT_REFUSED)).isZero();
    }

    private static Path orchestratorSource() {
        Path cursor = Path.of("").toAbsolutePath();
        Path relative = Path.of("backend/services/contract-asset-management/src/main/java/com/opencrm/contract/documents/apply/DocumentApplyOrchestrator.java");
        while (cursor != null && !Files.exists(cursor.resolve(relative))) {
            cursor = cursor.getParent();
        }
        assertThat(cursor).isNotNull();
        return cursor.resolve(relative);
    }
}
