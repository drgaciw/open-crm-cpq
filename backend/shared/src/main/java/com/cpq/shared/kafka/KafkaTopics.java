package com.cpq.shared.kafka;

/**
 * Constants for all Kafka topics used in the Agentic CPQ Platform.
 */
public final class KafkaTopics {
    
    // Product Catalog events
    public static final String PRODUCT_STATUS_RETIRED = "cpq.product.status.retired";
    
    // Pricing Engine events
    public static final String DISCOUNT_APPROVAL_REQUIRED = "cpq.discount.approval.required";
    
    // Quote Management events
    public static final String QUOTE_STATUS_ACCEPTED = "cpq.quote.status.accepted";
    
    // Contract/Asset Management events
    public static final String SUBSCRIPTION_RENEWAL_ELIGIBLE = "cpq.subscription.renewal.eligible";
    
    // Approval Routing events
    public static final String QUOTE_APPROVAL_REJECTED = "cpq.quote.approval.rejected";
    
    // Private constructor to prevent instantiation
    private KafkaTopics() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}