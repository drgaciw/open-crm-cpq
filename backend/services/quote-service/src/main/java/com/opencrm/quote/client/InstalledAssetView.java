package com.opencrm.quote.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Installed-base row from CAM {@code GET /api/v1/assets?accountId=&asOf=}.
 * Quote-service does not import contract-asset-management types.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InstalledAssetView(
        String assetId,
        String accountId,
        String contractLineId,
        Long subscriptionId,
        String productRef,
        BigDecimal quantity,
        LocalDate effectiveStart,
        LocalDate effectiveEnd,
        String status) {
}
