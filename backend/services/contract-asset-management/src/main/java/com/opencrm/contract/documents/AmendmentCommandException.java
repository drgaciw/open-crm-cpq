package com.opencrm.contract.documents;

import com.opencrm.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

/** Business refusal. {@code audit} is written only after the business transaction rolls back. */
public class AmendmentCommandException extends BusinessException {

    private final String ruleId;
    private final UUID documentId;
    private final String eventType;
    private final boolean audit;
    private final CommandContext context;

    public AmendmentCommandException(String message, HttpStatus status, String errorCode, String ruleId,
                                     UUID documentId, String eventType, boolean audit, CommandContext context) {
        super(message, status, errorCode);
        this.ruleId = ruleId;
        this.documentId = documentId;
        this.eventType = eventType;
        this.audit = audit;
        this.context = context;
    }

    public String getRuleId() {
        return ruleId;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public String getEventType() {
        return eventType;
    }

    public boolean isAudit() {
        return audit;
    }

    public CommandContext getContext() {
        return context;
    }

    static AmendmentCommandException rule(UUID documentId, String ruleId, String message, CommandContext context) {
        return new AmendmentCommandException(ruleId + ": " + message, HttpStatus.UNPROCESSABLE_ENTITY,
                "VALIDATION_REJECTED", ruleId, documentId, "VALIDATION_REJECTED", documentId != null, context);
    }

    static AmendmentCommandException conflict(UUID documentId, String code, String message, CommandContext context) {
        String event = "VERSION_CONFLICT".equals(code) || "IMMUTABLE_EXECUTED".equals(code) || "ILLEGAL_TRANSITION".equals(code)
                ? "AMENDMENT_REVISION_REJECTED" : "VALIDATION_REJECTED";
        boolean revisionRefusal = "AMENDMENT_REVISION_REJECTED".equals(event);
        return new AmendmentCommandException(message, HttpStatus.CONFLICT, code, null, documentId,
                revisionRefusal ? event : "VALIDATION_REJECTED", documentId != null, context);
    }

    static AmendmentCommandException signature(UUID documentId, CommandContext context) {
        return new AmendmentCommandException("signature policy is not supported in this slice",
                HttpStatus.UNPROCESSABLE_ENTITY, "SIGNATURE_NOT_SUPPORTED_IN_SLICE", null, documentId,
                "VALIDATION_REJECTED", documentId != null, context);
    }
}
