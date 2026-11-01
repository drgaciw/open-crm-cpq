package com.opencrm.common.event;

import com.opencrm.common.event.EventContractRegistry.EventContract;
import com.opencrm.common.event.EventContractRegistry.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DRG-510 / ADR-018 D5: the four CRM domain events are registered as PUBLISHED, owned by crm-service.
 */
@DisplayName("DRG-510: CRM events in EventContractRegistry")
class EventContractRegistryCrmTest {

    @Test
    @DisplayName("OrganizationCreated, ContactCreated, DealStageChanged and DealClosed are PUBLISHED by crm-service")
    void fourCrmEventsArePublishedByCrmService() throws Exception {
        assertPublished("ORGANIZATION_CREATED", "cpq.crm.organization.created",
                "OrganizationCreated", "OrganizationCreatedEvent");
        assertPublished("CONTACT_CREATED", "cpq.crm.contact.created",
                "ContactCreated", "ContactCreatedEvent");
        assertPublished("DEAL_STAGE_CHANGED", "cpq.crm.deal.stage.changed",
                "DealStageChanged", "DealStageChangedEvent");
        assertPublished("DEAL_CLOSED", "cpq.crm.deal.closed",
                "DealClosed", "DealClosedEvent");
    }

    private static void assertPublished(String constant, String topic, String eventType, String payloadSimpleName)
            throws Exception {
        EventContract contract = EventContractRegistry.forTopic(topic).orElse(null);
        assertThat(contract).as("registry entry for %s", topic).isNotNull();
        assertThat(contract.eventType()).isEqualTo(eventType);
        assertThat(contract.eventVersion()).isEqualTo(1);
        assertThat(contract.owner()).isEqualTo("crm-service");
        assertThat(contract.status()).isEqualTo(Status.PUBLISHED);
        assertThat(contract.topic()).isEqualTo(topic);
        assertThat(contract.payloadClass()).isNotNull();
        assertThat(contract.payloadClass().getSimpleName()).isEqualTo(payloadSimpleName);
        assertThat(contract.payloadClass().getPackageName()).isEqualTo("com.opencrm.common.event.dto");

        Field registryField = EventContractRegistry.class.getField(constant + "_V1");
        assertThat(registryField.get(null)).isEqualTo(contract);

        Field topicField = KafkaTopics.class.getField(constant);
        assertThat(topicField.get(null)).isEqualTo(topic);
    }
}
