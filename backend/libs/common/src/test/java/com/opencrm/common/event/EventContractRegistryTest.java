package com.opencrm.common.event;

import com.opencrm.common.event.EventContractRegistry.EventContract;
import com.opencrm.common.event.EventContractRegistry.Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ADR-018 D5: EventContractRegistry is complete and consistent")
class EventContractRegistryTest {

    @Test
    @DisplayName("all() lists every EventContract constant exactly once")
    void allListsEveryConstant() throws IllegalAccessException {
        List<EventContract> constants = new ArrayList<>();
        for (Field field : EventContractRegistry.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == EventContract.class) {
                constants.add((EventContract) field.get(null));
            }
        }
        assertThat(EventContractRegistry.all()).containsExactlyInAnyOrderElementsOf(constants);
    }

    @Test
    @DisplayName("one contract per topic, and event type plus version is unique")
    void topicsAndTypesAreUnique() {
        List<String> topics = EventContractRegistry.all().stream().map(EventContract::topic).toList();
        List<String> typeVersions = EventContractRegistry.all().stream()
                .map(contract -> contract.eventType() + "@" + contract.eventVersion())
                .toList();

        assertThat(topics).doesNotHaveDuplicates();
        assertThat(typeVersions).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("every contract's topic is a KafkaTopics constant")
    void topicsComeFromKafkaTopics() {
        Set<String> kafkaTopics = Arrays.stream(KafkaTopics.class.getFields())
                .filter(field -> Modifier.isStatic(field.getModifiers()) && field.getType() == String.class)
                .map(EventContractRegistryTest::valueOf)
                .collect(Collectors.toSet());

        assertThat(EventContractRegistry.all()).allSatisfy(contract ->
                assertThat(kafkaTopics).as(contract.eventType()).contains(contract.topic()));
    }

    @Test
    @DisplayName("every KafkaTopics constant has a contract: no topic names without one")
    void everyKafkaTopicHasAContract() {
        Set<String> registered = EventContractRegistry.all().stream()
                .map(EventContract::topic)
                .collect(Collectors.toSet());

        assertThat(Arrays.stream(KafkaTopics.class.getFields())
                .filter(field -> Modifier.isStatic(field.getModifiers()) && field.getType() == String.class)
                .map(EventContractRegistryTest::valueOf))
                .as("add a topic together with its EventContractRegistry entry (PUBLISHED or RESERVED)")
                .allSatisfy(topic -> assertThat(registered).contains(topic));
    }
    @Test
    @DisplayName("the wire: topic, owner and status of every contract are pinned")
    void wireIsPinned() {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("cpq.subscription.ramp.applied", "RampApplied v1, contract-asset-management, PUBLISHED");
        expected.put("cpq.quote.status.accepted", "QuoteAccepted v1, quote-service, PUBLISHED");
        expected.put("cpq.discount.approval.required", "DiscountApprovalRequired v1, pricing-service, PUBLISHED");
        expected.put("cpq.quote.ramp.approval.binding.changed", "RampApprovalBindingChanged v1, quote-service, PUBLISHED");
        expected.put("cpq.product.status.retired", "ProductRetired v1, product-service, PUBLISHED");
        expected.put("cpq.product.updated", "ProductUpdated v1, product-service, PUBLISHED");
        expected.put("cpq.product.bundle.structure.changed", "BundleStructureChanged v1, product-service, PUBLISHED");
        expected.put("cpq.subscription.renewal.eligible",
                "SubscriptionRenewalEligible v1, contract-asset-management, PUBLISHED");
        expected.put("cpq.configuration.committed", "ConfigurationCommitted v1, configuration-engine, PUBLISHED");
        expected.put("cpq.quote.approval.rejected", "QuoteApprovalRejected v1, approval-routing, PUBLISHED");
        expected.put("cpq.price.calculated", "PriceCalculated v1, pricing-service, RESERVED");
        expected.put("cpq.contract.executed", "ContractExecuted v1, contract-asset-management, RESERVED");
        expected.put("cpq.approval.step.decided", "ApprovalStepDecided v1, approval-routing, RESERVED");
        expected.put("cpq.approval.outcome.completed", "ApprovalOutcomeCompleted v1, approval-routing, RESERVED");
        expected.put("cpq.pricing.ruleset.published", "RuleSetPublished v1, pricing-service, RESERVED");

        Map<String, String> actual = new LinkedHashMap<>();
        for (EventContract contract : EventContractRegistry.all()) {
            actual.put(contract.topic(), contract.eventType() + " v" + contract.eventVersion() + ", "
                    + contract.owner() + ", " + contract.status());
        }
        assertThat(actual).containsExactlyInAnyOrderEntriesOf(expected);
    }

    @Test
    @DisplayName("PUBLISHED contracts name their payload class")
    void publishedContractsHavePayloadClasses() {
        assertThat(EventContractRegistry.all())
                .filteredOn(EventContract::isPublished)
                .allSatisfy(contract -> assertThat(contract.payloadClass()).as(contract.topic()).isNotNull());
    }

    @Test
    @DisplayName("forTopic finds registered topics and nothing else")
    void forTopicLooksUpByTopic() {
        assertThat(EventContractRegistry.forTopic(KafkaTopics.QUOTE_STATUS_ACCEPTED))
                .contains(EventContractRegistry.QUOTE_ACCEPTED_V1);
        assertThat(EventContractRegistry.forTopic("cpq.quote.created")).isEmpty();
    }

    @Test
    @DisplayName("a contract is refused without a payload class when PUBLISHED, or with a version below 1")
    void contractValidatesItself() {
        assertThatThrownBy(() -> new EventContract("X", 1, "svc", "cpq.x", null, Status.PUBLISHED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payload class");
        assertThatThrownBy(() -> new EventContract("X", 0, "svc", "cpq.x", Object.class, Status.PUBLISHED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventVersion");
        assertThat(new EventContract("X", 1, "svc", "cpq.x", null, Status.RESERVED).isPublished()).isFalse();
    }

    private static String valueOf(Field field) {
        try {
            return (String) field.get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
