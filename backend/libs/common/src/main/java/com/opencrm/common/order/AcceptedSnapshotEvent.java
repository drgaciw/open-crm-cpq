package com.opencrm.common.order;
public record AcceptedSnapshotEvent(String eventId, AcceptedOrderSnapshot snapshot, String snapshotHash) {}
