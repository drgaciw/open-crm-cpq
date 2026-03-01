package com.opencrm.contract.acl;

public class ErpRateLimitException extends RuntimeException {
    public ErpRateLimitException(String message) {
        super(message);
    }
}
