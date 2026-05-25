package com.opencrm.ai.agent.governance;

import com.opencrm.ai.agent.AgentRequest;
import com.opencrm.ai.agent.AgentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

class TaxCalculationIntegrationAgentTest {

    private TaxCalculationIntegrationAgent agent;
    private ChatClient chatClient;
    private StreamingChatClient streamingChatClient;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        streamingChatClient = mock(StreamingChatClient.class);
        agent = new TaxCalculationIntegrationAgent(chatClient, streamingChatClient);
    }

    @Test
    void testGetAgentIdAndName() {
        assertEquals("cpq-agent-033", agent.getAgentId());
        assertEquals("Tax Calculation Integration Agent", agent.getName());
        assertNotNull(agent.getDescription());
    }

    @Test
    void testInvokeSuccess() {
        AgentRequest request = mock(AgentRequest.class);
        when(request.getSystemPrompt()).thenReturn("You are a tax expert.");
        when(request.getMessages()).thenReturn(List.of("Calculate tax for this deal."));

        ChatResponse mockResponse = mock(ChatResponse.class);
        Generation mockGeneration = mock(Generation.class);
        AssistantMessage mockMessage = new AssistantMessage("Tax calculated successfully.");

        when(mockGeneration.getOutput()).thenReturn(mockMessage);
        when(mockResponse.getResult()).thenReturn(mockGeneration);
        when(chatClient.call(any(Prompt.class))).thenReturn(mockResponse);

        AgentResponse response = agent.invoke(request);

        assertTrue(response.isSuccess());
        assertEquals("Tax calculated successfully.", response.getOutput());
        assertEquals("Automated", response.getMetadata().get("autonomy"));
        assertEquals("cpq-agent-033", response.getMetadata().get("agentId"));

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatClient).call(promptCaptor.capture());
        Prompt capturedPrompt = promptCaptor.getValue();
        assertEquals(2, capturedPrompt.getInstructions().size());
    }

    @Test
    void testInvokeError() {
        AgentRequest request = mock(AgentRequest.class);
        when(chatClient.call(any(Prompt.class))).thenThrow(new RuntimeException("API Connection Error"));

        AgentResponse response = agent.invoke(request);

        assertFalse(response.isSuccess());
        assertTrue(response.getOutput().contains("API Connection Error"));
        assertEquals("Automated", response.getMetadata().get("autonomy"));
        assertEquals("cpq-agent-033", response.getMetadata().get("agentId"));
        assertEquals("API Connection Error", response.getMetadata().get("error"));
    }

    @Test
    void testStreamSuccess() {
        AgentRequest request = mock(AgentRequest.class);

        ChatResponse mockResponse1 = mock(ChatResponse.class);
        Generation mockGeneration1 = mock(Generation.class);
        when(mockGeneration1.getOutput()).thenReturn(new AssistantMessage("Calculating... "));
        when(mockResponse1.getResult()).thenReturn(mockGeneration1);

        ChatResponse mockResponse2 = mock(ChatResponse.class);
        Generation mockGeneration2 = mock(Generation.class);
        when(mockGeneration2.getOutput()).thenReturn(new AssistantMessage("Done."));
        when(mockResponse2.getResult()).thenReturn(mockGeneration2);

        when(streamingChatClient.stream(any(Prompt.class))).thenReturn(Flux.just(mockResponse1, mockResponse2));

        Flux<String> flux = agent.stream(request);

        StepVerifier.create(flux)
                .expectNext("Calculating... ")
                .expectNext("Done.")
                .verifyComplete();
    }
}
