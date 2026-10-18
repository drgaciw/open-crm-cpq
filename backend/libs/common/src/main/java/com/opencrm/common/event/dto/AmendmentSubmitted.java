package com.opencrm.common.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/** S6 lifecycle schema, RESERVED, not emitted. documentStatus is the S3 name IN_REVIEW. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AmendmentSubmitted(
        UUID documentId,
        UUID contractId,
        String amendmentNumber,
        String documentStatus,
        Instant occurredAt,
        String correlationId,
        UUID eventId) {
}
