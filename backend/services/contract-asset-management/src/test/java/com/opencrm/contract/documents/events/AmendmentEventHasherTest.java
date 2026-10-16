package com.opencrm.contract.documents.events;

import com.opencrm.contract.domain.documents.ActorType;
import com.opencrm.contract.domain.documents.AmendmentDocumentEvent;
import com.opencrm.contract.domain.documents.AmendmentDocumentStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AmendmentEventHasherTest {

    private static final String CANONICAL = "{\"canonicalVersion\":1,"
            + "\"actorId\":\"seller-1\",\"actorRole\":\"seller\",\"actorType\":\"USER\","
            + "\"correlationId\":\"corr-1\",\"createdAt\":\"2026-10-16T12:00:00Z\",\"createdBy\":\"seller-1\","
            + "\"documentId\":\"11111111-1111-1111-1111-111111111111\","
            + "\"eventId\":\"22222222-2222-2222-2222-222222222222\","
            + "\"eventType\":\"AmendmentDrafted\",\"fromStatus\":null,\"occurredAt\":\"2026-10-16T12:00:00Z\","
            + "\"payloadDigest\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\","
            + "\"payloadRef\":\"payload:1\","
            + "\"prevHash\":\"0000000000000000000000000000000000000000000000000000000000000000\","
            + "\"reasonCode\":null,\"revisionNo\":null,\"seqNo\":1,\"sourceEventId\":null,"
            + "\"tenantId\":\"33333333-3333-3333-3333-333333333333\",\"toStatus\":\"DRAFT\",\"unitNo\":null}";

    private static final String GOLDEN_HASH = "0b168ee31e5dccdd0ed08a71758abb34f052ed5aae12eab1b0af3d99ad8b6a44";

    @Test
    void canonicalFormAndContentHashMatchTheGoldenVector() {
        AmendmentDocumentEvent event = sample(null);
        assertThat(AmendmentEventHasher.canonicalForm(event)).isEqualTo(CANONICAL);
        assertThat(AmendmentEventHasher.contentHash(event)).isEqualTo(GOLDEN_HASH);
        assertThat(AmendmentEventHasher.sha256(CANONICAL)).isEqualTo(GOLDEN_HASH);
        assertThat(CANONICAL).doesNotContain("recordedAt", "contentHash");
    }

    @Test
    void canonicalFormEscapesQuotesAndNewlines() {
        AmendmentDocumentEvent event = sample("say \"hi\"\n");
        String canonical = AmendmentEventHasher.canonicalForm(event);
        assertThat(canonical).contains("\"reasonCode\":\"say \\\"hi\\\"\\n\"");
        assertThat(AmendmentEventHasher.contentHash(event))
                .isEqualTo("3c7f313c259855a0d6c5ea91169b92ff84df5d67aafa80a6e385a0019e1122e1");
    }

    @Test
    void genesisPrevHashIsSixtyFourZeros() {
        assertThat(AmendmentEventHasher.GENESIS_PREV_HASH).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void changingPrevHashChangesTheContentHash() {
        AmendmentDocumentEvent first = sample(null);
        first.setContentHash(AmendmentEventHasher.contentHash(first));
        AmendmentDocumentEvent second = sample(null);
        second.setEventId(UUID.fromString("44444444-4444-4444-4444-444444444444"));
        second.setSeqNo(2L);
        second.setPrevHash(first.getContentHash());
        String linked = AmendmentEventHasher.contentHash(second);
        second.setPrevHash(AmendmentEventHasher.GENESIS_PREV_HASH);
        assertThat(AmendmentEventHasher.contentHash(second)).isNotEqualTo(linked);
    }

    private static AmendmentDocumentEvent sample(String reasonCode) {
        Instant at = Instant.parse("2026-10-16T12:00:00Z");
        return AmendmentDocumentEvent.builder()
                .eventId(UUID.fromString("22222222-2222-2222-2222-222222222222"))
                .tenantId(UUID.fromString("33333333-3333-3333-3333-333333333333"))
                .documentId(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .seqNo(1L)
                .eventType("AmendmentDrafted")
                .fromStatus(null)
                .toStatus(AmendmentDocumentStatus.DRAFT)
                .actorId("seller-1")
                .actorType(ActorType.USER)
                .actorRole("seller")
                .occurredAt(at)
                .recordedAt(at)
                .reasonCode(reasonCode)
                .payloadDigest("a".repeat(64))
                .payloadRef("payload:1")
                .prevHash(AmendmentEventHasher.GENESIS_PREV_HASH)
                .correlationId("corr-1")
                .createdAt(at)
                .createdBy("seller-1")
                .build();
    }
}
