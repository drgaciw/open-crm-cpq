package com.opencrm.contract.repository.documents;

import com.opencrm.contract.domain.documents.DocumentApplyLedgerEntry;
import com.opencrm.contract.domain.documents.DocumentApplyLedgerEntry.Key;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentApplyLedgerRepository extends JpaRepository<DocumentApplyLedgerEntry, Key> {

    Optional<DocumentApplyLedgerEntry> findByTenantIdAndDocumentIdAndUnitNo(UUID tenantId, UUID documentId, Integer unitNo);
}
