package com.opencrm.ai.task;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentTaskDispatcherTest {

    @Test
    void unknownKindIsRecordedAndDoesNotStopTheNextTask() {
        AgentTaskService tasks = mock(AgentTaskService.class);
        AgentTask unknown = task("missing-kind");
        AgentTask known = task("enrich");
        when(tasks.claim(eq("dispatcher-a"), anyInt())).thenReturn(List.of(unknown, known));
        doThrow(new IllegalStateException("boom")).when(tasks).fail(eq(unknown.getId()), eq("dispatcher-a"), any());
        RecordingHandler handler = new RecordingHandler("enrich", false);
        AgentTaskDispatcher dispatcher = dispatcher(tasks, handler);

        dispatcher.poll();

        verify(tasks).fail(eq(unknown.getId()), eq("dispatcher-a"), contains("unknown kind"));
        assertThat(handler.runs).isEqualTo(1);
        assertThat(handler.budgetSeen).isEqualTo(7);
        verify(tasks).complete(known.getId(), "dispatcher-a");
    }

    @Test
    void handlerFailureGoesToRetryAndSuccessPassesBudgetWithoutEnforcement() {
        AgentTaskService tasks = mock(AgentTaskService.class);
        AgentTask failing = task("enrich");
        failing.setBudget(-5);
        when(tasks.claim(eq("dispatcher-a"), anyInt())).thenReturn(List.of(failing));
        RecordingHandler handler = new RecordingHandler("enrich", true);
        AgentTaskDispatcher dispatcher = dispatcher(tasks, handler);

        dispatcher.poll();

        assertThat(handler.runs).isEqualTo(1);
        assertThat(handler.budgetSeen).isEqualTo(-5);
        verify(tasks).fail(eq(failing.getId()), eq("dispatcher-a"), contains("scripted failure"));
        verify(tasks, never()).complete(any(), any());
    }

    @Test
    void dispatcherHasNoDependencyOnLlmAgentRouterOrSelection() throws Exception {
        byte[] bytecode = AgentTaskDispatcher.class.getResourceAsStream("AgentTaskDispatcher.class").readAllBytes();
        String constants = new String(bytecode, StandardCharsets.ISO_8859_1);
        assertThat(constants).doesNotContain("com/opencrm/ai/llm");
        assertThat(constants).doesNotContain("com/opencrm/ai/agent/");
        assertThat(constants).doesNotContain("com/opencrm/ai/router");
        assertThat(constants).doesNotContain("com/opencrm/ai/selection");
        String source = Files.readString(Path.of("src/main/java/com/opencrm/ai/task/AgentTaskDispatcher.java"));
        assertThat(source).doesNotContain("com.opencrm.ai.llm");
        assertThat(source).doesNotContain("com.opencrm.ai.agent");
        assertThat(source).doesNotContain("com.opencrm.ai.router");
        assertThat(source).doesNotContain("com.opencrm.ai.selection");
        assertThat(source).doesNotContain("@Scheduled");
        assertThat(source).doesNotContain("kafka");
    }

    private static AgentTaskDispatcher dispatcher(AgentTaskService tasks, AgentTaskHandler handler) {
        AgentTaskProperties properties = new AgentTaskProperties();
        properties.getDispatcher().setEnabled(true);
        properties.getDispatcher().setClaimLimit(10);
        properties.getDispatcher().setLease(Duration.ofSeconds(30));
        properties.getDispatcher().setPollInterval(Duration.ofHours(1));
        properties.getDispatcher().setLeaseOwner("dispatcher-a");
        return new AgentTaskDispatcher(tasks, properties, List.of(handler));
    }

    private static AgentTask task(String kind) {
        AgentTask task = new AgentTask();
        task.setId(UUID.randomUUID());
        task.setTenantId("tenant-a");
        task.setKind(kind);
        task.setSubjectType("contact");
        task.setSubjectId(UUID.randomUUID().toString());
        task.setBudget(7);
        task.setAttempts(0);
        task.setMaxAttempts(5);
        return task;
    }

    private static final class RecordingHandler implements AgentTaskHandler {
        private final String kind;
        private final boolean fail;
        private int runs;
        private Integer budgetSeen;

        private RecordingHandler(String kind, boolean fail) {
            this.kind = kind;
            this.fail = fail;
        }

        @Override
        public String kind() {
            return kind;
        }

        @Override
        public void handle(AgentTask task) {
            runs++;
            budgetSeen = task.getBudget();
            if (fail) {
                throw new IllegalStateException("scripted failure");
            }
        }
    }
}
