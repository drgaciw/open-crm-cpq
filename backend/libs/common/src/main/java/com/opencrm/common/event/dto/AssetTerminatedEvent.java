package com.opencrm.common.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;

/** An installed asset removed or terminated by a ChangeLine (DRG-655). */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AssetTerminatedEvent(
        String assetId,
        String accountId,
        String contractLineId,
        Long subscriptionId,
        String productRef,
        String status,
        LocalDate effectiveDate,
        String sourceAmendmentId,
        String sourceApplyUnit) {
}
