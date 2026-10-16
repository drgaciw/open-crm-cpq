package com.opencrm.contract.repository.documents;

import com.opencrm.contract.domain.documents.AmendmentDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AmendmentDocumentRepository extends JpaRepository<AmendmentDocument, UUID> {

    Optional<AmendmentDocument> findByLifecycleChangeId(UUID lifecycleChangeId);
}
