package com.opencrm.quote.lifecycle;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LifecycleControllerGateTest {
    @Configuration(proxyBeanMethods = false)
    @Profile("lifecycle-gate-test")
    @EnableWebMvc
    @Import(LifecycleChangeController.class)
    static class Config {
        @Bean LifecycleChangeService service() { return mock(LifecycleChangeService.class); }
    }

    private final WebApplicationContextRunner context = new WebApplicationContextRunner()
            .withPropertyValues("spring.profiles.active=lifecycle-gate-test")
            .withUserConfiguration(Config.class);

    @Test
    void defaultDirectHttpMutationsAreUnrouted() {
        context.run(loaded -> {
            assertThat(loaded).hasNotFailed();
            var mvc = MockMvcBuilders.webAppContextSetup(loaded.getSourceApplicationContext()).build();
            UUID id = UUID.randomUUID();
            mvc.perform(post("/api/v1/lifecycle-changes").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
            mvc.perform(post("/api/v1/lifecycle-changes/{id}:price", id).contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
            mvc.perform(post("/api/v1/lifecycle-changes/{id}:submit", id).contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/api/v1/lifecycle-changes/{id}", id)).andExpect(status().isNotFound());
            verifyNoInteractions(loaded.getBean(LifecycleChangeService.class));
        });
    }

    @Test
    void explicitEnablementRegistersTheMutationRoutes() {
        context.withPropertyValues("opencrm.lifecycle-change.enabled=true").run(loaded -> {
            assertThat(loaded).hasNotFailed();
            var mvc = MockMvcBuilders.webAppContextSetup(loaded.getSourceApplicationContext()).build();
            UUID id = UUID.randomUUID();
            mvc.perform(post("/api/v1/lifecycle-changes").contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
            mvc.perform(post("/api/v1/lifecycle-changes/{id}:price", id).contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
            mvc.perform(post("/api/v1/lifecycle-changes/{id}:submit", id).contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
            LifecycleChangeService service = loaded.getBean(LifecycleChangeService.class);
            verifyNoInteractions(service);
            when(service.get(id)).thenReturn(LifecycleChangeResponse.builder().id(id).build());
            mvc.perform(get("/api/v1/lifecycle-changes/{id}", id)).andExpect(status().isOk());
            verify(service).get(id);
        });
    }
}
