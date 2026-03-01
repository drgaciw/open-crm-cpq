package com.opencrm.cpq.shared.kafka;

/**
 * Central registry of all Kafka topic names used across CPQ bounded contexts.
 * Matches the event_topics section of the CPQ Agent Framework.
 */
public final class CpqTopics {

    private CpqTopics() {
    }

    /** Published by Product Catalog when a product is retired. */
    public static final String PRODUCT_STATUS_RETIRED = "cpq.product.status.retired";

    /** Published by Pricing Engine when a discount needs approval. */
    public static final String DISCOUNT_APPROVAL_REQUIRED = "cpq.discount.approval.required";

    /** Published by Quote Management when a quote is accepted. */
    public static final String QUOTE_STATUS_ACCEPTED = "cpq.quote.status.accepted";

    /** Published by Contract/Asset Management when a subscription is eligible for renewal. */
    public static final String SUBSCRIPTION_RENEWAL_ELIGIBLE = "cpq.subscription.renewal.eligible";

    /** Published by Approval Routing when a quote approval is rejected. */
    public static final String QUOTE_APPROVAL_REJECTED = "cpq.quote.approval.rejected";

    /** Published by Approval Routing when a discount approval is decided. */
    public static final String DISCOUNT_APPROVAL_DECIDED = "cpq.discount.approval.decided";
}
