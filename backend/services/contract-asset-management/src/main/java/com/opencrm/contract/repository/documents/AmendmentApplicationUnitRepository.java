package com.opencrm.contract.repository.documents;

import com.opencrm.contract.domain.documents.AmendmentApplicationUnit;
import com.opencrm.contract.domain.documents.AmendmentApplicationUnitKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AmendmentApplicationUnitRepository
        extends JpaRepository<AmendmentApplicationUnit, AmendmentApplicationUnitKey> {

    List<AmendmentApplicationUnit> findByDocumentIdOrderByDependencyOrderAscUnitNoAsc(UUID documentId);
}
