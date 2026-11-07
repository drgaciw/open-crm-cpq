package com.opencrm.contract.documents.apply;

/**
 * Exact document-contract ownership of an apply target. Apply never infers a contract from an
 * asset or another chain; {@link #UNSET} means the subscription has no {@code contract_id}.
 */
public enum TargetContractMembership {
    /** Subscription contract_id equals the document contract in the document tenant. */
    MATCHED,
    /** Subscription exists but contract_id is null. */
    UNSET,
    /** Linked contract differs, tenant differs, or the membership row cannot be admitted. */
    MISMATCH
}
