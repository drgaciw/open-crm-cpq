package com.opencrm.ai.agent.intelligence;

import com.opencrm.ai.agent.AgentRequest;
import com.opencrm.ai.agent.AgentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MarginOptimizationAgentTest {

    private ChatClient chatClient;
    private StreamingChatClient streamingChatClient;
    private MarginOptimizationAgent agent;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        streamingChatClient = mock(StreamingChatClient.class);
        agent = new MarginOptimizationAgent(chatClient, streamingChatClient);
    }

    @Test
    void testGetAgentIdAndName() {
        assertEquals("cpq-agent-027", agent.getAgentId());
        assertEquals("Margin Optimization Agent", agent.getName());
        assertNotNull(agent.getDescription());
    }

    @Test
    void testInvokeSuccess() {
        ChatResponse chatResponse = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);
        org.springframework.ai.chat.messages.AssistantMessage assistantMessage =
                mock(org.springframework.ai.chat.messages.AssistantMessage.class);

        when(chatClient.call(any(Prompt.class))).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(assistantMessage.getContent()).thenReturn("Optimal margin: 32% with volume discount lever");

        AgentRequest request = mock(AgentRequest.class);
        when(request.getSystemPrompt()).thenReturn("System context");
        when(request.getMessages()).thenReturn(List.of("Optimize deal margin"));

        AgentResponse response = agent.invoke(request);

        assertTrue(response.isSuccess());
        assertEquals("Optimal margin: 32% with volume discount lever", response.getOutput());
    }

    @Test
    void testInvokeFailure() {
        when(chatClient.call(any(Prompt.class))).thenThrow(new RuntimeException("Model not available"));

        AgentRequest request = mock(AgentRequest.class);
        when(request.getSystemPrompt()).thenReturn(null);
        when(request.getMessages()).thenReturn(List.of("Optimize deal margin"));

        AgentResponse response = agent.invoke(request);

        assertFalse(response.isSuccess());
        assertNotNull(response.getOutput());
        assertTrue(response.getOutput().contains("Model not available"));
    }

    @Test
    void testStreamSuccess() {
        ChatResponse chunk1 = mock(ChatResponse.class);
        ChatResponse chunk2 = mock(ChatResponse.class);
        Generation gen1 = mock(Generation.class);
        Generation gen2 = mock(Generation.class);
        org.springframework.ai.chat.messages.AssistantMessage msg1 =
                mock(org.springframework.ai.chat.messages.AssistantMessage.class);
        org.springframework.ai.chat.messages.AssistantMessage msg2 =
                mock(org.springframework.ai.chat.messages.AssistantMessage.class);

        when(chunk1.getResult()).thenReturn(gen1);
        when(gen1.getOutput()).thenReturn(msg1);
        when(msg1.getContent()).thenReturn("Chunk 1");

        when(chunk2.getResult()).thenReturn(gen2);
        when(gen2.getOutput()).thenReturn(msg2);
        when(msg2.getContent()).thenReturn("Chunk 2");

        when(streamingChatClient.stream(any(Prompt.class))).thenReturn(Flux.just(chunk1, chunk2));

        AgentRequest request = mock(AgentRequest.class);
        when(request.getSystemPrompt()).thenReturn(null);
        when(request.getMessages()).thenReturn(List.of("Stream margin analysis"));

        Flux<String> result = agent.stream(request);

        StepVerifier.create(result)
                .expectNext("Chunk 1")
                .expectNext("Chunk 2")
                .verifyComplete();
    }
}
