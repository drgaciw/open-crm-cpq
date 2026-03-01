package com.opencrm.contract.acl;

public class ErpSyncException extends RuntimeException {
    public ErpSyncException(String message) {
        super(message);
    }
    
    public ErpSyncException(String message, Throwable cause) {
        super(message, cause);
    }
}
