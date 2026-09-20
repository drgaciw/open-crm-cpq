package com.opencrm.order.fulfillment;
import jakarta.validation.constraints.*;
import java.time.Instant;
public record EvidenceCommand(@Min(0) long expectedVersion,@NotBlank String operationKey,@NotBlank String reason,
    @NotBlank String lineId,@NotBlank String kind,@Min(1) int quantity,@NotBlank String externalReference,
    @NotNull Instant occurredAt,String allocationReference,String shipmentReference,Instant expiresAt) {}
