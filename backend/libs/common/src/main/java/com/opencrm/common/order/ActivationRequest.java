package com.opencrm.common.order;
import java.time.Instant;
public record ActivationRequest(String eventId, String operationKey, String orderId, String scopeId,
        String customerId, String quoteId, int acceptedRevision, AcceptedOrderSnapshot.Line line,
        String evidenceReference, @com.fasterxml.jackson.annotation.JsonFormat(shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING) Instant completedAt) {}
