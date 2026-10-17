package com.opencrm.pricing.renewal;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class RenewalResolveNoWriteTest {

    @Test
    void resolveTypesHaveNoRepositoryOutboxOrWriteTransaction() {
        assertThat(instanceFields(RenewalResolveController.class))
                .allMatch(field -> field.getType().equals(RenewalResolveService.class));
        assertThat(instanceFields(RenewalResolveService.class))
                .allMatch(field -> field.getType().equals(RenewalUpliftResolver.class));
        assertThat(RenewalResolveService.class.getAnnotation(Transactional.class)).isNull();
        assertThat(RenewalResolveController.class.getAnnotation(Transactional.class)).isNull();
        for (Class<?> type : List.of(RenewalResolveController.class, RenewalResolveService.class, RenewalUpliftResolver.class)) {
            for (Field field : instanceFields(type)) {
                String name = field.getType().getName();
                assertThat(name).doesNotContain("Repository", "Outbox", "PricingAuditLog", "EntityManager", "Kafka");
            }
        }
        assertThat(RenewalUpliftResolver.class.getDeclaredFields())
                .allMatch(field -> Modifier.isStatic(field.getModifiers()));
    }

    private static List<Field> instanceFields(Class<?> type) {
        return java.util.Arrays.stream(type.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .toList();
    }

}
