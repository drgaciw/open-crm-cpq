package com.opencrm.approval.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Escalation path")
class EscalationPathTest {

    @Test
    void defaultChainStepsUpFromTheCurrentTier() {
        assertEquals("SALES_VP", EscalationPath.next("DEAL_DESK", null).orElseThrow());
        assertEquals("SALES_VP", EscalationPath.next("MANAGER", "  ").orElseThrow());
        assertEquals("CFO", EscalationPath.next("SALES_VP", null).orElseThrow());
        assertEquals("CFO", EscalationPath.next("DIRECTOR", null).orElseThrow());
        assertTrue(EscalationPath.next("CFO", null).isEmpty());
    }

    @Test
    void configuredPathFromTheMatrixWins() {
        assertEquals("CFO", EscalationPath.next("deal_desk", "DEAL_DESK, CFO").orElseThrow());
        assertTrue(EscalationPath.next("CFO", "DEAL_DESK,CFO").isEmpty());
        assertTrue(EscalationPath.next("SALES_VP", "DEAL_DESK,CFO").isEmpty());
        assertTrue(EscalationPath.next(null, "DEAL_DESK,CFO").isEmpty());
    }
}
