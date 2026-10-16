package com.opencrm.contract.repository.documents;

import com.opencrm.contract.domain.documents.AmendmentDocumentRevision;
import com.opencrm.contract.domain.documents.AmendmentRevisionKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AmendmentDocumentRevisionRepository
        extends JpaRepository<AmendmentDocumentRevision, AmendmentRevisionKey> {
}
