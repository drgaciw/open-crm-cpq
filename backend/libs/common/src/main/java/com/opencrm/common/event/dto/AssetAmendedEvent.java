package com.opencrm.common.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;

/** An installed asset changed by a ChangeLine Update (DRG-655). */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AssetAmendedEvent(
        String assetId,
        String accountId,
        String contractLineId,
        Long subscriptionId,
        String entitlementId,
        String productRef,
        BigDecimal quantity,
        String status,
        LocalDate effectiveStart,
        LocalDate effectiveEnd,
        LocalDate effectiveDate,
        String sourceAmendmentId,
        String sourceApplyUnit) {
}
