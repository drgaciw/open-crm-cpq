package com.opencrm.contract.signature;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** API boundary DTO (ADR-006): the {@link SignatureEnvelope} entity is never returned from a controller. */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SignatureEnvelopeDto {

    private String envelopeId;
    private String aggregateType;
    private String aggregateId;
    private SignatureEnvelopeStatus status;
    private String voidReason;
    private Instant sentAt;
    private Instant completedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
