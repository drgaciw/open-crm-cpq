package com.opencrm.contract.documents.apply;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** JDBC port for the advisory lock, subscription lookup, lines, and rate-period line trace. */
public interface DocumentApplyGateway {

    void lockDocument(UUID documentId);

    void lockSubscription(String subscriptionUuid);

    Optional<ApplyTarget> subscription(String subscriptionUuid);

    List<ApplyLine> lines(UUID documentId, int revisionNo);

    void bindAmendmentLine(long periodId, UUID lineId, int revisionNo);
}
