package com.opencrm.common.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.opencrm.common.order.AcceptedOrderSnapshot;

/** Accepted immutable OMS pilot source, published through the registered outbox contract. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OmsAcceptedSnapshotEvent(String eventId, AcceptedOrderSnapshot snapshot, String snapshotHash) {}
