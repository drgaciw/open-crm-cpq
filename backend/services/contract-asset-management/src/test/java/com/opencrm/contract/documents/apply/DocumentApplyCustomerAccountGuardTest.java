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

/**
 * DRG-663 AC3/AC6/AC7: CUSTOMER_ACCOUNT_UNSET and CUSTOMER_ACCOUNT_MISMATCH
 * block apply with APPLY_UNIT_BLOCKED audit. Never pass on a missing typed key.
 */
@Tag("unit")
class DocumentApplyCustomerAccountGuardTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final LocalDate END = LocalDate.of(2026, 12, 31);
    private static final LocalDate DAY = LocalDate.of(2026, 3, 1);

    @Test
    void customerAccountMismatchBlocksWithExactAuditCodes() {
        ApplyFixture fixture = new ApplyFixture();
        Subscription subscription = fixture.subscription(1L, 2L, START, END);
        fixture.unit(1, subscription, 2L);
        fixture.line(1, ActionCode.ADD, null, BigDecimal.ONE, DAY, END, "pb-1");
        when(fixture.gateway.contractCustomerAccountBlock(anyLong())).thenReturn("CUSTOMER_ACCOUNT_MISMATCH");

        ApplyResult result = fixture.apply();

        assertThat(result.status()).isEqualTo(AmendmentDocumentStatus.APPLY_BLOCKED);
        assertThat(fixture.units.get(0).getStatus()).isEqualTo(ApplicationUnitStatus.BLOCKED);
        assertThat(fixture.units.get(0).getBlockReason()).isEqualTo("CUSTOMER_ACCOUNT_MISMATCH");
        assertThat(fixture.ledger).isEmpty();
        assertThat(fixture.events).anyMatch(event ->
                DocumentApplyOrchestrator.EVENT_UNIT_BLOCKED.equals(event.eventType())
                        && "CUSTOMER_ACCOUNT_MISMATCH".equals(event.reasonCode()));
    }

    @Test
    void customerAccountUnsetBlocksWhenContractLinkedAndTypedKeyNull() {
        ApplyFixture fixture = new ApplyFixture();
        Subscription subscription = fixture.subscription(1L, 2L, START, END);
        fixture.unit(1, subscription, 2L);
        fixture.line(1, ActionCode.ADD, null, BigDecimal.ONE, DAY, END, "pb-1");
        when(fixture.gateway.contractCustomerAccountBlock(anyLong())).thenReturn("CUSTOMER_ACCOUNT_UNSET");

        ApplyResult result = fixture.apply();

        assertThat(result.status()).isEqualTo(AmendmentDocumentStatus.APPLY_BLOCKED);
        assertThat(fixture.units.get(0).getStatus()).isEqualTo(ApplicationUnitStatus.BLOCKED);
        assertThat(fixture.units.get(0).getBlockReason()).isEqualTo("CUSTOMER_ACCOUNT_UNSET");
        assertThat(fixture.events).anyMatch(event ->
                DocumentApplyOrchestrator.EVENT_UNIT_BLOCKED.equals(event.eventType())
                        && "CUSTOMER_ACCOUNT_UNSET".equals(event.reasonCode()));
    }
}
