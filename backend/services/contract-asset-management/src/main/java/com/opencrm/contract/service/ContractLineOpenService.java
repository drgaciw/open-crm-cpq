package com.opencrm.contract.service;

import com.opencrm.contract.domain.documents.ContractHeader;
import com.opencrm.contract.domain.documents.ContractLine;
import com.opencrm.contract.domain.documents.ContractLineStatus;
import com.opencrm.contract.repository.documents.ContractHeaderRepository;
import com.opencrm.contract.repository.documents.ContractLineRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Opens {@link ContractLine} rows. Tenant is always copied from the parent contract (DRG-665 AC1).
 */
@Service
public class ContractLineOpenService {

    private final ContractHeaderRepository contracts;
    private final ContractLineRepository lines;

    public ContractLineOpenService(ContractHeaderRepository contracts, ContractLineRepository lines) {
        this.contracts = contracts;
        this.lines = lines;
    }

    @Transactional
    public ContractLine open(ContractLineOpenCommand command) {
        if (command == null || command.contractId() == null) {
            throw new IllegalArgumentException("contract id is required");
        }
        ContractHeader contract = contracts.findById(command.contractId())
                .orElseThrow(() -> new IllegalArgumentException("unknown contract: " + command.contractId()));

        // bodyTenantId is intentionally unused — tenant comes from the contract only.
        ContractLine line = ContractLine.builder()
                .tenantId(contract.getTenantId())
                .contractId(contract.getId())
                .lineNo(command.lineNo())
                .acceptedLineUuid(command.acceptedLineUuid())
                .quoteId(command.quoteId())
                .productExternalId(command.productExternalId())
                .productName(command.productName())
                .quantity(command.quantity() == null ? BigDecimal.ZERO : command.quantity())
                .status(ContractLineStatus.PENDING)
                .effectiveFrom(command.effectiveFrom())
                .effectiveTo(command.effectiveTo())
                .chargeType(command.chargeType())
                .createdBy(command.createdBy())
                .build();
        return lines.save(line);
    }
}
