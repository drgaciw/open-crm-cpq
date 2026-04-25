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

class WinProbabilityAnalysisAgentTest {

    private ChatClient chatClient;
    private StreamingChatClient streamingChatClient;
    private WinProbabilityAnalysisAgent agent;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        streamingChatClient = mock(StreamingChatClient.class);
        agent = new WinProbabilityAnalysisAgent(chatClient, streamingChatClient);
    }

    @Test
    void testGetAgentIdAndName() {
        assertEquals("cpq-agent-026", agent.getAgentId());
        assertEquals("Win Probability Analysis Agent", agent.getName());
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
        when(assistantMessage.getContent()).thenReturn("Win probability: 78% based on deal size and history");

        AgentRequest request = mock(AgentRequest.class);
        when(request.getSystemPrompt()).thenReturn("System context");
        when(request.getMessages()).thenReturn(List.of("Analyze win probability"));

        AgentResponse response = agent.invoke(request);

        assertTrue(response.isSuccess());
        assertEquals("Win probability: 78% based on deal size and history", response.getOutput());
    }

    @Test
    void testInvokeFailure() {
        when(chatClient.call(any(Prompt.class))).thenThrow(new RuntimeException("Service error"));

        AgentRequest request = mock(AgentRequest.class);
        when(request.getSystemPrompt()).thenReturn(null);
        when(request.getMessages()).thenReturn(List.of("Analyze win probability"));

        AgentResponse response = agent.invoke(request);

        assertFalse(response.isSuccess());
        assertNotNull(response.getOutput());
        assertTrue(response.getOutput().contains("Service error"));
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
        when(request.getMessages()).thenReturn(List.of("Stream win probability analysis"));

        Flux<String> result = agent.stream(request);

        StepVerifier.create(result)
                .expectNext("Chunk 1")
                .expectNext("Chunk 2")
                .verifyComplete();
    }
}
