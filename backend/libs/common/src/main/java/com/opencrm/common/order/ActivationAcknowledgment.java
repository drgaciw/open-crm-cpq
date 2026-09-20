package com.opencrm.common.order;
import java.time.Instant;
public record ActivationAcknowledgment(String eventId, String operationKey, String orderId,
        String scopeId, String subscriptionId, String status, String evidenceReference, @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) Instant occurredAt) {}
