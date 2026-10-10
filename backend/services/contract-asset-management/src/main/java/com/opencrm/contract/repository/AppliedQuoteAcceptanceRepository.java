package com.opencrm.contract.repository;

import com.opencrm.contract.domain.AppliedQuoteAcceptance;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AppliedQuoteAcceptanceRepository extends JpaRepository<AppliedQuoteAcceptance, Long> {
    Optional<AppliedQuoteAcceptance> findByAcceptedQuoteId(String acceptedQuoteId);
}
