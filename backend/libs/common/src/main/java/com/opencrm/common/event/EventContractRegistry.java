package com.opencrm.common.event;

/**
 * Registry of versioned published-language event contracts shared across CPQ services.
 */
public final class EventContractRegistry {

    private EventContractRegistry() {
    }

    public record EventContract(String eventType, int eventVersion, String owner, String topic) {
    }

    public static final EventContract QUOTE_ACCEPTED_V1 =
            new EventContract("QuoteAccepted", 1, "quote-service", KafkaTopics.QUOTE_STATUS_ACCEPTED);

    public static final EventContract PRICE_CALCULATED_V1 =
            new EventContract("PriceCalculated", 1, "pricing-service", KafkaTopics.PRICE_CALCULATED);

    public static final EventContract DISCOUNT_APPROVAL_REQUIRED_V1 =
            new EventContract("DiscountApprovalRequired", 1, "pricing-service", KafkaTopics.DISCOUNT_APPROVAL_REQUIRED);

    public static final EventContract PRODUCT_RETIRED_V1 =
            new EventContract("ProductRetired", 1, "product-service", KafkaTopics.PRODUCT_STATUS_RETIRED);

    public static final EventContract SUBSCRIPTION_RENEWAL_ELIGIBLE_V1 =
            new EventContract("SubscriptionRenewalEligible", 1, "contract-asset-management", KafkaTopics.SUBSCRIPTION_RENEWAL_ELIGIBLE);

    public static final EventContract CONFIGURATION_COMMITTED_V1 =
            new EventContract("ConfigurationCommitted", 1, "configuration-engine", KafkaTopics.CONFIGURATION_COMMITTED);

    public static final EventContract CONTRACT_EXECUTED_V1 =
            new EventContract("ContractExecuted", 1, "contract-asset-management", KafkaTopics.CONTRACT_EXECUTED);

    public static final EventContract APPROVAL_STEP_DECIDED_V1 =
            new EventContract("ApprovalStepDecided", 1, "approval-routing", KafkaTopics.APPROVAL_STEP_DECIDED);

    public static final EventContract APPROVAL_OUTCOME_COMPLETED_V1 =
            new EventContract("ApprovalOutcomeCompleted", 1, "approval-routing", KafkaTopics.APPROVAL_OUTCOME_COMPLETED);
}
