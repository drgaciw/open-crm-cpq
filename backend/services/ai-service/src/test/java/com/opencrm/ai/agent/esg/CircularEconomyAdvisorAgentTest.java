package com.opencrm.ai.agent.esg;

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

class CircularEconomyAdvisorAgentTest {

    private ChatClient chatClient;
    private StreamingChatClient streamingChatClient;
    private CircularEconomyAdvisorAgent agent;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        streamingChatClient = mock(StreamingChatClient.class);
        agent = new CircularEconomyAdvisorAgent(chatClient, streamingChatClient);
    }

    @Test
    void testGetAgentIdAndName() {
        assertEquals("cpq-agent-D-2", agent.getAgentId());
        assertEquals("Circular Economy Advisor", agent.getName());
        assertNotNull(agent.getDescription());
    }

    @Test
    void testInvokeSuccess() {
        AgentRequest request = mock(AgentRequest.class);
        when(request.getSystemPrompt()).thenReturn("You are a circular economy advisor agent.");
        when(request.getMessages()).thenReturn(List.of("Advise on circular economy strategy."));

        String mockResponseContent = "Circular economy advice generated.";
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
    void testInvokeFailure() {
        AgentRequest request = mock(AgentRequest.class);
        when(request.getSystemPrompt()).thenReturn("You are a circular economy advisor agent.");
        when(request.getMessages()).thenReturn(List.of("Advise on circular economy strategy."));

        when(chatClient.call(any(Prompt.class))).thenThrow(new RuntimeException("LLM error"));

        AgentResponse response = agent.invoke(request);

        assertFalse(response.isSuccess());
        assertTrue(response.getOutput().contains("LLM error"));
    }

    @Test
    void testStreamSuccess() {
        AgentRequest request = mock(AgentRequest.class);
        when(request.getSystemPrompt()).thenReturn("System prompt");
        when(request.getMessages()).thenReturn(List.of("User message"));

        ChatResponse chunk1 = new ChatResponse(List.of(new Generation("Circular ")));
        ChatResponse chunk2 = new ChatResponse(List.of(new Generation("economy advice.")));

        when(streamingChatClient.stream(any(Prompt.class))).thenReturn(Flux.just(chunk1, chunk2));

        Flux<String> resultFlux = agent.stream(request);

        StepVerifier.create(resultFlux)
                .expectNext("Circular ")
                .expectNext("economy advice.")
                .verifyComplete();
    }
}
