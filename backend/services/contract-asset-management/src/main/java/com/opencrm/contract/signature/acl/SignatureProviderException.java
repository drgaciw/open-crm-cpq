package com.opencrm.contract.signature.acl;

/** Wraps any failure from the e-sign provider, with provider-specific detail kept out of its message where possible. */
public class SignatureProviderException extends RuntimeException {
    public SignatureProviderException(String message) {
        super(message);
    }

    public SignatureProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
