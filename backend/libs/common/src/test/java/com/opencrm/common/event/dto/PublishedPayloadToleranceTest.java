package com.opencrm.common.event.dto;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.opencrm.common.event.EventContractRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * ADR-018 D5: consumers must tolerate properties they do not know, so a producer can add fields without breaking a
 * consumer built against an older copy of the DTO. Every payload class in {@link EventContractRegistry}, and every
 * published-language type it contains, must read a JSON object carrying an unknown field under a strict mapper.
 */
@DisplayName("ADR-018 D5: every registered event payload tolerates unknown properties")
class PublishedPayloadToleranceTest {

    private static final String PAYLOAD_PACKAGE = "com.opencrm.common.event";
    private static final String UNKNOWN_FIELD = "{\"aFieldNoConsumerKnows\": {\"nested\": [1, 2]}}";

    private static final ObjectMapper STRICT = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    static Stream<Class<?>> payloadTypes() {
        Set<Class<?>> types = new LinkedHashSet<>();
        EventContractRegistry.all().stream()
                .map(EventContractRegistry.EventContract::payloadClass)
                .filter(type -> type != null)
                .forEach(root -> types.addAll(reachableTypes(root)));
        return types.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("payloadTypes")
    void readsAnObjectWithAnUnknownField(Class<?> type) {
        assertThatCode(() -> STRICT.readValue(UNKNOWN_FIELD, type)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("payloadTypes")
    void isInThePublishedLanguagePackage(Class<?> type) {
        assertThat(type.getPackageName()).startsWith(PAYLOAD_PACKAGE);
    }

    /** The payload class plus every published-language type reachable through its fields, including list elements. */
    private static Set<Class<?>> reachableTypes(Class<?> root) {
        Set<Class<?>> seen = new LinkedHashSet<>();
        Deque<Class<?>> todo = new ArrayDeque<>();
        todo.add(root);
        while (!todo.isEmpty()) {
            Class<?> type = todo.poll();
            if (!seen.add(type)) {
                continue;
            }
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                for (Class<?> candidate : classesIn(field.getGenericType())) {
                    if (candidate.getPackageName().startsWith(PAYLOAD_PACKAGE) && !candidate.isEnum()) {
                        todo.add(candidate);
                    }
                }
            }
        }
        return seen;
    }

    private static Set<Class<?>> classesIn(Type type) {
        Set<Class<?>> classes = new LinkedHashSet<>();
        if (type instanceof Class<?> clazz) {
            classes.add(clazz);
        } else if (type instanceof ParameterizedType parameterized) {
            classes.addAll(classesIn(parameterized.getRawType()));
            for (Type argument : parameterized.getActualTypeArguments()) {
                classes.addAll(classesIn(argument));
            }
        }
        return classes;
    }
}
