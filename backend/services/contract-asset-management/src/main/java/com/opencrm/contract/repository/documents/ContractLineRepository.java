package com.opencrm.contract.repository.documents;

import com.opencrm.contract.domain.documents.ContractLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContractLineRepository extends JpaRepository<ContractLine, UUID> {

    List<ContractLine> findByTenantIdAndContractId(UUID tenantId, UUID contractId);

    Optional<ContractLine> findByTenantIdAndAcceptedLineUuid(UUID tenantId, String acceptedLineUuid);
}
