package com.opencrm.order.advisory;

import java.time.Instant;

/** Local binding, deliberately separate from the privacy-safe state sent upstream. */
public record OrderAdvisorySnapshot(
        String scopeId, String customerId, String orderId, long orderVersion,
        String exceptionId, long exceptionVersion, Instant capturedAt,
        OrderAdvisoryFeatures.Facts facts) { }
