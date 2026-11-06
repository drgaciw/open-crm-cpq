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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/** DRG-662 AC4/AC7/AC9: TENANT_MISMATCH and TENANT_UNSET block apply with audit. */
@Tag("unit")
class DocumentApplyTenantGuardTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final LocalDate END = LocalDate.of(2026, 12, 31);
    private static final LocalDate DAY = LocalDate.of(2026, 3, 1);

    @Test
    void tenantMismatchBlocksWithExactAuditCodes() {
        ApplyFixture fixture = new ApplyFixture();
        Subscription subscription = fixture.subscription(1L, 2L, START, END);
        fixture.unit(1, subscription, 2L);
        fixture.line(1, ActionCode.ADD, null, BigDecimal.ONE, DAY, END, "pb-1");
        when(fixture.gateway.contractTenantBlock(anyLong())).thenReturn("TENANT_MISMATCH");

        ApplyResult result = fixture.apply();

        assertThat(result.status()).isEqualTo(AmendmentDocumentStatus.APPLY_BLOCKED);
        assertThat(fixture.units.get(0).getStatus()).isEqualTo(ApplicationUnitStatus.BLOCKED);
        assertThat(fixture.units.get(0).getBlockReason()).isEqualTo("TENANT_MISMATCH");
        assertThat(fixture.ledger).isEmpty();
        assertThat(fixture.events).anyMatch(event ->
                DocumentApplyOrchestrator.EVENT_UNIT_BLOCKED.equals(event.eventType())
                        && "TENANT_MISMATCH".equals(event.reasonCode()));
    }

    @Test
    void tenantUnsetBlocksWhenContractLinkedAndSubscriptionTenantNull() {
        ApplyFixture fixture = new ApplyFixture();
        Subscription subscription = fixture.subscription(1L, 2L, START, END);
        fixture.unit(1, subscription, 2L);
        fixture.line(1, ActionCode.ADD, null, BigDecimal.ONE, DAY, END, "pb-1");
        when(fixture.gateway.contractTenantBlock(anyLong())).thenReturn("TENANT_UNSET");

        ApplyResult result = fixture.apply();

        assertThat(result.status()).isEqualTo(AmendmentDocumentStatus.APPLY_BLOCKED);
        assertThat(fixture.units.get(0).getStatus()).isEqualTo(ApplicationUnitStatus.BLOCKED);
        assertThat(fixture.units.get(0).getBlockReason()).isEqualTo("TENANT_UNSET");
        assertThat(fixture.events).anyMatch(event ->
                DocumentApplyOrchestrator.EVENT_UNIT_BLOCKED.equals(event.eventType())
                        && "TENANT_UNSET".equals(event.reasonCode()));
    }
}
