package com.opencrm.ai.agent.governance;

import com.opencrm.ai.agent.AgentRequest;
import com.opencrm.ai.agent.AgentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import org.springframework.ai.chat.Generation;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurrencyFxManagementAgentTest {

    private CurrencyFxManagementAgent agent;

    @Mock
    private ChatClient chatClient;

    @Mock
    private StreamingChatClient streamingChatClient;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        agent = new CurrencyFxManagementAgent(chatClient, streamingChatClient);
    }

    @Test
    void testGetAgentIdAndName() {
        assertEquals("cpq-agent-032", agent.getAgentId());
        assertEquals("Currency & FX Management Agent", agent.getName());
        assertNotNull(agent.getDescription());
    }

    @Test
    void testInvokeSuccess() {
        AgentRequest request = mock(AgentRequest.class);
        when(request.getSystemPrompt()).thenReturn("System Prompt");
        when(request.getMessages()).thenReturn(List.of("User message"));

        ChatResponse mockResponse = mock(ChatResponse.class);
        Generation mockGeneration = mock(Generation.class);
        org.springframework.ai.chat.messages.AssistantMessage assistantMessage = mock(org.springframework.ai.chat.messages.AssistantMessage.class);

        when(chatClient.call(any(Prompt.class))).thenReturn(mockResponse);
        when(mockResponse.getResult()).thenReturn(mockGeneration);
        when(mockGeneration.getOutput()).thenReturn(assistantMessage);
        when(assistantMessage.getContent()).thenReturn("Simulated LLM execution");

        AgentResponse response = agent.invoke(request);

        assertTrue(response.isSuccess());
        assertEquals("Simulated LLM execution", response.getOutput());
        assertEquals("Automated", response.getMetadata().get("autonomy"));
    }

    @Test
    void testInvokeError() {
        AgentRequest request = mock(AgentRequest.class);
        when(chatClient.call(any(Prompt.class))).thenThrow(new RuntimeException("API error"));

        AgentResponse response = agent.invoke(request);

        assertFalse(response.isSuccess());
        assertTrue(response.getOutput().contains("API error"));
        assertEquals("Automated", response.getMetadata().get("autonomy"));
        assertEquals("API error", response.getMetadata().get("error"));
    }

    @Test
    void testStreamSuccess() {
        AgentRequest request = mock(AgentRequest.class);

        ChatResponse mockResponse = mock(ChatResponse.class);
        Generation mockGeneration = mock(Generation.class);
        org.springframework.ai.chat.messages.AssistantMessage assistantMessage = mock(org.springframework.ai.chat.messages.AssistantMessage.class);

        when(mockResponse.getResult()).thenReturn(mockGeneration);
        when(mockGeneration.getOutput()).thenReturn(assistantMessage);
        when(assistantMessage.getContent()).thenReturn("Chunk 1");

        when(streamingChatClient.stream(any(Prompt.class))).thenReturn(Flux.just(mockResponse));

        Flux<String> flux = agent.stream(request);

        StepVerifier.create(flux)
                .expectNext("Chunk 1")
                .verifyComplete();
    }

    @Test
    void testStreamError() {
        AgentRequest request = mock(AgentRequest.class);
        when(streamingChatClient.stream(any(Prompt.class))).thenReturn(Flux.error(new RuntimeException("Stream error")));

        Flux<String> flux = agent.stream(request);

        StepVerifier.create(flux)
                .expectNext("Error streaming Currency & FX Management Agent: Stream error")
                .verifyComplete();
    }
}
