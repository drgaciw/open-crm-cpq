package com.opencrm.common.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Asset created when an accepted quote activates a contract line (DRG-653 aggregate, DRG-654).
 * {@code accountId} is the aggregate's account field. {@code entitlementId} is null when nothing exists to link.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AssetCreatedEvent(
        String assetId,
        String accountId,
        String contractLineId,
        Long subscriptionId,
        String entitlementId,
        String productRef,
        BigDecimal quantity,
        String status,
        LocalDate effectiveStart,
        LocalDate effectiveEnd) {
}
