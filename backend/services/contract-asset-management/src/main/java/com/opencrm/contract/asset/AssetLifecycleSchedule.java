package com.opencrm.contract.asset;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;

/**
 * Enqueues lifecycle changes. A repeated {@code dedupeKey} returns the row already stored.
 * Callers that enqueue from production paths arrive in later stories; this one only stores.
 */
@Service
public class AssetLifecycleSchedule {

    private final AssetLifecycleChangeRepository changes;
    private final TransactionOperations inserts;

    @Autowired
    public AssetLifecycleSchedule(AssetLifecycleChangeRepository changes, PlatformTransactionManager transactions) {
        this(changes, requiresNew(transactions));
    }

    AssetLifecycleSchedule(AssetLifecycleChangeRepository changes, TransactionOperations inserts) {
        this.changes = changes;
        this.inserts = inserts;
    }

    public AssetLifecycleChange enqueue(Enqueue command) {
        throw new UnsupportedOperationException("lifecycle schedule enqueue is not implemented");
    }

    private AssetLifecycleChange insert(Enqueue command) {
        try {
            return inserts.execute(status -> {
                AssetLifecycleChange row = command.newPending(changes.nextSequence());
                return changes.saveAndFlush(row);
            });
        } catch (DataIntegrityViolationException ex) {
            return changes.findByDedupeKey(command.dedupeKey()).orElseThrow(() -> ex);
        }
    }

    private static TransactionTemplate requiresNew(PlatformTransactionManager transactions) {
        TransactionTemplate template = new TransactionTemplate(transactions);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    /**
     * One change to store. The compact constructor rejects values the table cannot hold.
     * A blank payload is stored as {@code {}}.
     */
    public record Enqueue(
            String dedupeKey,
            AssetLifecycleChangeKind kind,
            String assetUuid,
            long subscriptionId,
            String contractLineId,
            String accountId,
            LocalDate effectiveDate,
            AssetLifecycleChange.Source source,
            String sourceAmendmentId,
            String sourceApplyUnit,
            String correlationId,
            String payload) {

        public Enqueue {
            dedupeKey = require(dedupeKey, 200, "dedupeKey");
            if (kind == null) {
                throw new IllegalArgumentException("kind is required");
            }
            assetUuid = optional(assetUuid, 36, "assetUuid");
            contractLineId = require(contractLineId, 64, "contractLineId");
            accountId = require(accountId, 255, "accountId");
            if (effectiveDate == null) {
                throw new IllegalArgumentException("effectiveDate is required");
            }
            if (source == null) {
                throw new IllegalArgumentException("source is required");
            }
            sourceAmendmentId = optional(sourceAmendmentId, 36, "sourceAmendmentId");
            sourceApplyUnit = optional(sourceApplyUnit, 64, "sourceApplyUnit");
            correlationId = optional(correlationId, 100, "correlationId");
            if (payload == null || payload.isBlank()) {
                payload = "{}";
            }
        }

        AssetLifecycleChange newPending(long sequenceNo) {
            return AssetLifecycleChange.builder()
                    .dedupeKey(dedupeKey)
                    .kind(kind)
                    .assetUuid(assetUuid)
                    .subscriptionId(subscriptionId)
                    .contractLineId(contractLineId)
                    .accountId(accountId)
                    .effectiveDate(effectiveDate)
                    .sequenceNo(sequenceNo)
                    .sourceType(source)
                    .sourceAmendmentId(sourceAmendmentId)
                    .sourceApplyUnit(sourceApplyUnit)
                    .correlationId(correlationId)
                    .payload(payload)
                    .status(AssetLifecycleChangeStatus.PENDING)
                    .attempts(0)
                    .build();
        }

        private static String require(String value, int max, String name) {
            if (value == null || value.isBlank() || value.length() > max) {
                throw new IllegalArgumentException(name + " must be 1.." + max + " characters");
            }
            return value;
        }

        private static String optional(String value, int max, String name) {
            if (value == null || value.isBlank()) {
                return null;
            }
            if (value.length() > max) {
                throw new IllegalArgumentException(name + " exceeds " + max + " characters");
            }
            return value;
        }
    }
}
