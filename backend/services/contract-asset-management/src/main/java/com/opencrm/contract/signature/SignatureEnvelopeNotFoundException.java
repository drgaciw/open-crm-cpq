package com.opencrm.contract.signature;

/** No signature envelope exists for the given id. */
public class SignatureEnvelopeNotFoundException extends RuntimeException {
    public SignatureEnvelopeNotFoundException(String envelopeUuid) {
        super("No signature envelope found for id " + envelopeUuid);
    }
}
