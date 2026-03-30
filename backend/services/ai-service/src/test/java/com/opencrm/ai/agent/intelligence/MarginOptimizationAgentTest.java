package com.opencrm.ai.agent.intelligence;

import com.opencrm.ai.agent.AgentRequest;
import com.opencrm.ai.agent.AgentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class MarginOptimizationAgentTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private StreamingChatClient streamingChatClient;

    private MarginOptimizationAgent agent;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        agent = new MarginOptimizationAgent(chatClient, streamingChatClient);
    }

    @Test
    public void testInvokeSuccess() {
        AgentRequest request = mock(AgentRequest.class);
        when(request.getSystemPrompt()).thenReturn("System Prompt");
        when(request.getMessages()).thenReturn(List.of("Message 1"));
        when(request.getContext()).thenReturn(Map.of("key", "value"));

        ChatResponse mockResponse = mock(ChatResponse.class);
        Generation mockGeneration = mock(Generation.class);
        AssistantMessage mockMessage = mock(AssistantMessage.class);

        when(mockMessage.getContent()).thenReturn("Optimization Plan");
        when(mockGeneration.getOutput()).thenReturn(mockMessage);
        when(mockResponse.getResult()).thenReturn(mockGeneration);
        when(chatClient.call(any(Prompt.class))).thenReturn(mockResponse);

        AgentResponse response = agent.invoke(request);

        assertTrue(response.isSuccess());
        assertEquals("Optimization Plan", response.getOutput());
        assertNotNull(response.getMetadata());
        assertEquals("Automated", response.getMetadata().get("autonomy"));

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatClient, times(1)).call(promptCaptor.capture());
    }

    @Test
    public void testInvokeError() {
        AgentRequest request = mock(AgentRequest.class);
        when(request.getMessages()).thenReturn(List.of("Message 1"));
        when(chatClient.call(any(Prompt.class))).thenThrow(new RuntimeException("API Error"));

        AgentResponse response = agent.invoke(request);

        assertFalse(response.isSuccess());
        assertTrue(response.getOutput().contains("Failed to execute"));
        assertEquals("API Error", response.getMetadata().get("error"));
    }

    @Test
    public void testStreamSuccess() {
        AgentRequest request = mock(AgentRequest.class);
        when(request.getSystemPrompt()).thenReturn("System Prompt");
        when(request.getMessages()).thenReturn(List.of("Message 1"));
        when(request.getContext()).thenReturn(Map.of("key", "value"));

        ChatResponse mockResponse1 = mock(ChatResponse.class);
        Generation mockGeneration1 = mock(Generation.class);
        AssistantMessage mockMessage1 = mock(AssistantMessage.class);
        when(mockMessage1.getContent()).thenReturn("Part 1 ");
        when(mockGeneration1.getOutput()).thenReturn(mockMessage1);
        when(mockResponse1.getResult()).thenReturn(mockGeneration1);

        ChatResponse mockResponse2 = mock(ChatResponse.class);
        Generation mockGeneration2 = mock(Generation.class);
        AssistantMessage mockMessage2 = mock(AssistantMessage.class);
        when(mockMessage2.getContent()).thenReturn("Part 2");
        when(mockGeneration2.getOutput()).thenReturn(mockMessage2);
        when(mockResponse2.getResult()).thenReturn(mockGeneration2);

        when(streamingChatClient.stream(any(Prompt.class))).thenReturn(Flux.just(mockResponse1, mockResponse2));

        Flux<String> resultFlux = agent.stream(request);

        StepVerifier.create(resultFlux)
                .expectNext("Part 1 ")
                .expectNext("Part 2")
                .verifyComplete();

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(streamingChatClient, times(1)).stream(promptCaptor.capture());
    }

    @Test
    public void testStreamError() {
        AgentRequest request = mock(AgentRequest.class);
        when(request.getMessages()).thenReturn(List.of("Message 1"));

        when(streamingChatClient.stream(any(Prompt.class))).thenReturn(Flux.error(new RuntimeException("Stream Error")));

        Flux<String> resultFlux = agent.stream(request);

        StepVerifier.create(resultFlux)
                .expectNextMatches(msg -> msg.contains("Error during Margin Optimization Agent streaming: Stream Error"))
                .verifyComplete();
    }
}
