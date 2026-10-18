package com.opencrm.pricing.renewal;

/**
 * Preview of one renewal resolution. {@code preview} is always true: this is not a confirmed commercial price.
 */
public record RenewalResolveResponse(boolean preview, String apiVersion, RenewalUpliftResult resolution) {

    public static final String API_VERSION = "v1";

    public RenewalResolveResponse {
        if (!preview) {
            throw new IllegalArgumentException("preview must be true");
        }
        if (apiVersion == null || apiVersion.isBlank()) {
            throw new IllegalArgumentException("apiVersion is required");
        }
    }

    public static RenewalResolveResponse previewOf(RenewalUpliftResult resolution) {
        return new RenewalResolveResponse(true, API_VERSION, resolution);
    }
}
