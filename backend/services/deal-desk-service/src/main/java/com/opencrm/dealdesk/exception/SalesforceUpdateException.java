package com.opencrm.dealdesk.exception;

public class SalesforceUpdateException extends SalesforceIntegrationException {

    public SalesforceUpdateException(String message) {
        super(message);
    }

    public SalesforceUpdateException(String message, Throwable cause) {
        super(message, cause);
    }
}
