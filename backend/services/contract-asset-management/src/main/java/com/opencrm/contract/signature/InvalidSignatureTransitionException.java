package com.opencrm.contract.signature;

/** The requested status transition is not valid from the envelope's current status (see {@link SignatureEnvelopeStatus}). */
public class InvalidSignatureTransitionException extends RuntimeException {
    public InvalidSignatureTransitionException(SignatureEnvelopeStatus from, SignatureEnvelopeStatus to) {
        super("Cannot transition signature envelope from " + from + " to " + to);
    }
}
