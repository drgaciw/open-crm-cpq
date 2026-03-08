package com.opencrm.dealdesk.exception;

public class SalesforceIntegrationException extends RuntimeException {

    public SalesforceIntegrationException(String message) {
        super(message);
    }

    public SalesforceIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
