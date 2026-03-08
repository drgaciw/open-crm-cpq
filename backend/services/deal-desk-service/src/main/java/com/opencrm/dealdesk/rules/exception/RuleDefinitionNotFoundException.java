package com.opencrm.dealdesk.rules.exception;

public class RuleDefinitionNotFoundException extends RuntimeException {

    public RuleDefinitionNotFoundException(Long id) {
        super("Rule definition not found: " + id);
    }
}
