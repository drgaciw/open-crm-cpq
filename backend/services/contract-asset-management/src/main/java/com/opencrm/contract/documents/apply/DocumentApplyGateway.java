package com.opencrm.contract.documents.apply;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** JDBC port for the advisory lock, subscription lookup, lines, and rate-period line trace. */
public interface DocumentApplyGateway {

    void lockDocument(UUID documentId);

    void lockSubscription(String subscriptionUuid);

    Optional<ApplyTarget> subscription(String subscriptionUuid);

    /**
     * Caller holds the subscription row lock. Only the exact document contract is accepted; successor
     * chains need a separate authorization contract. This check does not establish link provenance.
     * A matching contract row remains share-locked until commit. Null {@code contract_id} on the
     * subscription is {@link TargetContractMembership#UNSET}, not a mismatch and not an inferred link.
     */
    TargetContractMembership targetContractMembership(long subscriptionId, UUID contractId, UUID tenantId);

    /**
     * When {@code subscriptions.contract_id} is set, compare {@code subscriptions.tenant_id} to
     * {@code contracts.tenant_id}. Returns {@code TENANT_UNSET}, {@code TENANT_MISMATCH}, or null.
     */
    String contractTenantBlock(long subscriptionId);

    /**
     * When {@code subscriptions.contract_id} is set, compare {@code subscriptions.customer_account_id}
     * to {@code contracts.customer_account_id}. Returns {@code CUSTOMER_ACCOUNT_UNSET},
     * {@code CUSTOMER_ACCOUNT_MISMATCH}, or null.
     */
    String contractCustomerAccountBlock(long subscriptionId);

    List<ApplyLine> lines(UUID documentId, int revisionNo);

    void bindAmendmentLine(long periodId, UUID lineId, int revisionNo);
}
