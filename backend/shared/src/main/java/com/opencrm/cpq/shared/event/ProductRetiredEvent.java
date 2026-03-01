package com.opencrm.cpq.shared.event;

import java.io.Serializable;
import java.time.Instant;

public record ProductRetiredEvent(
        String productId,
        Instant retiredAt,
        String reasonCode
) implements Serializable {
}
