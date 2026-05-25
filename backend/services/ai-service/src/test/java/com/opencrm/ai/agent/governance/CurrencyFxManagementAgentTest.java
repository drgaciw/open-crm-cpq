package com.opencrm.ai.agent.governance;

import com.opencrm.ai.agent.AgentRequest;
import com.opencrm.ai.agent.AgentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import org.springframework.ai.chat.Generation;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurrencyFxManagementAgentTest {

    private CurrencyFxManagementAgent agent;
    private ChatClient chatClient;
    private StreamingChatClient streamingChatClient;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        streamingChatClient = mock(StreamingChatClient.class);
        agent = new CurrencyFxManagementAgent(chatClient, streamingChatClient);
    }

    @Test
    void testGetAgentIdAndName() {
        assertEquals("cpq-agent-032", agent.getAgentId());
        assertEquals("Currency & FX Management Agent", agent.getName());
        assertNotNull(agent.getDescription());
    }

    @Test
    void testInvokeReturnsLlmResponse() {
        AgentRequest request = mock(AgentRequest.class);
        when(request.getSystemPrompt()).thenReturn("System prompt");
        when(request.getMessages()).thenReturn(List.of("Message 1"));

        AssistantMessage assistantMessage = new AssistantMessage("LLM Response for Currency & FX");
        Generation generation = new Generation("LLM Response for Currency & FX");
        ChatResponse chatResponse = new ChatResponse(List.of(generation));
        when(chatClient.call(any(Prompt.class))).thenReturn(chatResponse);

        AgentResponse response = agent.invoke(request);

        assertTrue(response.isSuccess());
        assertEquals("LLM Response for Currency & FX", response.getOutput());
        assertEquals("Automated", response.getMetadata().get("autonomy"));
        assertEquals("cpq-agent-032", response.getMetadata().get("agentId"));
    }

    @Test
    void testStreamReturnsLlmChunks() {
        AgentRequest request = mock(AgentRequest.class);
        when(request.getSystemPrompt()).thenReturn("System prompt");
        when(request.getMessages()).thenReturn(List.of("Message 1"));

        AssistantMessage m1 = new AssistantMessage("Chunk 1");
        AssistantMessage m2 = new AssistantMessage("Chunk 2");
        ChatResponse r1 = new ChatResponse(List.of(new Generation("Chunk 1")));
        ChatResponse r2 = new ChatResponse(List.of(new Generation("Chunk 2")));

        when(streamingChatClient.stream(any(Prompt.class))).thenReturn(Flux.just(r1, r2));

        Flux<String> flux = agent.stream(request);

        StepVerifier.create(flux)
                .expectNext("Chunk 1")
                .expectNext("Chunk 2")
                .verifyComplete();
    }
}