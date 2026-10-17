package com.opencrm.contract.repository.documents;

import com.opencrm.contract.documents.AmendmentOperationReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AmendmentOperationReceiptRepository extends JpaRepository<AmendmentOperationReceipt, UUID> {

    Optional<AmendmentOperationReceipt> findByTenantIdAndCallerIdAndOperationAndIdempotencyKey(
            UUID tenantId, String callerId, String operation, String idempotencyKey);

    Optional<AmendmentOperationReceipt> findByTenantIdAndDocumentIdAndSourceLifecycleChangeIdAndSourceRevisionAndOperation(
            UUID tenantId, UUID documentId, UUID sourceLifecycleChangeId, int sourceRevision, String operation);
}
