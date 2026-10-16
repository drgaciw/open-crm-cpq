package com.opencrm.contract.documents.audit;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AmendmentAuditPublicationTest {

    private static final UUID DOCUMENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID EVENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String DIGEST = "a".repeat(64);

    @Test
    void payloadIsDocumentSequenceAndDigestOnly() {
        String json = new AmendmentAuditPublication(
                DOCUMENT_ID, DOCUMENT_ID, null, null, DIGEST, "corr-1", EVENT_ID).toJson();

        assertThat(json).isEqualTo("{\"amendmentId\":\"11111111-1111-1111-1111-111111111111\","
                + "\"documentId\":\"11111111-1111-1111-1111-111111111111\","
                + "\"amendmentSequence\":null,\"amendmentNumber\":null,"
                + "\"digest\":\"" + DIGEST + "\","
                + "\"correlationId\":\"corr-1\","
                + "\"eventId\":\"22222222-2222-2222-2222-222222222222\"}");
        assertThat(json).doesNotContain("orderId", "sagaId", "commandId", "fulfilment", "prevHash", "seqNo");
    }

    @Test
    void assignedSequenceAndNumberAreIncluded() {
        String json = new AmendmentAuditPublication(
                DOCUMENT_ID, DOCUMENT_ID, 4, "C-100-A004", DIGEST, "corr-1", EVENT_ID).toJson();

        assertThat(json).contains("\"amendmentSequence\":4", "\"amendmentNumber\":\"C-100-A004\"");
    }

    @Test
    void envelopeCorrelationKeepsIdsThatFitTheOutboxColumn() {
        assertThat(AmendmentAuditOutbox.envelopeCorrelation("corr-1")).isEqualTo("corr-1");
        assertThat(AmendmentAuditOutbox.envelopeCorrelation("c".repeat(100))).hasSize(100);
        assertThat(AmendmentAuditOutbox.envelopeCorrelation("c".repeat(101))).isNull();
    }
}
