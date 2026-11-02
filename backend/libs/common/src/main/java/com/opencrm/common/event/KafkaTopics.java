package com.opencrm.common.event;

/**
 * Kafka topic constants for CPQ event-driven architecture.
 * All cross-service state propagation uses these topics with Transactional Outbox pattern.
 *
 * <p>ADR-018 D5: every constant here has an {@link EventContractRegistry} entry, PUBLISHED or RESERVED, and
 * {@code EventContractRegistryTest} keeps the two in step. A new topic is added with its contract. The 15 names that had
 * neither a producer nor a contract (for example {@code cpq.quote.created} and the AMCP event mesh topics of the held
 * agent Kafka work, #241) were removed on 2026-09-22. They come back as RESERVED contracts when their wave is planned.
 */
public final class KafkaTopics {

    private KafkaTopics() {
        // Utility class
    }

    // Product Catalog Events
    public static final String PRODUCT_STATUS_RETIRED = "cpq.product.status.retired";
    public static final String PRODUCT_UPDATED = "cpq.product.updated";
    public static final String BUNDLE_STRUCTURE_CHANGED = "cpq.product.bundle.structure.changed";

    // Configuration Events
    public static final String CONFIGURATION_COMMITTED = "cpq.configuration.committed";

    // Pricing Events
    public static final String DISCOUNT_APPROVAL_REQUIRED = "cpq.discount.approval.required";
    public static final String PRICE_CALCULATED = "cpq.price.calculated";
    public static final String RULE_SET_PUBLISHED = "cpq.pricing.ruleset.published";

    // Quote Events
    public static final String QUOTE_STATUS_ACCEPTED = "cpq.quote.status.accepted";
    public static final String QUOTE_APPROVAL_REJECTED = "cpq.quote.approval.rejected";
    public static final String RAMP_APPROVAL_BINDING_CHANGED = "cpq.quote.ramp.approval.binding.changed";

    // Approval Events
    public static final String APPROVAL_STEP_DECIDED = "cpq.approval.step.decided";
    public static final String APPROVAL_OUTCOME_COMPLETED = "cpq.approval.outcome.completed";

    // Contract/Asset Events
    public static final String AMENDMENT_DOCUMENT_AUDIT = "cpq.amendment.document.audit";
    public static final String SUBSCRIPTION_RAMP_APPLIED = "cpq.subscription.ramp.applied";
    public static final String SUBSCRIPTION_RENEWAL_ELIGIBLE = "cpq.subscription.renewal.eligible";
    public static final String CONTRACT_EXECUTED = "cpq.contract.executed";

    // CRM domain events (DRG-510). Appended so existing constants keep their order.
    public static final String ORGANIZATION_CREATED = "cpq.crm.organization.created";
    public static final String CONTACT_CREATED = "cpq.crm.contact.created";
    public static final String DEAL_STAGE_CHANGED = "cpq.crm.deal.stage.changed";
    public static final String DEAL_CLOSED = "cpq.crm.deal.closed";

    /** DRG-654. Installed-base asset created from an activated contract line. */
    public static final String ASSET_CREATED = "cpq.asset.created";
    /** DRG-654. Audit of an asset creation. */
    public static final String ASSET_AUDIT = "cpq.asset.audit";
    /** DRG-655. ChangeLine Update applied to an installed asset. */
    public static final String ASSET_AMENDED = "cpq.asset.amended";
    /** DRG-655. ChangeLine Remove or Terminate applied to an installed asset. */
    public static final String ASSET_TERMINATED = "cpq.asset.terminated";
    /** DRG-655. ChangeLine Swap or Upgrade. The successor row exists before this event. */
    public static final String ASSET_SUPERSEDED = "cpq.asset.superseded";

    /** DRG-482 / BC-12-02. Account parent link created or changed. */
    public static final String HIERARCHY_CHANGED = "cpq.crm.account.hierarchy-changed";

    /** DRG-483. Territory and segment assigned to an account (organization). */
    public static final String TERRITORY_ASSIGNED = "cpq.crm.account.territory-assigned";

    /** DRG-484. One organization merged into an explicitly chosen survivor. */
    public static final String ACCOUNTS_MERGED = "cpq.crm.account.merged";
}
