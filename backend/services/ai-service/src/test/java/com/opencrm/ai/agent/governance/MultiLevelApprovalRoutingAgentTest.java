package com.opencrm.ai.agent.governance;

import com.opencrm.ai.agent.AgentRequest;
import com.opencrm.ai.agent.AgentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MultiLevelApprovalRoutingAgentTest {

    private ChatClient chatClient;
    private MultiLevelApprovalRoutingAgent agent;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        agent = new MultiLevelApprovalRoutingAgent(chatClient);
    }

    @Test
    void exposesAgentMetadata() {
        assertEquals("cpq-agent-024", agent.getAgentId());
        assertEquals("Multi-Level Approval Routing Agent", agent.getName());
        assertNotNull(agent.getDescription());
    }

    @Test
    void invokeUsesChatClientForApprovalRouting() {
        AgentRequest request = mock(AgentRequest.class);
        when(request.getSystemPrompt()).thenReturn("You are an approval routing agent.");
        when(request.getMessages()).thenReturn(List.of("Route this quote for approval."));

        when(chatClient.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation("Quote routed for multi-level approval."))));

        AgentResponse response = agent.invoke(request);

        assertTrue(response.isSuccess());
        assertEquals("Quote routed for multi-level approval.", response.getOutput());
        assertEquals("Automated", response.getMetadata().get("autonomy"));
    }

    @Test
    void streamReturnsSimulatedChunksUntilModelBacked() {
        AgentRequest request = mock(AgentRequest.class);

        StepVerifier.create(agent.stream(request))
                .expectNextCount(3)
                .verifyComplete();
    }
}
