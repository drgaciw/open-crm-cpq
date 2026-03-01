package com.opencrm.shared.kafka;

public final class KafkaTopics {
    public static final String PRODUCT_STATUS_RETIRED = "cpq.product.status.retired";
    public static final String DISCOUNT_APPROVAL_REQUIRED = "cpq.discount.approval.required";
    public static final String QUOTE_STATUS_ACCEPTED = "cpq.quote.status.accepted";
    public static final String SUBSCRIPTION_RENEWAL_ELIGIBLE = "cpq.subscription.renewal.eligible";
    public static final String QUOTE_APPROVAL_REJECTED = "cpq.quote.approval.rejected";
    
    private KafkaTopics() {}
}
