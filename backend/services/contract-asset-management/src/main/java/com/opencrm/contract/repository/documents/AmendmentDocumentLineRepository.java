package com.opencrm.contract.repository.documents;

import com.opencrm.contract.domain.documents.AmendmentDocumentLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AmendmentDocumentLineRepository extends JpaRepository<AmendmentDocumentLine, UUID> {
}
