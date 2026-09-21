package com.opencrm.order.controller;

import com.opencrm.order.finance.FinanceService;
import com.opencrm.order.finance.FinanceDtos.Acknowledgment;
import com.opencrm.order.fulfillment.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrderEvidenceControllerTest {
    @Test void canonicalEvidenceRouteRetainsPendingCausalEvidence() throws Exception {
        var evidence=mock(EvidenceService.class);var finance=mock(FinanceService.class);
        when(evidence.record(eq("order"),any())).thenReturn(new EvidenceReceipt("order","proof","PENDING",4));
        var mvc=MockMvcBuilders.standaloneSetup(new OrderEvidenceController(evidence,finance)).build();
        mvc.perform(post("/api/v1/orders/order/evidence").contentType("application/json").content("""
            {"expectedVersion":3,"operationKey":"delivery","reason":"carrier confirmation","lineId":"line",
             "kind":"DELIVERY","quantity":2,"externalReference":"POD","shipmentReference":"SHIP",
             "occurredAt":"2026-09-01T00:00:00Z"}
            """)).andExpect(status().isAccepted()).andExpect(jsonPath("$.data.status").value("PENDING"));
        verify(evidence).record(eq("order"),argThat(c->"SHIP".equals(c.shipmentReference())&&c.quantity()==2));
        verifyNoInteractions(finance);
    }
    @Test void financeEvidenceUsesAcknowledgmentLedgerAndNeverGenericCompletion() throws Exception {
        var evidence=mock(EvidenceService.class);var finance=mock(FinanceService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new OrderEvidenceController(evidence,finance)).build();
        mvc.perform(post("/api/v1/orders/order/evidence").contentType("application/json").content("""
            {"expectedVersion":3,"operationKey":"finance","reason":"verified accounting record","lineId":"line",
             "kind":"FINANCE_ACK","quantity":2,"externalReference":"invoice-line","requestKey":"request",
             "occurredAt":"2026-09-01T00:00:00Z"}
            """)).andExpect(status().isOk());
        verify(finance).acknowledge(eq("order"),argThat((Acknowledgment c)->"ACKNOWLEDGED".equals(c.outcome())&&"request".equals(c.requestKey())));
        verifyNoInteractions(evidence);
    }
}
