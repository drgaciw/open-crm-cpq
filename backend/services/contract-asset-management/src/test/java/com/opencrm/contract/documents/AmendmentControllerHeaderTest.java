package com.opencrm.contract.documents;

import com.opencrm.contract.config.GlobalExceptionHandler;
import com.opencrm.contract.domain.documents.AmendmentDocumentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Missing or invalid caller identity headers are 400. A 500, a null-pointer, or an
 * illegal-argument escape is a failure of this contract.
 */
class AmendmentControllerHeaderTest {

    private static final String BODY = """
            {"lifecycleChangeId":"11111111-1111-4111-8111-111111111111","lifecycleChangeRevision":1,
            "targetQuoteId":"22222222-2222-4222-8222-222222222222","inputsSnapshotRef":"snap",
            "signaturePolicy":"NOT_REQUIRED","actionDate":"2026-06-01","contractEffectiveDate":"2026-06-01",
            "backdatingReasonCode":null,"currency":"USD","lines":[]}
            """;

    private MockMvc mvc;

    @BeforeEach
    void controller() {
        AmendmentDocumentService documents = mock(AmendmentDocumentService.class);
        when(documents.createForHttp(any(), any(), any())).thenReturn(new AmendmentOutcome(sample(), false));
        mvc = MockMvcBuilders.standaloneSetup(new AmendmentController(documents, mock(AmendmentRevisionService.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "not-a-uuid"})
    void missingOrInvalidTenantIsBadRequest(String tenant) throws Exception {
        var request = post("/api/v1/contracts/{id}/amendments", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "key-1")
                .header("X-Caller-Id", "caller")
                .header("X-Actor-Id", "seller-1")
                .header("X-Actor-Type", "USER")
                .header("X-Actor-Role", "seller")
                .header("X-Correlation-Id", "corr")
                .content(BODY);
        if (tenant != null) {
            request.header("X-Tenant-Id", tenant);
        }
        mvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_REJECTED"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "NOPE"})
    void missingOrInvalidActorTypeIsBadRequest(String actorType) throws Exception {
        var request = post("/api/v1/contracts/{id}/amendments", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", "key-1")
                .header("X-Tenant-Id", UUID.randomUUID().toString())
                .header("X-Caller-Id", "caller")
                .header("X-Actor-Id", "seller-1")
                .header("X-Actor-Role", "seller")
                .header("X-Correlation-Id", "corr")
                .content(BODY);
        if (actorType != null) {
            request.header("X-Actor-Type", actorType);
        }
        mvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_REJECTED"));
    }

    @Test
    void validIdentityHeadersReachTheService() throws Exception {
        mvc.perform(post("/api/v1/contracts/{id}/amendments", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "key-1")
                        .header("X-Tenant-Id", UUID.randomUUID().toString())
                        .header("X-Caller-Id", "caller")
                        .header("X-Actor-Id", "seller-1")
                        .header("X-Actor-Type", "USER")
                        .header("X-Actor-Role", "seller")
                        .header("X-Correlation-Id", "corr")
                        .content(BODY))
                .andExpect(status().isCreated());
    }

    private static AmendmentView sample() {
        return new AmendmentView(UUID.randomUUID(), UUID.randomUUID(), "P", null, null,
                AmendmentDocumentStatus.DRAFT, 0, null, null, null, null, null, null, null, List.of(), List.of());
    }
}
