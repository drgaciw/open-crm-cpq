package com.opencrm.contract.signature;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SignatureEnvelopeRepository extends JpaRepository<SignatureEnvelope, Long> {

    Optional<SignatureEnvelope> findByUuid(String uuid);
}
