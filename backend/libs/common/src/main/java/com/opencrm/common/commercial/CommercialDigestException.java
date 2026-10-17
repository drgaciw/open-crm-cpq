package com.opencrm.common.commercial;

/** A commercial projection cannot be canonicalized under CAM-COMMERCIAL-1. */
public class CommercialDigestException extends IllegalArgumentException {

    public CommercialDigestException(String message) {
        super(message);
    }
}
