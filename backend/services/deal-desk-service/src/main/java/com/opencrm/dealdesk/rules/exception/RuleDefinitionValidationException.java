package com.opencrm.dealdesk.rules.exception;

public class RuleDefinitionValidationException extends RuntimeException {

    public RuleDefinitionValidationException(String message) {
        super(message);
    }

    public RuleDefinitionValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
