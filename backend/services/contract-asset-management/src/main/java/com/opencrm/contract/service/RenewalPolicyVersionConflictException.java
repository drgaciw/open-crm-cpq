package com.opencrm.contract.service;

/** The unique policy version was already inserted by another writer. */
public class RenewalPolicyVersionConflictException extends RuntimeException {

    public RenewalPolicyVersionConflictException(String message) {
        super(message);
    }

    public RenewalPolicyVersionConflictException(Throwable cause) {
        super("renewal policy version conflict", cause);
    }
}
