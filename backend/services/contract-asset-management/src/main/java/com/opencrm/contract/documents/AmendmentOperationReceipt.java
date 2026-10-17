package com.opencrm.contract.documents;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Idempotency receipt. Lookup happens before the expected-version check. */
@Entity
@Table(name = "amendment_operation_receipts")
public class AmendmentOperationReceipt {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "caller_id", nullable = false, length = 255)
    private String callerId;

    @Column(name = "operation", nullable = false, length = 80)
    private String operation;

    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "source_lifecycle_change_id", nullable = false)
    private UUID sourceLifecycleChangeId;

    @Column(name = "source_revision", nullable = false)
    private int sourceRevision;

    @Column(name = "request_digest", nullable = false, length = 64)
    private String requestDigest;

    @Column(name = "http_status", nullable = false)
    private int httpStatus;

    @Column(name = "response_json", nullable = false)
    private String responseJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 255)
    private String createdBy;

    public static AmendmentOperationReceipt create(
            UUID tenantId, String callerId, String operation, String idempotencyKey, UUID documentId,
            UUID sourceLifecycleChangeId, int sourceRevision, String requestDigest, String responseJson, String createdBy) {
        AmendmentOperationReceipt row = new AmendmentOperationReceipt();
        row.id = UUID.randomUUID();
        row.tenantId = tenantId;
        row.callerId = callerId;
        row.operation = operation;
        row.idempotencyKey = idempotencyKey;
        row.documentId = documentId;
        row.sourceLifecycleChangeId = sourceLifecycleChangeId;
        row.sourceRevision = sourceRevision;
        row.requestDigest = requestDigest;
        row.httpStatus = 201;
        row.responseJson = responseJson;
        row.createdAt = Instant.now();
        row.createdBy = createdBy;
        return row;
    }

    public String getRequestDigest() {
        return requestDigest;
    }

    public String getResponseJson() {
        return responseJson;
    }

    public UUID getDocumentId() {
        return documentId;
    }
}
