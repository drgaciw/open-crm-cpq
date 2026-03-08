package com.opencrm.dealdesk.exception;

public class DealNotFoundException extends RuntimeException {

    public DealNotFoundException(Long id) {
        super("Deal not found: " + id);
    }
}
