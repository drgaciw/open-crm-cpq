package com.opencrm.ai.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class CpqAgentToolClientsContextTest {
    @Test void springSelectsTheProductionConstructorAmongTestOverloads() {
        new ApplicationContextRunner().withUserConfiguration(CpqAgentToolClients.class)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withPropertyValues("opencrm.gateway.base-url=http://127.0.0.1:1")
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(CpqAgentToolClients.class));
    }
}
