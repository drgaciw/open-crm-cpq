package com.opencrm.quote.client;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** One CAM renewal policy version. No tenant field: the token supplies it. */
public record RenewalPolicyView(
        UUID id,
        String scopeKind,
        UUID scopeRef,
        int policyVersion,
        String mode,
        String basis,
        BigDecimal upliftPercentPoints,
        LocalDate effectiveFrom,
        LocalDate effectiveTo) {
}
