package com.opencrm.ai.task;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AgentTaskDispatcherDisabledTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AgentTaskConfiguration.class);

    @Test
    void absentPropertyDoesNotStartDispatcher() {
        runner.run(context -> assertThat(context).doesNotHaveBean(AgentTaskDispatcher.class));
    }

    @Test
    void explicitFalseDoesNotStartDispatcher() {
        runner.withPropertyValues("ai.agent-task.dispatcher.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(AgentTaskDispatcher.class));
    }

    @Test
    void explicitTrueStartsDispatcherBean() {
        runner.withPropertyValues(
                        "ai.agent-task.dispatcher.enabled=true",
                        "ai.agent-task.dispatcher.poll-interval=1h",
                        "ai.agent-task.dispatcher.lease=30s")
                .withBean(AgentTaskService.class, () -> mock(AgentTaskService.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(AgentTaskDispatcher.class);
                    AgentTaskDispatcher dispatcher = context.getBean(AgentTaskDispatcher.class);
                    assertThat(dispatcher.isRunning()).isTrue();
                });
    }

    @Test
    void applicationYamlKeepsDispatcherDisabledInEveryProfile() throws Exception {
        String yaml = Files.readString(Path.of("src/main/resources/application.yml"));
        int section = yaml.indexOf("agent-task:");
        assertThat(section).isGreaterThan(-1);
        String block = yaml.substring(section);
        assertThat(block).contains("enabled: false");
        assertThat(block).doesNotContain("enabled: true");
        assertThat(AgentTaskLimits.MAX_LEASE).isEqualTo(Duration.ofMinutes(2));
    }
}
