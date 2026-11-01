package com.opencrm.crm.outbox;

/**
 * Topic names DRG-510 registers. Tests use the literals so they compile before {@code KafkaTopics} grows.
 */
final class CrmOutboxTopics {

    static final String ORGANIZATION_CREATED = "cpq.crm.organization.created";
    static final String CONTACT_CREATED = "cpq.crm.contact.created";
    static final String DEAL_STAGE_CHANGED = "cpq.crm.deal.stage.changed";
    static final String DEAL_CLOSED = "cpq.crm.deal.closed";

    private CrmOutboxTopics() {
    }
}
