package com.opencrm.contract.documents.apply;

import java.util.Map;
import java.util.UUID;

/**
 * One explicit apply call. {@code tenantId} is the verified token {@code tid}.
 * A null tenant is a missing-tid refusal. Pricebook pins are the values observed at apply time.
 */
public record ApplyCommand(
        UUID amendmentId,
        UUID tenantId,
        String callerId,
        String authorities,
        String correlationId,
        Map<Integer, String> observedPricebookByUnit) {

    public ApplyCommand {
        observedPricebookByUnit = observedPricebookByUnit == null ? Map.of() : Map.copyOf(observedPricebookByUnit);
        callerId = callerId == null || callerId.isBlank() ? "unknown" : callerId;
        authorities = authorities == null ? "" : authorities;
        correlationId = correlationId == null || correlationId.isBlank() ? UUID.randomUUID().toString() : correlationId;
    }
}
