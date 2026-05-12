package com.opencrm.common.event.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Event published when product catalog lifecycle or bundle structure changes.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductCatalogLifecycleEvent {
    private String aggregateId;
    private String changeType;
    private Instant changedAt;
}
