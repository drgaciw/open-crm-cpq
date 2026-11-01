package com.opencrm.crm.outbox;

import com.opencrm.common.event.EventContractRegistry;
import com.opencrm.common.event.OutboxEventPublisher;
import com.opencrm.common.event.dto.ContactCreatedEvent;
import com.opencrm.common.event.dto.DealClosedEvent;
import com.opencrm.common.event.dto.DealStageChangedEvent;
import com.opencrm.common.event.dto.OrganizationCreatedEvent;
import com.opencrm.crm.contact.Contact;
import com.opencrm.crm.deal.Deal;
import com.opencrm.crm.deal.DealStage;
import com.opencrm.crm.organization.Organization;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * The only outbox publish sites for CRM domain events (ADR-018 D9: one call site per contract).
 * Domain code writes rows here inside the caller's transaction. Nothing in this service sends to Kafka directly.
 */
@Component
@RequiredArgsConstructor
public class CrmDomainEvents {

    private final OutboxEventPublisher outboxEventPublisher;

    public void organizationCreated(Organization organization) {
        outboxEventPublisher.publish(
                "Organization",
                organization.getUuid(),
                EventContractRegistry.ORGANIZATION_CREATED_V1,
                new OrganizationCreatedEvent(
                        organization.getUuid(),
                        organization.getName(),
                        organization.getType() == null ? null : organization.getType().name(),
                        occurredAt(organization.getCreatedAt())));
    }

    public void contactCreated(Contact contact) {
        outboxEventPublisher.publish(
                "Contact",
                contact.getUuid(),
                EventContractRegistry.CONTACT_CREATED_V1,
                new ContactCreatedEvent(
                        contact.getUuid(),
                        contact.getOrganization() == null ? null : contact.getOrganization().getUuid(),
                        contact.getFirstName(),
                        contact.getLastName(),
                        contact.getEmail(),
                        occurredAt(contact.getCreatedAt())));
    }

    /**
     * Records a stage move. A move into WON or LOST also records {@code DealClosed}.
     * No event when the stage did not change.
     */
    public void dealTransition(Deal deal, DealStage previousStage) {
        if (previousStage == deal.getStage()) {
            return;
        }
        dealStageChanged(deal, previousStage);
        if (deal.getStage() == DealStage.WON || deal.getStage() == DealStage.LOST) {
            dealClosed(deal, previousStage);
        }
    }

    private void dealStageChanged(Deal deal, DealStage previousStage) {
        outboxEventPublisher.publish(
                "Deal",
                deal.getUuid(),
                EventContractRegistry.DEAL_STAGE_CHANGED_V1,
                new DealStageChangedEvent(
                        deal.getUuid(),
                        organizationId(deal),
                        previousStage.name(),
                        deal.getStage().name(),
                        Instant.now()));
    }

    private void dealClosed(Deal deal, DealStage previousStage) {
        outboxEventPublisher.publish(
                "Deal",
                deal.getUuid(),
                EventContractRegistry.DEAL_CLOSED_V1,
                new DealClosedEvent(
                        deal.getUuid(),
                        organizationId(deal),
                        deal.getStage().name(),
                        previousStage.name(),
                        occurredAt(deal.getStage() == DealStage.WON ? deal.getWonAt() : deal.getLostAt()),
                        deal.getStage() == DealStage.LOST ? deal.getLostReason() : null));
    }

    private static String organizationId(Deal deal) {
        return deal.getOrganization() == null ? null : deal.getOrganization().getUuid();
    }

    private static Instant occurredAt(Instant createdAt) {
        return createdAt != null ? createdAt : Instant.now();
    }
}
