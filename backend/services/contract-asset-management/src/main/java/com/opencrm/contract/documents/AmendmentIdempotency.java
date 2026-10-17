package com.opencrm.contract.documents;

/** Receipt decision. Callers invoke this before the expected-version check. */
public final class AmendmentIdempotency {

    public enum Decision {
        PROCEED,
        REPLAY,
        CONFLICT
    }

    private AmendmentIdempotency() {
    }

    public static Decision decide(String storedRequestDigest, String incomingRequestDigest) {
        if (storedRequestDigest == null) {
            return Decision.PROCEED;
        }
        if (storedRequestDigest.equals(incomingRequestDigest)) {
            return Decision.REPLAY;
        }
        return Decision.CONFLICT;
    }
}
