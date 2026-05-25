package com.opencrm.ai.agent.governance;

import com.opencrm.ai.agent.AgentRequest;
import com.opencrm.ai.agent.AgentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CompetitivePricingIntelligenceAgentTest {

    private ChatClient chatClient;
    private StreamingChatClient streamingChatClient;
    private CompetitivePricingIntelligenceAgent agent;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        streamingChatClient = mock(StreamingChatClient.class);
        agent = new CompetitivePricingIntelligenceAgent(chatClient, streamingChatClient);
    }

    @Test
    void testGetAgentIdAndName() {
        assertEquals("cpq-agent-034", agent.getAgentId());
        assertEquals("Competitive Pricing Intelligence Agent", agent.getName());
        assertNotNull(agent.getDescription());
    }

    @Test
    void testInvokeSuccess() {
        AgentRequest request = mock(AgentRequest.class);
        when(request.getSystemPrompt()).thenReturn("You are a pricing agent.");
        when(request.getMessages()).thenReturn(List.of("Analyze pricing."));

        String mockResponseContent = "Pricing analysis successful.";
        Generation generation = new Generation(mockResponseContent);
        ChatResponse chatResponse = new ChatResponse(List.of(generation));

        when(chatClient.call(any(Prompt.class))).thenReturn(chatResponse);

        AgentResponse response = agent.invoke(request);

        assertTrue(response.isSuccess());
        assertEquals(mockResponseContent, response.getOutput());

        Map<String, Object> metadata = response.getMetadata();
        assertEquals("Advisory", metadata.get("autonomy"));
        assertEquals("cpq-agent-034", metadata.get("agentId"));
    }

    @Test
    void testInvokeFailure() {
        AgentRequest request = mock(AgentRequest.class);
        when(request.getSystemPrompt()).thenReturn("You are a pricing agent.");
        when(request.getMessages()).thenReturn(List.of("Analyze pricing."));

        when(chatClient.call(any(Prompt.class))).thenThrow(new RuntimeException("LLM error"));

        AgentResponse response = agent.invoke(request);

        assertFalse(response.isSuccess());
        assertTrue(response.getOutput().contains("LLM error"));

        Map<String, Object> metadata = response.getMetadata();
        assertEquals("Advisory", metadata.get("autonomy"));
        assertEquals("cpq-agent-034", metadata.get("agentId"));
        assertEquals("LLM error", metadata.get("error"));
    }

    @Test
    void testStreamSuccess() {
        AgentRequest request = mock(AgentRequest.class);
        when(request.getSystemPrompt()).thenReturn("System prompt");
        when(request.getMessages()).thenReturn(List.of("User message"));

        ChatResponse chunk1 = new ChatResponse(List.of(new Generation("Starting stream for Competitive Pricing Intelligence Agent...")));
        ChatResponse chunk2 = new ChatResponse(List.of(new Generation("Processing...")));
        ChatResponse chunk3 = new ChatResponse(List.of(new Generation("Done")));

        when(streamingChatClient.stream(any(Prompt.class))).thenReturn(Flux.just(chunk1, chunk2, chunk3));

        Flux<String> resultFlux = agent.stream(request);

        StepVerifier.create(resultFlux)
                .expectNext("Starting stream for Competitive Pricing Intelligence Agent...")
                .expectNext("Processing...")
                .expectNext("Done")
                .verifyComplete();
    }
}
