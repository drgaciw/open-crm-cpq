package com.opencrm.contract.service;

import com.opencrm.contract.domain.documents.RenewalPolicyScopeKind;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Commercial body of one policy version. The tenant is not a field here; the service stamps the caller. */
public record RenewalPolicyDraft(
        RenewalPolicyScopeKind scopeKind,
        UUID scopeRef,
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
        Integer expectedVersion) {
}
