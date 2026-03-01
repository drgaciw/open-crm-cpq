package com.opencrm.common.event;

/**
 * Kafka topic constants for CPQ event-driven architecture.
 * All cross-service state propagation uses these topics with Transactional Outbox pattern.
 */
public final class KafkaTopics {
    
    private KafkaTopics() {
        // Utility class
    }
    
    // Product Catalog Events
    public static final String PRODUCT_STATUS_RETIRED = "cpq.product.status.retired";
    public static final String PRODUCT_CREATED = "cpq.product.created";
    public static final String PRODUCT_UPDATED = "cpq.product.updated";
    
    // Pricing Events
    public static final String DISCOUNT_APPROVAL_REQUIRED = "cpq.discount.approval.required";
    public static final String PRICE_CALCULATED = "cpq.price.calculated";
    
    // Quote Events
    public static final String QUOTE_STATUS_ACCEPTED = "cpq.quote.status.accepted";
    public static final String QUOTE_CREATED = "cpq.quote.created";
    public static final String QUOTE_UPDATED = "cpq.quote.updated";
    public static final String QUOTE_APPROVAL_REJECTED = "cpq.quote.approval.rejected";
    
    // Approval Events
    public static final String APPROVAL_APPROVED = "cpq.approval.approved";
    public static final String APPROVAL_REJECTED = "cpq.approval.rejected";
    
    // Document Events
    public static final String DOCUMENT_GENERATION_REQUESTED = "cpq.document.generation.requested";
    public static final String DOCUMENT_GENERATED = "cpq.document.generated";
    
    // Contract/Asset Events
    public static final String SUBSCRIPTION_RENEWAL_ELIGIBLE = "cpq.subscription.renewal.eligible";
    public static final String CONTRACT_ACTIVATED = "cpq.contract.activated";
    public static final String ASSET_PROVISIONED = "cpq.asset.provisioned";
}
