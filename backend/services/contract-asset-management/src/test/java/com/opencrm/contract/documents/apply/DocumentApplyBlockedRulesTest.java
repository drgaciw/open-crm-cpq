package com.opencrm.contract.documents.apply;

import com.opencrm.contract.domain.Subscription;
import com.opencrm.contract.domain.documents.ActionCode;
import com.opencrm.contract.domain.documents.AmendmentDocumentStatus;
import com.opencrm.contract.domain.documents.ApplicationUnitStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** AC-S4-6 and AC-S4-7. DoD v0.3 Core 3 and v0.3 add 3. */
@Tag("unit")
class DocumentApplyBlockedRulesTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final LocalDate END = LocalDate.of(2026, 12, 31);
    private static final LocalDate DAY = LocalDate.of(2026, 3, 1);

    @Test
    void retiredSourceLine() {
        ApplyFixture fixture = new ApplyFixture();
        Subscription subscription = fixture.subscription(1L, 2L, START, END);
        fixture.unit(1, subscription, 2L);
        fixture.line(1, ActionCode.UPDATE, "line-source", new BigDecimal("80.0000"), DAY, END, "pb-1");
        var source = fixture.openPeriod(subscription, "line-source", new BigDecimal("100.0000"), START, END);
        source.setRetired(true);

        ApplyResult result = fixture.apply();

        assertThat(result.status()).isEqualTo(AmendmentDocumentStatus.APPLY_BLOCKED);
        assertThat(fixture.units.get(0).getStatus()).isEqualTo(ApplicationUnitStatus.BLOCKED);
        assertThat(fixture.ledger).isEmpty();
        assertThat(fixture.periods).containsExactly(source);
        assertThat(source.getEffectiveEndDate()).isEqualTo(END);
        assertThat(fixture.events).anyMatch(event -> "V4".equals(event.reasonCode()));
        assertThat(fixture.meters.counter(DocumentApplyOrchestrator.ALERT_METRIC, "status", "APPLY_BLOCKED").count())
                .isEqualTo(1);
    }

    @Test
    void pricebookPinMismatch() {
        ApplyFixture fixture = new ApplyFixture();
        Subscription subscription = fixture.subscription(1L, 2L, START, END);
        fixture.unit(1, subscription, 2L);
        fixture.line(1, ActionCode.ADD, null, BigDecimal.ONE, DAY, END, "pb-pinned");

        ApplyResult result = fixture.orchestrator.apply(new ApplyCommand(
                fixture.document.getId(), fixture.tenantId, "quote-service", "ROLE_INTERNAL_INVOKE", "corr-s4",
                Map.of(1, "pb-live")));

        assertThat(result.status()).isEqualTo(AmendmentDocumentStatus.APPLY_BLOCKED);
        assertThat(fixture.units.get(0).getStatus()).isEqualTo(ApplicationUnitStatus.BLOCKED);
        assertThat(fixture.ledger).isEmpty();
        assertThat(fixture.periods).isEmpty();
        assertThat(fixture.events).anyMatch(event -> "V9".equals(event.reasonCode())
                && event.payloadRef().contains("pricebook"));
    }
}
