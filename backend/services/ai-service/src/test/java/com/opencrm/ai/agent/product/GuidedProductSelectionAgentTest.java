package com.opencrm.ai.agent.product;

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

class GuidedProductSelectionAgentTest {

    private ChatClient chatClient;
    private StreamingChatClient streamingChatClient;
    private GuidedProductSelectionAgent agent;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        streamingChatClient = mock(StreamingChatClient.class);
        agent = new GuidedProductSelectionAgent(chatClient, streamingChatClient);
    }

    @Test
    void testGetAgentIdAndName() {
        assertEquals("cpq-agent-001", agent.getAgentId());
        assertEquals("Guided Product Selection Agent", agent.getName());
        assertNotNull(agent.getDescription());
    }

    @Test
    void testInvokeSuccess() {
        AgentRequest request = mock(AgentRequest.class);
        when(request.getSystemPrompt()).thenReturn("You are a product selection agent.");
        when(request.getMessages()).thenReturn(List.of("Help me choose a server for 500 users."));

        String mockResponseContent = "Recommended: Enterprise Server X with 128GB RAM.";
        Generation generation = new Generation(mockResponseContent);
        ChatResponse chatResponse = new ChatResponse(List.of(generation));

        when(chatClient.call(any(Prompt.class))).thenReturn(chatResponse);

        AgentResponse response = agent.invoke(request);

        assertTrue(response.isSuccess());
        assertEquals(mockResponseContent, response.getOutput());

        Map<String, Object> metadata = response.getMetadata();
        assertEquals("Automated", metadata.get("autonomy"));
        assertEquals("cpq-agent-001", metadata.get("agentId"));

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatClient).call(promptCaptor.capture());

        Prompt capturedPrompt = promptCaptor.getValue();
        List<Message> messages = capturedPrompt.getInstructions();
        assertEquals(2, messages.size());
        assertTrue(messages.get(0) instanceof SystemMessage);
        assertTrue(messages.get(1) instanceof UserMessage);
    }

    @Test
    void testInvokeFailure() {
        AgentRequest request = mock(AgentRequest.class);
        when(request.getSystemPrompt()).thenReturn("You are a product selection agent.");
        when(request.getMessages()).thenReturn(List.of("Help me choose a product."));

        when(chatClient.call(any(Prompt.class))).thenThrow(new RuntimeException("API error"));

        AgentResponse response = agent.invoke(request);

        assertFalse(response.isSuccess());
        assertTrue(response.getOutput().contains("API error"));

        Map<String, Object> metadata = response.getMetadata();
        assertEquals("Automated", metadata.get("autonomy"));
        assertEquals("API error", metadata.get("error"));
    }

    @Test
    void testStreamSuccess() {
        AgentRequest request = mock(AgentRequest.class);
        when(request.getSystemPrompt()).thenReturn("System prompt");
        when(request.getMessages()).thenReturn(List.of("User message"));

        ChatResponse chunk1 = new ChatResponse(List.of(new Generation("Product ")));
        ChatResponse chunk2 = new ChatResponse(List.of(new Generation("selected.")));

        when(streamingChatClient.stream(any(Prompt.class))).thenReturn(Flux.just(chunk1, chunk2));

        Flux<String> resultFlux = agent.stream(request);

        StepVerifier.create(resultFlux)
                .expectNext("Product ")
                .expectNext("selected.")
                .verifyComplete();
    }
}
