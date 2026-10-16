package com.opencrm.contract.documents;

import com.opencrm.common.event.KafkaInbox;
import com.opencrm.contract.ContractAssetManagementApplication;
import com.opencrm.contract.documents.audit.AmendmentAuditOutbox;
import com.opencrm.contract.documents.audit.AmendmentChainBreak;
import com.opencrm.contract.documents.audit.AmendmentChainVerifier;
import com.opencrm.contract.documents.events.AmendmentEventAppend;
import com.opencrm.contract.documents.events.AmendmentEventHasher;
import com.opencrm.contract.documents.events.AmendmentEventWriter;
import com.opencrm.contract.domain.documents.ActorType;
import com.opencrm.contract.domain.documents.AmendmentDocument;
import com.opencrm.contract.domain.documents.AmendmentDocumentEvent;
import com.opencrm.contract.domain.documents.AmendmentDocumentRevision;
import com.opencrm.contract.domain.documents.AmendmentDocumentStatus;
import com.opencrm.contract.domain.documents.AmendmentIntent;
import com.opencrm.contract.domain.documents.ContractHeader;
import com.opencrm.contract.domain.documents.ContractStatus;
import com.opencrm.contract.domain.documents.DocumentKind;
import com.opencrm.contract.domain.documents.EvidenceClass;
import com.opencrm.contract.domain.documents.InitiationTrigger;
import com.opencrm.contract.domain.documents.RevisionCloseStatus;
import com.opencrm.contract.domain.documents.SignaturePolicy;
import com.opencrm.contract.domain.documents.TermModel;
import com.opencrm.contract.repository.documents.AmendmentDocumentEventRepository;
import com.opencrm.contract.repository.documents.AmendmentDocumentRepository;
import com.opencrm.contract.repository.documents.AmendmentDocumentRevisionRepository;
import com.opencrm.contract.repository.documents.ContractHeaderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * DRG-580. PostgreSQL replay of the hash chain, same-transaction append, and outbox inbox dedup.
 * The relay stays off: nothing is sent to a broker.
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(classes = ContractAssetManagementApplication.class, properties = {
        "spring.profiles.active=test",
        "spring.kafka.listener.auto-startup=false",
        "opencrm.erp-sync.enabled=false",
        "opencrm.subscription-ramp.enabled=false",
        "opencrm.outbox.relay.enabled=false"})
class AmendmentEventChainIntegrationTest {

    private static final String DIGEST = "a".repeat(64);
    private static final String AUDIT_GROUP = "bc-20-01-audit";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withCreateContainerCmdModifier(command -> command.getHostConfig().withMemory(384L * 1024 * 1024));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> schemaUrl(POSTGRES));
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ContractHeaderRepository contracts;
    @Autowired AmendmentDocumentRepository documents;
    @Autowired AmendmentDocumentRevisionRepository revisions;
    @Autowired AmendmentDocumentEventRepository events;
    @Autowired AmendmentEventWriter writer;
    @Autowired AmendmentChainVerifier verifier;
    @Autowired KafkaInbox inbox;

    private TransactionTemplate tx;

    @BeforeEach
    void transaction() {
        tx = new TransactionTemplate(transactions);
        tx.setTimeout(20);
    }

    @Test
    void acS21_appendAssignsNextSequenceAndCommitsWithTheStateChange() {
        UUID documentId = seed(null, null);
        tx.executeWithoutResult(status -> {
            AmendmentDocument document = documents.findById(documentId).orElseThrow();
            document.setStatus(AmendmentDocumentStatus.IN_REVIEW);
            writer.append(command(documentId, "AmendmentDrafted", null,
                    AmendmentDocumentStatus.DRAFT, AmendmentDocumentStatus.IN_REVIEW));
        });
        tx.executeWithoutResult(status -> writer.append(command(documentId, "AmendmentSubmitted", UUID.randomUUID(),
                AmendmentDocumentStatus.IN_REVIEW, AmendmentDocumentStatus.IN_REVIEW)));

        List<AmendmentDocumentEvent> chain = chain(documentId);
        assertThat(documents.findById(documentId).orElseThrow().getStatus())
                .isEqualTo(AmendmentDocumentStatus.IN_REVIEW);
        assertThat(chain).extracting(AmendmentDocumentEvent::getSeqNo).containsExactly(1L, 2L);
        assertThat(chain.get(0).getPrevHash()).isEqualTo(AmendmentEventHasher.GENESIS_PREV_HASH);
        assertThat(chain.get(1).getPrevHash()).isEqualTo(chain.get(0).getContentHash());
        assertThat(chain.get(0).getContentHash()).isEqualTo(AmendmentEventHasher.contentHash(chain.get(0)));
        assertThat(verifier.verify(documentId)).isEmpty();
        assertThat(outboxPayload(documentId, chain.get(0).getEventId())).contains("\"amendmentSequence\":null");
    }

    @Test
    void acS22_concurrentAppendsStayGaplessAndUnique() throws Exception {
        UUID documentId = seed(null, null);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        for (int i = 0; i < 2; i++) {
            int n = i;
            pool.submit(() -> {
                ready.countDown();
                try {
                    if (!start.await(20, TimeUnit.SECONDS)) {
                        failure.compareAndSet(null, new AssertionError("start timeout"));
                        return;
                    }
                    tx.executeWithoutResult(status -> writer.append(command(
                            documentId, "Concurrent" + n, UUID.randomUUID(), null, null)));
                } catch (Throwable thrown) {
                    failure.compareAndSet(null, thrown);
                } finally {
                    done.countDown();
                }
            });
        }
        assertThat(ready.await(20, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();
        if (failure.get() != null) {
            throw new AssertionError("concurrent append failed", failure.get());
        }

        List<AmendmentDocumentEvent> chain = chain(documentId);
        assertThat(chain).extracting(AmendmentDocumentEvent::getSeqNo).containsExactly(1L, 2L);
        assertThat(chain.get(1).getPrevHash()).isEqualTo(chain.get(0).getContentHash());
        assertThat(verifier.verify(documentId)).isEmpty();
    }

    @Test
    void acS23_rolledBackStateChangeLeavesNoEvent() {
        UUID documentId = seed(null, null);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            documents.findById(documentId).orElseThrow().setStatus(AmendmentDocumentStatus.IN_REVIEW);
            writer.append(command(documentId, "RolledBack", UUID.randomUUID(),
                    AmendmentDocumentStatus.DRAFT, AmendmentDocumentStatus.IN_REVIEW));
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(documents.findById(documentId).orElseThrow().getStatus())
                .isEqualTo(AmendmentDocumentStatus.DRAFT);
        assertThat(countEvents(documentId)).isZero();
        assertThat(countOutbox(documentId)).isZero();
    }

    @Test
    void acS24_verifierReportsTamperedHashAndGap() {
        UUID tampered = seed(null, null);
        UUID gapped = seed(null, null);
        appendThree(tampered, "Tamper");
        appendThree(gapped, "Gap");
        assertThat(verifier.verify(tampered)).isEmpty();
        assertThat(verifier.verify(gapped)).isEmpty();

        String tamperHash = AmendmentEventHasher.sha256("tamper-" + tampered);
        withoutAppendOnlyTrigger(() -> jdbc.update(
                "update amendment_document_events set content_hash = ? where document_id = ? and seq_no = 2",
                tamperHash, tampered));
        withoutAppendOnlyTrigger(() -> jdbc.update(
                "delete from amendment_document_events where document_id = ? and seq_no = 2",
                gapped));

        AmendmentChainBreak hashBreak = verifier.verify(tampered).orElseThrow();
        assertThat(hashBreak.documentId()).isEqualTo(tampered);
        assertThat(hashBreak.seqNo()).isEqualTo(2);
        assertThat(hashBreak.kind()).isEqualTo(AmendmentChainBreak.Kind.CONTENT_HASH_MISMATCH);

        AmendmentChainBreak gap = verifier.verify(gapped).orElseThrow();
        assertThat(gap.documentId()).isEqualTo(gapped);
        assertThat(gap.seqNo()).isEqualTo(2);
        assertThat(gap.kind()).isEqualTo(AmendmentChainBreak.Kind.GAP);

        assertThat(verifier.verifyAll().breaks())
                .extracting(AmendmentChainBreak::documentId, AmendmentChainBreak::seqNo, AmendmentChainBreak::kind)
                .contains(
                        tuple(tampered, 2L, AmendmentChainBreak.Kind.CONTENT_HASH_MISMATCH),
                        tuple(gapped, 2L, AmendmentChainBreak.Kind.GAP));
    }

    @Test
    void acS25_outboxReplayKeepsStableEventIdAndInboxDedup() {
        UUID sourceEventId = UUID.randomUUID();
        UUID documentId = seed(4, "C-100-A004");
        AmendmentDocumentEvent appended = tx.execute(status -> writer.append(command(
                documentId, "AmendmentExecuted", sourceEventId, null, AmendmentDocumentStatus.EXECUTED)));
        AmendmentDocumentEvent again = tx.execute(status -> writer.append(command(
                documentId, "AmendmentExecuted", sourceEventId, null, AmendmentDocumentStatus.EXECUTED)));

        assertThat(again.getEventId()).isEqualTo(appended.getEventId());
        assertThat(countEvents(documentId)).isEqualTo(1);
        assertThat(countOutbox(documentId)).isEqualTo(1);

        String eventId = appended.getEventId().toString();
        var row = jdbc.queryForMap("""
                select uuid, payload, correlation_id, topic, event_type, published, status, aggregate_type
                from outbox_events where aggregate_id = ?
                """, documentId.toString());
        assertThat(row.get("uuid")).isEqualTo(eventId);
        assertThat(row.get("uuid")).isEqualTo(jdbc.queryForObject(
                "select uuid from outbox_events where aggregate_id = ?", String.class, documentId.toString()));
        assertThat(row.get("published")).isEqualTo(false);
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("topic")).isEqualTo(AmendmentAuditOutbox.TOPIC);
        assertThat(row.get("event_type")).isEqualTo(AmendmentAuditOutbox.TOPIC);
        assertThat(row.get("aggregate_type")).isEqualTo(AmendmentAuditOutbox.AGGREGATE_TYPE);
        assertThat(row.get("correlation_id")).isEqualTo("corr-AmendmentExecuted");
        String payload = (String) row.get("payload");
        assertThat(payload).contains(
                "\"amendmentId\":\"" + documentId + "\"",
                "\"documentId\":\"" + documentId + "\"",
                "\"amendmentSequence\":4",
                "\"amendmentNumber\":\"C-100-A004\"",
                "\"digest\":\"" + appended.getContentHash() + "\"",
                "\"correlationId\":\"corr-AmendmentExecuted\"",
                "\"eventId\":\"" + eventId + "\"");
        assertThat(payload).doesNotContain("orderId", "sagaId", "commandId", "fulfilment");

        Boolean firstDelivery = tx.execute(status -> inbox.claimKey(AUDIT_GROUP, eventId));
        Boolean redelivery = tx.execute(status -> inbox.claimKey(AUDIT_GROUP, eventId));
        assertThat(firstDelivery).isTrue();
        assertThat(redelivery).isFalse();
        assertThat(jdbc.queryForObject(
                "select count(*) from processed_events where consumer_group = ? and event_id = ?",
                Long.class, AUDIT_GROUP, eventId)).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "select published from outbox_events where uuid = ?", Boolean.class, eventId)).isFalse();
    }

    private void appendThree(UUID documentId, String prefix) {
        for (int seq = 1; seq <= 3; seq++) {
            int n = seq;
            tx.executeWithoutResult(status -> writer.append(command(
                    documentId, prefix + n, UUID.randomUUID(), null, null)));
        }
    }

    private void withoutAppendOnlyTrigger(Runnable change) {
        jdbc.execute("alter table amendment_document_events disable trigger trg_amendment_document_events_append_only");
        try {
            change.run();
        } finally {
            jdbc.execute("alter table amendment_document_events enable trigger trg_amendment_document_events_append_only");
        }
    }

    private List<AmendmentDocumentEvent> chain(UUID documentId) {
        return events.findAll().stream()
                .filter(event -> documentId.equals(event.getDocumentId()))
                .sorted(Comparator.comparing(AmendmentDocumentEvent::getSeqNo))
                .toList();
    }

    private String outboxPayload(UUID documentId, UUID eventId) {
        return jdbc.queryForObject(
                "select payload from outbox_events where aggregate_id = ? and uuid = ?",
                String.class, documentId.toString(), eventId.toString());
    }

    private long countEvents(UUID documentId) {
        Long count = jdbc.queryForObject(
                "select count(*) from amendment_document_events where document_id = ?", Long.class, documentId);
        return count == null ? 0 : count;
    }

    private long countOutbox(UUID documentId) {
        Long count = jdbc.queryForObject(
                "select count(*) from outbox_events where aggregate_id = ?", Long.class, documentId.toString());
        return count == null ? 0 : count;
    }

    private AmendmentEventAppend command(UUID documentId, String eventType, UUID sourceEventId,
                                         AmendmentDocumentStatus fromStatus, AmendmentDocumentStatus toStatus) {
        return new AmendmentEventAppend(
                documentId,
                eventType,
                fromStatus,
                toStatus,
                null,
                null,
                "seller-1",
                ActorType.USER,
                "seller",
                Instant.parse("2026-10-16T12:00:00Z"),
                null,
                DIGEST,
                "payload:" + eventType,
                sourceEventId,
                "corr-" + eventType,
                "s2-test");
    }

    private UUID seed(Integer amendmentSeq, String amendmentNumber) {
        UUID tenantId = UUID.randomUUID();
        UUID contractId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        tx.executeWithoutResult(status -> {
            contracts.save(ContractHeader.builder()
                    .id(contractId)
                    .tenantId(tenantId)
                    .contractNumber("C-" + contractId.toString().substring(0, 8))
                    .customerAccountId(UUID.randomUUID())
                    .termModel(TermModel.TERMED)
                    .startDate(LocalDate.parse("2026-01-01"))
                    .endDate(LocalDate.parse("2026-12-31"))
                    .status(ContractStatus.ACTIVE)
                    .lastExecutedSeq(0)
                    .createdBy("s2-test")
                    .build());
            documents.save(AmendmentDocument.builder()
                    .id(documentId)
                    .tenantId(tenantId)
                    .contractId(contractId)
                    .lifecycleChangeId(UUID.randomUUID())
                    .lifecycleChangeRevision(1)
                    .intent(AmendmentIntent.AMEND)
                    .initiationTrigger(InitiationTrigger.MANUAL)
                    .provisionalNumber("P-" + documentId.toString().substring(0, 8))
                    .amendmentSeq(amendmentSeq)
                    .amendmentNumber(amendmentNumber)
                    .documentKind(DocumentKind.AMENDMENT)
                    .status(AmendmentDocumentStatus.DRAFT)
                    .currentRevisionNo(1)
                    .signaturePolicy(SignaturePolicy.NOT_REQUIRED)
                    .actionDate(LocalDate.parse("2026-06-01"))
                    .contractEffectiveDate(LocalDate.parse("2026-06-01"))
                    .currencyCode("USD")
                    .tcvDelta(BigDecimal.ZERO)
                    .arrDelta(BigDecimal.ZERO)
                    .oneTimeTotal(BigDecimal.ZERO)
                    .creditTotal(BigDecimal.ZERO)
                    .contentDigest(DIGEST)
                    .evidenceClass(EvidenceClass.STANDARD)
                    .legalHold(false)
                    .deleted(false)
                    .createdBy("s2-test")
                    .build());
            revisions.save(AmendmentDocumentRevision.builder()
                    .documentId(documentId)
                    .revisionNo(1)
                    .tenantId(tenantId)
                    .lifecycleChangeRevision(1)
                    .targetQuoteId(UUID.randomUUID())
                    .contentDigest(DIGEST)
                    .inputsSnapshotRef("snap")
                    .statusAtClose(RevisionCloseStatus.OPEN)
                    .createdBy("s2-test")
                    .build());
        });
        return documentId;
    }

    private static String schemaUrl(PostgreSQLContainer<?> postgres) {
        String url = postgres.getJdbcUrl();
        return url + (url.contains("?") ? "&" : "?") + "currentSchema=contract";
    }
}
