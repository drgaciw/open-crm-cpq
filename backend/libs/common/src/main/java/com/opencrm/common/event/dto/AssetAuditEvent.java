package com.opencrm.common.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/** Audit of one asset creation. digest covers the created identity, not a second business event. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AssetAuditEvent(
        String assetId,
        String accountId,
        String contractLineId,
        Long subscriptionId,
        String entitlementId,
        String digest,
        String correlationId,
        UUID eventId) {
}
