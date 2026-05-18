package com.opencrm.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.ai.audit.AgentAuditLogger;
import com.opencrm.ai.llm.LlmAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AbstractCpqAgent audit chain")
class AbstractCpqAgentAuditChainTest {

    @Mock
    private LlmAdapter llmAdapter;

    @Mock
    private AgentAuditLogger auditLogger;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private TestBenchAgent agent;

    /** Minimal concrete agent for exercising {@link AbstractCpqAgent} internals. */
    static final class TestBenchAgent extends AbstractCpqAgent {

        @Override
        public String getAgentId() {
            return "test-agent";
        }

        @Override
        public String getName() {
            return "Test Bench Agent";
        }

        @Override
        public String getDescription() {
            return "Harness-only agent";
        }

        @Override
        protected String autonomyTier() {
            return "Advisory";
        }

        @Override
        protected String agentSystemPrompt() {
            return "Return concise JSON.";
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        agent = new TestBenchAgent();
        inject("llmAdapter", llmAdapter);
        inject("auditLogger", auditLogger);
        inject("objectMapper", objectMapper);
    }

    private void inject(String fieldName, Object value) throws Exception {
        var f = AbstractCpqAgent.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(agent, value);
    }

    @Test
    @DisplayName("invoke() logs failure when LLM throws")
    void invokeLogsFailureOnLlmError() {
        when(llmAdapter.generate(anyString(), anyString()))
                .thenThrow(new RuntimeException("upstream unavailable"));

        AgentResponse response = agent.invoke(DefaultAgentRequest.of("hello", Map.of()));

        assertFalse(response.isSuccess());
        verify(auditLogger).logInvocation(eq("test-agent"), anyString(), eq(Map.of()));
        verify(auditLogger).logFailure(eq("test-agent"), anyString(), eq("upstream unavailable"));
        verify(auditLogger, never()).logResponse(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("invoke() logs response with latency on success")
    void invokeLogsResponseWithLatency() {
        when(llmAdapter.generate(anyString(), anyString())).thenReturn("ok");

        AgentResponse response = agent.invoke(DefaultAgentRequest.of("hello", Map.of("quoteId", "Q-1")));

        assertTrue(response.isSuccess());
        ArgumentCaptor<Integer> latencyCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(auditLogger).logResponse(eq("test-agent"), anyString(), eq("ok"),
                latencyCaptor.capture(), isNull());
        assertNotNull(latencyCaptor.getValue());
        assertTrue(latencyCaptor.getValue() >= 0);
    }

    @Test
    @DisplayName("stream() logs aggregated response on complete")
    void streamLogsAggregatedResponse() {
        when(llmAdapter.stream(anyString(), anyString())).thenReturn(Flux.just("part-", "two"));

        ArgumentCaptor<String> sessionCaptor = ArgumentCaptor.forClass(String.class);

        StepVerifier.create(agent.stream(DefaultAgentRequest.of("stream me", Map.of())))
                .expectNext("part-", "two")
                .verifyComplete();

        verify(auditLogger).logInvocation(eq("test-agent"), sessionCaptor.capture(), eq(Map.of()));
        verify(auditLogger).logResponse(eq("test-agent"), eq(sessionCaptor.getValue()), eq("part-two"));
        verify(auditLogger, never()).logFailure(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("stream() logs failure when stream errors")
    void streamLogsFailureOnError() {
        when(llmAdapter.stream(anyString(), anyString()))
                .thenReturn(Flux.error(new IllegalStateException("stream broke")));

        StepVerifier.create(agent.stream(DefaultAgentRequest.of("x", Map.of())))
                .verifyError(IllegalStateException.class);

        verify(auditLogger).logFailure(eq("test-agent"), anyString(), eq("stream broke"));
        verify(auditLogger, never()).logResponse(anyString(), anyString(), anyString());
    }
}
