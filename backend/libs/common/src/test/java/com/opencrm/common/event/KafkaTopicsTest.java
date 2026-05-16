package com.opencrm.common.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for KafkaTopics constants.
 */
@DisplayName("KafkaTopics Constants")
class KafkaTopicsTest {

    @Test
    @DisplayName("all topic constants should start with cpq.")
    void allTopicConstantsShouldStartWithCpq() throws IllegalAccessException {
        Field[] fields = KafkaTopics.class.getDeclaredFields();
        int topicCount = 0;
        for (Field field : fields) {
            int modifiers = field.getModifiers();
            if (Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers) && field.getType() == String.class) {
                String value = (String) field.get(null);
                assertTrue(value.startsWith("cpq."), "Topic constant " + field.getName() + " does not start with 'cpq.': " + value);
                topicCount++;
            }
        }
        assertTrue(topicCount > 0, "No topic constants found");
    }

    @Test
    @DisplayName("published-language topics are defined for cross-service contracts")
    void publishedLanguageTopicsAreDefined() {
        assertThat(KafkaTopics.QUOTE_STATUS_ACCEPTED).isEqualTo("cpq.quote.status.accepted");
        assertThat(KafkaTopics.PRICE_CALCULATED).isEqualTo("cpq.price.calculated");
        assertThat(KafkaTopics.DISCOUNT_APPROVAL_REQUIRED).isEqualTo("cpq.discount.approval.required");
        assertThat(KafkaTopics.PRODUCT_STATUS_RETIRED).isEqualTo("cpq.product.status.retired");
        assertThat(KafkaTopics.SUBSCRIPTION_RENEWAL_ELIGIBLE).isEqualTo("cpq.subscription.renewal.eligible");
        assertThat(KafkaTopics.CONFIGURATION_COMMITTED).isEqualTo("cpq.configuration.committed");
        assertThat(KafkaTopics.BUNDLE_STRUCTURE_CHANGED).isEqualTo("cpq.product.bundle.structure.changed");
        assertThat(KafkaTopics.CONTRACT_EXECUTED).isEqualTo("cpq.contract.executed");
    }
}
