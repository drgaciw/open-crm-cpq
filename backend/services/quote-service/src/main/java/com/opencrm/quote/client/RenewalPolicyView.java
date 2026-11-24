package com.opencrm.quote.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** One CAM renewal policy version. No tenant field: the token supplies it. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RenewalPolicyView(
        UUID id,
        String scopeKind,
        UUID scopeRef,
        int policyVersion,
        String mode,
        String basis,
        BigDecimal upliftPercentPoints,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        BigDecimal maxIncreasePct,
        BigDecimal maxDecreasePct,
        BigDecimal minIncreasePct,
        BigDecimal minUnitAmount,
        Boolean notAboveList,
        String priceAsOf) {

    public RenewalPolicyView(UUID id, String scopeKind, UUID scopeRef, int policyVersion, String mode,
                             String basis, BigDecimal upliftPercentPoints, LocalDate effectiveFrom,
                             LocalDate effectiveTo) {
        this(id, scopeKind, scopeRef, policyVersion, mode, basis, upliftPercentPoints, effectiveFrom, effectiveTo,
                null, null, null, null, null, null);
    }
}
