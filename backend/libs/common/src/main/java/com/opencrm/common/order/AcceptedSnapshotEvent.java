package com.opencrm.common.order;
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public record AcceptedSnapshotEvent(String eventId, AcceptedOrderSnapshot snapshot, String snapshotHash) {}
