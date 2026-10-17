package com.opencrm.contract.repository.documents;

import com.opencrm.contract.documents.AmendmentCommercialEnvelope;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AmendmentCommercialEnvelopeRepository extends JpaRepository<AmendmentCommercialEnvelope, UUID> {
}
