package com.opencrm.common.event;

import com.opencrm.common.event.dto.ApprovalRejectedEvent;
import com.opencrm.common.event.dto.AmendmentAuditEvent;
import com.opencrm.common.event.dto.ConfigurationCommittedEvent;
import com.opencrm.common.event.dto.ContractExecutedEvent;
import com.opencrm.common.event.dto.DiscountApprovalRequiredEvent;
import com.opencrm.common.event.dto.PriceCalculatedEvent;
import com.opencrm.common.event.dto.ProductCatalogLifecycleEvent;
import com.opencrm.common.event.dto.ProductRetiredEvent;
import com.opencrm.common.event.dto.QuoteAcceptedEvent;
import com.opencrm.common.event.dto.RampAppliedEvent;
import com.opencrm.common.event.dto.SubscriptionRenewalEligibleEvent;
import com.opencrm.common.event.dto.AssetAuditEvent;
import com.opencrm.common.event.dto.AssetCreatedEvent;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The published language of CPQ domain events (ADR-011) and the contract for what is on the wire (ADR-018 D5).
 *
 * <p>Each entry maps one Kafka topic to its registry event type, version, owning service and the payload class the
 * owner actually publishes. The registry is enforced in both directions:
 * <ul>
 *   <li>{@link OutboxEventPublisher} accepts only an entry from here, only a {@link Status#PUBLISHED} one, and only a
 *       payload of its class. A topic cannot be published without being registered.</li>
 *   <li>{@code EventContractFitnessTest} scans the services: every PUBLISHED entry has exactly one publish call site,
 *       in its owning service (D9), and a RESERVED entry has none.</li>
 * </ul>
 *
 * <p>Adding a field to a payload is not breaking. Payload classes ignore unknown properties, so a consumer built
 * against an older copy keeps working. A breaking change needs a new {@code eventVersion} and a dual-publish window.
 */
public final class EventContractRegistry {

    private EventContractRegistry() {
    }

    /** Whether a contract has a producer. */
    public enum Status {
        /** Produced: exactly one publish call site, in the owning service. */
        PUBLISHED,
        /** Agreed but not produced. {@link OutboxEventPublisher} refuses it until it is switched to PUBLISHED. */
        RESERVED
    }

    /**
     * One published-language event contract.
     *
     * @param payloadClass the class the owner publishes on {@code topic}; required when PUBLISHED, may be {@code null}
     *                     for a RESERVED contract that has no payload yet
     */
    public record EventContract(String eventType, int eventVersion, String owner, String topic,
                                Class<?> payloadClass, Status status) {

        public EventContract {
            Objects.requireNonNull(eventType, "eventType");
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(topic, "topic");
            Objects.requireNonNull(status, "status");
            if (eventVersion < 1) {
                throw new IllegalArgumentException("eventVersion must be at least 1: " + topic);
            }
            if (status == Status.PUBLISHED && payloadClass == null) {
                throw new IllegalArgumentException("A PUBLISHED contract needs its payload class: " + topic);
            }
        }

        public boolean isPublished() {
            return status == Status.PUBLISHED;
        }
    }

    // --- Published: each has exactly one producer, its owner. -------------------------------------------------------

    public static final EventContract QUOTE_ACCEPTED_V1 = published(
            "QuoteAccepted", "quote-service", KafkaTopics.QUOTE_STATUS_ACCEPTED, QuoteAcceptedEvent.class);

    public static final EventContract DISCOUNT_APPROVAL_REQUIRED_V1 = published(
            "DiscountApprovalRequired", "pricing-service", KafkaTopics.DISCOUNT_APPROVAL_REQUIRED,
            DiscountApprovalRequiredEvent.class);

    /** Quote owns its schedule binding; pricing keeps sole ownership of the legacy discount event (ADR-018 D9). */
    public static final EventContract RAMP_APPROVAL_BINDING_CHANGED_V1 = published(
            "RampApprovalBindingChanged", "quote-service", KafkaTopics.RAMP_APPROVAL_BINDING_CHANGED,
            DiscountApprovalRequiredEvent.class);

    public static final EventContract PRODUCT_RETIRED_V1 = published(
            "ProductRetired", "product-service", KafkaTopics.PRODUCT_STATUS_RETIRED, ProductRetiredEvent.class);

    public static final EventContract PRODUCT_UPDATED_V1 = published(
            "ProductUpdated", "product-service", KafkaTopics.PRODUCT_UPDATED, ProductCatalogLifecycleEvent.class);

    public static final EventContract BUNDLE_STRUCTURE_CHANGED_V1 = published(
            "BundleStructureChanged", "product-service", KafkaTopics.BUNDLE_STRUCTURE_CHANGED,
            ProductCatalogLifecycleEvent.class);

    public static final EventContract SUBSCRIPTION_RENEWAL_ELIGIBLE_V1 = published(
            "SubscriptionRenewalEligible", "contract-asset-management", KafkaTopics.SUBSCRIPTION_RENEWAL_ELIGIBLE,
            SubscriptionRenewalEligibleEvent.class);

    public static final EventContract CONFIGURATION_COMMITTED_V1 = published(
            "ConfigurationCommitted", "configuration-engine", KafkaTopics.CONFIGURATION_COMMITTED,
            ConfigurationCommittedEvent.class);

    /** Contract/asset owns the transactional rate application and its sole outbox publish site. */
    public static final EventContract RAMP_APPLIED_V1 = published(
            "RampApplied", "contract-asset-management", KafkaTopics.SUBSCRIPTION_RAMP_APPLIED, RampAppliedEvent.class);

    public static final EventContract QUOTE_APPROVAL_REJECTED_V1 = published(
            "QuoteApprovalRejected", "approval-routing", KafkaTopics.QUOTE_APPROVAL_REJECTED,
            ApprovalRejectedEvent.class);

    /** Minimal S2 audit publication; the later S6 lifecycle catalog remains separately gated. */
    public static final EventContract AMENDMENT_AUDIT_RECORDED_V1 = published(
            "AmendmentAuditRecorded", "contract-asset-management", KafkaTopics.AMENDMENT_DOCUMENT_AUDIT,
            AmendmentAuditEvent.class);

    // --- Reserved: agreed, not produced. ----------------------------------------------------------------------------

    public static final EventContract PRICE_CALCULATED_V1 = reserved(
            "PriceCalculated", "pricing-service", KafkaTopics.PRICE_CALCULATED, PriceCalculatedEvent.class);

    public static final EventContract CONTRACT_EXECUTED_V1 = reserved(
            "ContractExecuted", "contract-asset-management", KafkaTopics.CONTRACT_EXECUTED,
            ContractExecutedEvent.class);

    public static final EventContract APPROVAL_STEP_DECIDED_V1 = reserved(
            "ApprovalStepDecided", "approval-routing", KafkaTopics.APPROVAL_STEP_DECIDED, null);

    public static final EventContract APPROVAL_OUTCOME_COMPLETED_V1 = reserved(
            "ApprovalOutcomeCompleted", "approval-routing", KafkaTopics.APPROVAL_OUTCOME_COMPLETED, null);

    public static final EventContract RULE_SET_PUBLISHED_V1 = reserved(
            "RuleSetPublished", "pricing-service", KafkaTopics.RULE_SET_PUBLISHED, null);

    /** DRG-654. One asset created per activated contract line. */
    public static final EventContract ASSET_CREATED_V1 = published(
            "AssetCreated", "contract-asset-management", KafkaTopics.ASSET_CREATED, AssetCreatedEvent.class);

    /** DRG-654. Audit of that creation, same transaction, separate contract. */
    public static final EventContract ASSET_AUDIT_RECORDED_V1 = published(
            "AssetAuditRecorded", "contract-asset-management", KafkaTopics.ASSET_AUDIT, AssetAuditEvent.class);

    private static final List<EventContract> ALL = List.of(
            QUOTE_ACCEPTED_V1,
            AMENDMENT_AUDIT_RECORDED_V1,
            RAMP_APPLIED_V1,
            DISCOUNT_APPROVAL_REQUIRED_V1,
            RAMP_APPROVAL_BINDING_CHANGED_V1,
            PRODUCT_RETIRED_V1,
            PRODUCT_UPDATED_V1,
            BUNDLE_STRUCTURE_CHANGED_V1,
            SUBSCRIPTION_RENEWAL_ELIGIBLE_V1,
            CONFIGURATION_COMMITTED_V1,
            QUOTE_APPROVAL_REJECTED_V1,
            PRICE_CALCULATED_V1,
            CONTRACT_EXECUTED_V1,
            APPROVAL_STEP_DECIDED_V1,
            APPROVAL_OUTCOME_COMPLETED_V1,
            RULE_SET_PUBLISHED_V1,
            ASSET_CREATED_V1,
            ASSET_AUDIT_RECORDED_V1);

    /** Every registered contract, published and reserved. */
    public static List<EventContract> all() {
        return ALL;
    }

    /** The contract registered for a topic, if any. */
    public static Optional<EventContract> forTopic(String topic) {
        return ALL.stream().filter(contract -> contract.topic().equals(topic)).findFirst();
    }

    private static EventContract published(String eventType, String owner, String topic, Class<?> payloadClass) {
        return new EventContract(eventType, 1, owner, topic, payloadClass, Status.PUBLISHED);
    }

    private static EventContract reserved(String eventType, String owner, String topic, Class<?> payloadClass) {
        return new EventContract(eventType, 1, owner, topic, payloadClass, Status.RESERVED);
    }
}
