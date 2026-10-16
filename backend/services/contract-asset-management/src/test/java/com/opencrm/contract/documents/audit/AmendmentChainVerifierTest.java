package com.opencrm.contract.documents.audit;

import com.opencrm.contract.documents.events.AmendmentEventHasher;
import com.opencrm.contract.domain.documents.ActorType;
import com.opencrm.contract.domain.documents.AmendmentDocumentEvent;
import com.opencrm.contract.domain.documents.AmendmentDocumentStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AmendmentChainVerifierTest {

    private static final UUID DOCUMENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void intactChainHasNoBreak() {
        AmendmentDocumentEvent first = event(1, AmendmentEventHasher.GENESIS_PREV_HASH);
        AmendmentDocumentEvent second = event(2, first.getContentHash());
        assertThat(AmendmentChainVerifier.firstBreak(DOCUMENT_ID, List.of(second, first))).isEmpty();
    }

    @Test
    void emptyChainIsIntact() {
        assertThat(AmendmentChainVerifier.firstBreak(DOCUMENT_ID, List.of())).isEmpty();
    }

    @Test
    void tamperedContentHashReportsTheDocumentAndSequence() {
        AmendmentDocumentEvent first = event(1, AmendmentEventHasher.GENESIS_PREV_HASH);
        AmendmentDocumentEvent second = event(2, first.getContentHash());
        second.setContentHash("b".repeat(64));
        AmendmentDocumentEvent third = event(3, second.getContentHash());

        AmendmentChainBreak broken = AmendmentChainVerifier.firstBreak(
                DOCUMENT_ID, List.of(first, second, third)).orElseThrow();

        assertThat(broken.documentId()).isEqualTo(DOCUMENT_ID);
        assertThat(broken.seqNo()).isEqualTo(2);
        assertThat(broken.kind()).isEqualTo(AmendmentChainBreak.Kind.CONTENT_HASH_MISMATCH);
    }

    @Test
    void tamperedPreviousHashReportsTheBrokenLink() {
        AmendmentDocumentEvent first = event(1, AmendmentEventHasher.GENESIS_PREV_HASH);
        AmendmentDocumentEvent second = event(2, first.getContentHash());
        second.setPrevHash("c".repeat(64));
        second.setContentHash(AmendmentEventHasher.contentHash(second));

        AmendmentChainBreak broken = AmendmentChainVerifier.firstBreak(
                DOCUMENT_ID, List.of(first, second)).orElseThrow();

        assertThat(broken.documentId()).isEqualTo(DOCUMENT_ID);
        assertThat(broken.seqNo()).isEqualTo(2);
        assertThat(broken.kind()).isEqualTo(AmendmentChainBreak.Kind.BROKEN_PREV_HASH);
    }

    @Test
    void deletedMiddleRowReportsTheGap() {
        AmendmentDocumentEvent first = event(1, AmendmentEventHasher.GENESIS_PREV_HASH);
        AmendmentDocumentEvent third = event(3, "d".repeat(64));

        AmendmentChainBreak gap = AmendmentChainVerifier.firstBreak(
                DOCUMENT_ID, List.of(first, third)).orElseThrow();

        assertThat(gap.documentId()).isEqualTo(DOCUMENT_ID);
        assertThat(gap.seqNo()).isEqualTo(2);
        assertThat(gap.kind()).isEqualTo(AmendmentChainBreak.Kind.GAP);
    }

    @Test
    void wrongGenesisPreviousHashIsABrokenLink() {
        AmendmentDocumentEvent first = event(1, "e".repeat(64));

        AmendmentChainBreak broken = AmendmentChainVerifier.firstBreak(DOCUMENT_ID, List.of(first)).orElseThrow();

        assertThat(broken.seqNo()).isEqualTo(1);
        assertThat(broken.kind()).isEqualTo(AmendmentChainBreak.Kind.BROKEN_PREV_HASH);
    }

    private static AmendmentDocumentEvent event(long seqNo, String prevHash) {
        Instant at = Instant.parse("2026-10-16T12:00:00Z");
        AmendmentDocumentEvent event = AmendmentDocumentEvent.builder()
                .eventId(UUID.randomUUID())
                .tenantId(UUID.fromString("33333333-3333-3333-3333-333333333333"))
                .documentId(DOCUMENT_ID)
                .seqNo(seqNo)
                .eventType("AmendmentDrafted")
                .toStatus(AmendmentDocumentStatus.DRAFT)
                .actorId("seller-1")
                .actorType(ActorType.USER)
                .actorRole("seller")
                .occurredAt(at)
                .payloadDigest("a".repeat(64))
                .payloadRef("payload:" + seqNo)
                .prevHash(prevHash)
                .correlationId("corr-" + seqNo)
                .createdAt(at)
                .createdBy("s2-test")
                .build();
        event.setContentHash(AmendmentEventHasher.contentHash(event));
        return event;
    }
}
