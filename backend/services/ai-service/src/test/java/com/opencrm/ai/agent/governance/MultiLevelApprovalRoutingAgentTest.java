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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MultiLevelApprovalRoutingAgentTest {

    private ChatClient chatClient;
    private MultiLevelApprovalRoutingAgent agent;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        agent = new MultiLevelApprovalRoutingAgent(chatClient);
    }

    @Test
    void testGetAgentIdAndName() {
        assertEquals("cpq-agent-024", agent.getAgentId());
        assertEquals("Multi-Level Approval Routing Agent", agent.getName());
        assertNotNull(agent.getDescription());
    }

    @Test
    void testInvokeSuccess() {
        AgentRequest request = mock(AgentRequest.class);
        when(request.getSystemPrompt()).thenReturn("You are an approval routing agent.");
        when(request.getMessages()).thenReturn(List.of("Route this quote for approval."));

        String mockResponseContent = "Quote routed for multi-level approval.";
        Generation generation = new Generation(mockResponseContent);
        ChatResponse chatResponse = new ChatResponse(List.of(generation));

        when(chatClient.call(any(Prompt.class))).thenReturn(chatResponse);

        AgentResponse response = agent.invoke(request);

        assertTrue(response.isSuccess());
        assertEquals(mockResponseContent, response.getOutput());

        Map<String, Object> metadata = response.getMetadata();
        assertEquals("Automated", metadata.get("autonomy"));
    }

    @Test
    void testInvokePropagatesLlmFailure() {
        AgentRequest request = mock(AgentRequest.class);
        when(request.getSystemPrompt()).thenReturn("You are an approval routing agent.");
        when(request.getMessages()).thenReturn(List.of("Route this quote for approval."));

        when(chatClient.call(any(Prompt.class))).thenThrow(new RuntimeException("LLM error"));

        assertThrows(RuntimeException.class, () -> agent.invoke(request));
    }

    @Test
    void testStreamStubSequence() {
        StepVerifier.create(agent.stream(mock(AgentRequest.class)))
                .expectNextMatches(s -> s.contains("Multi-Level Approval Routing Agent"))
                .expectNext("Processing...")
                .expectNext("Done")
                .verifyComplete();
    }
}
