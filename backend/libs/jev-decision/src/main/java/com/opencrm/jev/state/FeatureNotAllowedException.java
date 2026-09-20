package com.opencrm.jev.state;

/** A feature was offered that the use case schema does not allow, or its value is not a safe shape. */
public class FeatureNotAllowedException extends IllegalArgumentException {

    public FeatureNotAllowedException(String message) {
        super(message);
    }
}
