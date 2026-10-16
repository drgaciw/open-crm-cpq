package com.opencrm.common.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/** S2 audit chain notification. digest is the event content hash, not the document payload digest. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.ALWAYS)
public record AmendmentAuditEvent(UUID amendmentId, UUID documentId, Integer amendmentSequence,
                                 String amendmentNumber, String digest, String correlationId, UUID eventId) {
}
