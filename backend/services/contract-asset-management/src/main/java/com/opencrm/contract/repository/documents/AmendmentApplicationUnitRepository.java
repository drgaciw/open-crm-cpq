package com.opencrm.contract.repository.documents;

import com.opencrm.contract.domain.documents.AmendmentApplicationUnit;
import com.opencrm.contract.domain.documents.AmendmentApplicationUnitKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AmendmentApplicationUnitRepository
        extends JpaRepository<AmendmentApplicationUnit, AmendmentApplicationUnitKey> {
}
