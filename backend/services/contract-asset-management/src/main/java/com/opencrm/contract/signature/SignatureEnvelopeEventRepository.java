package com.opencrm.contract.signature;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SignatureEnvelopeEventRepository extends JpaRepository<SignatureEnvelopeEvent, Long> {

    boolean existsByEnvelopeIdAndProviderEventId(Long envelopeId, String providerEventId);
}
