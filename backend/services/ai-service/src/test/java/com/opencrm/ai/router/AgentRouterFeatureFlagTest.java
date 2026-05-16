package com.opencrm.ai.router;

import com.opencrm.ai.agent.AgentRequest;
import com.opencrm.ai.agent.AgentResponse;
import com.opencrm.ai.agent.CpqAgent;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRouterFeatureFlagTest {

    @Test
    void invokeReturnsDisabledFeatureResponseWhenAgentsAreDisabled() {
        RecordingAgent agent = new RecordingAgent();
        AgentRouter router = new AgentRouter(List.of(agent), false);

        var response = router.invokeAgent("cpq-agent-test", request());

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        AgentResponse body = response.getBody();
        assertFalse(body.isSuccess());
        assertEquals("AI agent feature is disabled for cpq-agent-test.", body.getOutput());
        assertEquals(true, body.getMetadata().get("error"));
        assertFalse(agent.invoked);
    }

    @Test
    void streamReturnsDisabledFeatureResponseWhenAgentsAreDisabled() {
        RecordingAgent agent = new RecordingAgent();
        AgentRouter router = new AgentRouter(List.of(agent), false);

        StepVerifier.create(router.streamAgent("cpq-agent-test", request()))
                .expectNext("AI agent feature is disabled for cpq-agent-test.")
                .verifyComplete();

        assertFalse(agent.streamed);
    }

    @Test
    void listAgentsReturnsEmptyWhenAgentsAreDisabled() {
        AgentRouter router = new AgentRouter(List.of(new RecordingAgent()), false);

        var response = router.listAgents();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isEmpty());
    }

    private AgentRequest request() {
        return new AgentRequest() {
            @Override
            public String getSystemPrompt() {
                return "";
            }

            @Override
            public List<String> getMessages() {
                return List.of("hello");
            }

            @Override
            public Map<String, Object> getContext() {
                return Map.of();
            }
        };
    }

    private static final class RecordingAgent implements CpqAgent {
        private boolean invoked;
        private boolean streamed;

        @Override
        public String getAgentId() {
            return "cpq-agent-test";
        }

        @Override
        public String getName() {
            return "Test Agent";
        }

        @Override
        public String getDescription() {
            return "Records router calls";
        }

        @Override
        public AgentResponse invoke(AgentRequest request) {
            invoked = true;
            throw new AssertionError("disabled router must not invoke agents");
        }

        @Override
        public Flux<String> stream(AgentRequest request) {
            streamed = true;
            return Flux.just("unexpected");
        }
    }
}
