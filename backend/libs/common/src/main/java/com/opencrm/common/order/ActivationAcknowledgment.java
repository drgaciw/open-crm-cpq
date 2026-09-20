package com.opencrm.common.order;
import java.time.Instant;
public record ActivationAcknowledgment(String eventId, String operationKey, String orderId,
        String scopeId, String subscriptionId, String status, String evidenceReference, Instant occurredAt) {}
