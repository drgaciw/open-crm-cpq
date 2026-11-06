package com.opencrm.contract.service;

import com.opencrm.contract.domain.documents.ChargeType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Open a contract line under an existing contract.
 * {@code bodyTenantId} is accepted only so callers that smuggle a tenant can be proven ignored (DRG-665 AC1).
 */
public record ContractLineOpenCommand(
        UUID contractId,
        UUID bodyTenantId,
        int lineNo,
        String acceptedLineUuid,
        String quoteId,
        String productExternalId,
        String productName,
        BigDecimal quantity,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        ChargeType chargeType,
        String createdBy
) {
}
