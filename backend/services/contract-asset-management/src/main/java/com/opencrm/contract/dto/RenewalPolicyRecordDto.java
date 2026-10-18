package com.opencrm.contract.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Read model for one policy version. The tenant stays on the token and is not echoed. */
public record RenewalPolicyRecordDto(
        UUID id,
        String scopeKind,
        UUID scopeRef,
        int policyVersion,
        String mode,
        String basis,
        BigDecimal upliftPercentPoints,
        BigDecimal maxIncreasePct,
        BigDecimal minIncreasePct,
        BigDecimal minUnitAmount,
        Boolean notAboveList,
        Boolean notBelowExitNet,
        String priceAsOf,
        String retentionMarker,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String status) {
}
