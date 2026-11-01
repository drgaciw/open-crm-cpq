package com.opencrm.common.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;

/**
 * The old asset after a Swap or Upgrade. The successor asset row is written first (DRG-655).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AssetSupersededEvent(
        String assetId,
        String accountId,
        String successorAssetId,
        String predecessorAssetId,
        String status,
        LocalDate effectiveDate,
        String sourceAmendmentId,
        String sourceApplyUnit) {
}
