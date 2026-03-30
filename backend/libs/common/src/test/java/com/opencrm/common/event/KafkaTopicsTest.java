package com.opencrm.common.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

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
}
