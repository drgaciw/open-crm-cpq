package com.opencrm.common.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Emitted by approval-routing when a deal desk records a time-boxed exception for one quote version and
 * one commercial policy (BC-04-05, DRG-445).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExceptionRecordedEvent(
        Long exceptionId,
        String quoteId,
        Long quoteVersion,
        Long policyId,
        String tenantId,
        String recordedBy,
        String justification,
        Instant expiresAt) {
}
