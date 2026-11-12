package com.opencrm.common.event;

import com.opencrm.common.event.dto.AccountsMergedEvent;
import com.opencrm.common.event.dto.ApprovalRejectedEvent;
import com.opencrm.common.event.dto.AmendmentAuditEvent;
import com.opencrm.common.event.dto.ConfigurationCommittedEvent;
import com.opencrm.common.event.dto.ConfigurationFrozenEvent;
import com.opencrm.common.event.dto.ConfigurationSessionStartedEvent;
import com.opencrm.common.event.dto.ContactCreatedEvent;
import com.opencrm.common.event.dto.ContractExecutedEvent;
import com.opencrm.common.event.dto.DealClosedEvent;
import com.opencrm.common.event.dto.DealCreatedEvent;
import com.opencrm.common.event.dto.DealStageChangedEvent;
import com.opencrm.common.event.dto.DiscountApprovalRequiredEvent;
import com.opencrm.common.event.dto.PriceCalculatedEvent;
import com.opencrm.common.event.dto.HierarchyChangedEvent;
import com.opencrm.common.event.dto.OrganizationCreatedEvent;
import com.opencrm.common.event.dto.PolicyDefinedEvent;
import com.opencrm.common.event.dto.PriceBookPublishedEvent;
import com.opencrm.common.event.dto.ProductCatalogLifecycleEvent;
import com.opencrm.common.event.dto.ProductDefinedEvent;
import com.opencrm.common.event.dto.ProductRetiredEvent;
import com.opencrm.common.event.dto.ProductRevisedEvent;
import com.opencrm.common.event.dto.QuoteAcceptedEvent;
import com.opencrm.common.event.dto.RampAppliedEvent;
import com.opencrm.common.event.dto.RoleAssignedEvent;
import com.opencrm.common.event.dto.SignatureCompletedEvent;
import com.opencrm.common.event.dto.SolutionDerivedEvent;
import com.opencrm.common.event.dto.SubscriptionRenewalEligibleEvent;
import com.opencrm.common.event.dto.TerritoryAssignedEvent;
import com.opencrm.common.event.dto.AssetAmendedEvent;
import com.opencrm.common.event.dto.AssetAuditEvent;
import com.opencrm.common.event.dto.AssetCreatedEvent;
import com.opencrm.common.event.dto.AssetSupersededEvent;
import com.opencrm.common.event.dto.AssetTerminatedEvent;

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

    /** DRG-418 / BC-01-01. DefineProduct: a new Product definition created. */
    public static final EventContract PRODUCT_DEFINED_V1 = published(
            "ProductDefined", "product-service", KafkaTopics.PRODUCT_DEFINED, ProductDefinedEvent.class);

    /** DRG-418 / BC-01-01. ReviseProduct: an existing Product definition revised. */
    public static final EventContract PRODUCT_REVISED_V1 = published(
            "ProductRevised", "product-service", KafkaTopics.PRODUCT_REVISED, ProductRevisedEvent.class);

    public static final EventContract SUBSCRIPTION_RENEWAL_ELIGIBLE_V1 = published(
            "SubscriptionRenewalEligible", "contract-asset-management", KafkaTopics.SUBSCRIPTION_RENEWAL_ELIGIBLE,
            SubscriptionRenewalEligibleEvent.class);

    public static final EventContract CONFIGURATION_COMMITTED_V1 = published(
            "ConfigurationCommitted", "configuration-engine", KafkaTopics.CONFIGURATION_COMMITTED,
            ConfigurationCommittedEvent.class);

    /** DRG-424 / BC-02-01. Emitted when StartSession creates a configuration session. */
    public static final EventContract CONFIGURATION_SESSION_STARTED_V1 = published(
            "ConfigurationSessionStarted", "configuration-engine", KafkaTopics.CONFIGURATION_SESSION_STARTED,
            ConfigurationSessionStartedEvent.class);

    /**
     * DRG-428 / BC-02-05. FreezeConfiguration: ValidateConfiguration then FreezeConfiguration computes the
     * deterministic ConfigurationHash over selections, catalog version and rule set version, making the
     * configuration immutable so Pricing and Quote can reference it.
     */
    public static final EventContract CONFIGURATION_FROZEN_V1 = published(
            "ConfigurationFrozen", "configuration-engine", KafkaTopics.CONFIGURATION_FROZEN,
            ConfigurationFrozenEvent.class);

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

    /** AccountCreated from BC-12-01. Appended; existing contracts keep their order. */
    public static final EventContract ORGANIZATION_CREATED_V1 = published(
            "OrganizationCreated", "crm-service", KafkaTopics.ORGANIZATION_CREATED, OrganizationCreatedEvent.class);

    public static final EventContract CONTACT_CREATED_V1 = published(
            "ContactCreated", "crm-service", KafkaTopics.CONTACT_CREATED, ContactCreatedEvent.class);

    public static final EventContract DEAL_STAGE_CHANGED_V1 = published(
            "DealStageChanged", "crm-service", KafkaTopics.DEAL_STAGE_CHANGED, DealStageChangedEvent.class);

    public static final EventContract DEAL_CLOSED_V1 = published(
            "DealClosed", "crm-service", KafkaTopics.DEAL_CLOSED, DealClosedEvent.class);

    /**
     * DRG-485 / BC-13-01. DealCreated is OpportunityCreated. DealStageChanged and DealClosed remain the
     * stage and close contracts; this is not a second StageAdvanced or OpportunityClosed.
     */
    public static final EventContract DEAL_CREATED_V1 = published(
            "DealCreated", "crm-service", KafkaTopics.DEAL_CREATED, DealCreatedEvent.class);

    /** DRG-482 / BC-12-02. One event when an account parent link is created or changed. */
    public static final EventContract HIERARCHY_CHANGED_V1 = published(
            "HierarchyChanged", "crm-service", KafkaTopics.HIERARCHY_CHANGED, HierarchyChangedEvent.class);

    /** DRG-654. One asset created per activated contract line. */
    public static final EventContract ASSET_CREATED_V1 = published(
            "AssetCreated", "contract-asset-management", KafkaTopics.ASSET_CREATED, AssetCreatedEvent.class);

    /** DRG-654. Audit of that creation, same transaction, separate contract. */
    public static final EventContract ASSET_AUDIT_RECORDED_V1 = published(
            "AssetAuditRecorded", "contract-asset-management", KafkaTopics.ASSET_AUDIT, AssetAuditEvent.class);

    /** DRG-655. ChangeLine Update. */
    public static final EventContract ASSET_AMENDED_V1 = published(
            "AssetAmended", "contract-asset-management", KafkaTopics.ASSET_AMENDED, AssetAmendedEvent.class);

    /** DRG-655. ChangeLine Remove or Terminate. */
    public static final EventContract ASSET_TERMINATED_V1 = published(
            "AssetTerminated", "contract-asset-management", KafkaTopics.ASSET_TERMINATED, AssetTerminatedEvent.class);

    /** DRG-655. ChangeLine Swap or Upgrade, published after the successor row is saved. */
    public static final EventContract ASSET_SUPERSEDED_V1 = published(
            "AssetSuperseded", "contract-asset-management", KafkaTopics.ASSET_SUPERSEDED, AssetSupersededEvent.class);

    /** DRG-483. One territory and segment assignment for an account. */
    public static final EventContract TERRITORY_ASSIGNED_V1 = published(
            "TerritoryAssigned", "crm-service", KafkaTopics.TERRITORY_ASSIGNED, TerritoryAssignedEvent.class);

    /** DRG-484. CRM-owned rows are repointed in this service; other services consume the event. */
    public static final EventContract ACCOUNTS_MERGED_V1 = published(
            "AccountsMerged", "crm-service", KafkaTopics.ACCOUNTS_MERGED, AccountsMergedEvent.class);

    /** DRG-487 / BC-13-03. Advisory solution from a guided selling session. Does not close the deal. */
    public static final EventContract SOLUTION_DERIVED_V1 = published(
            "SolutionDerived", "crm-service", KafkaTopics.SOLUTION_DERIVED, SolutionDerivedEvent.class);

    /** DRG-441 / BC-04-01. A commercial policy version (discount/margin/term/clause thresholds) is defined. */
    public static final EventContract POLICY_DEFINED_V1 = published(
            "PolicyDefined", "approval-routing", KafkaTopics.POLICY_DEFINED, PolicyDefinedEvent.class);

    /** DRG-307 / BC-19-03. AssignRole gave a principal a role. */
    public static final EventContract ROLE_ASSIGNED_V1 = published(
            "RoleAssigned", "authorization-service", KafkaTopics.ROLE_ASSIGNED, RoleAssignedEvent.class);

    /** DRG-431 / BC-03-01. One event per price book version publish. */
    public static final EventContract PRICE_BOOK_PUBLISHED_V1 = published(
            "PriceBookPublished", "pricebook-service", KafkaTopics.PRICE_BOOK_PUBLISHED, PriceBookPublishedEvent.class);

    /**
     * DRG-457 / BC-06-04. Every required signer completed an e-signature envelope, behind the ACL in
     * {@code com.opencrm.contract.signature.acl}. Owned by contract-asset-management: e-signature coordination sits
     * in the Govern &amp; Contract phase alongside amendment/asset lifecycle, which this service already owns.
     */
    public static final EventContract SIGNATURE_COMPLETED_V1 = published(
            "SignatureCompleted", "contract-asset-management", KafkaTopics.SIGNATURE_COMPLETED,
            SignatureCompletedEvent.class);

    private static final List<EventContract> ALL = List.of(
            QUOTE_ACCEPTED_V1,
            AMENDMENT_AUDIT_RECORDED_V1,
            RAMP_APPLIED_V1,
            DISCOUNT_APPROVAL_REQUIRED_V1,
            RAMP_APPROVAL_BINDING_CHANGED_V1,
            PRODUCT_RETIRED_V1,
            PRODUCT_UPDATED_V1,
            BUNDLE_STRUCTURE_CHANGED_V1,
            PRODUCT_DEFINED_V1,
            PRODUCT_REVISED_V1,
            SUBSCRIPTION_RENEWAL_ELIGIBLE_V1,
            CONFIGURATION_COMMITTED_V1,
            CONFIGURATION_SESSION_STARTED_V1,
            QUOTE_APPROVAL_REJECTED_V1,
            PRICE_CALCULATED_V1,
            CONTRACT_EXECUTED_V1,
            APPROVAL_STEP_DECIDED_V1,
            APPROVAL_OUTCOME_COMPLETED_V1,
            RULE_SET_PUBLISHED_V1,
            ORGANIZATION_CREATED_V1,
            CONTACT_CREATED_V1,
            DEAL_STAGE_CHANGED_V1,
            DEAL_CLOSED_V1,
            DEAL_CREATED_V1,
            ASSET_CREATED_V1,
            ASSET_AUDIT_RECORDED_V1,
            ASSET_AMENDED_V1,
            ASSET_TERMINATED_V1,
            ASSET_SUPERSEDED_V1,
            HIERARCHY_CHANGED_V1,
            TERRITORY_ASSIGNED_V1,
            ACCOUNTS_MERGED_V1,
            SOLUTION_DERIVED_V1,
            POLICY_DEFINED_V1,
            CONFIGURATION_FROZEN_V1,
            PRICE_BOOK_PUBLISHED_V1,
            SIGNATURE_COMPLETED_V1);

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
