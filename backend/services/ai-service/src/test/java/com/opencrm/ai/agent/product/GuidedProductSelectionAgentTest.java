package com.opencrm.ai.agent.product;

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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
    void testInvokeReturnsSimulatedResponse() {
        ChatResponse chatResponse = mock(ChatResponse.class);
        Generation generation = new org.springframework.ai.chat.Generation("Simulated execution for Guided Product Selection Agent");
        when(chatResponse.getResult()).thenReturn(generation);
        when(chatClient.call(any(Prompt.class))).thenReturn(chatResponse);

        AgentResponse response = agent.invoke(mock(AgentRequest.class));

        assertTrue(response.isSuccess());
        assertEquals("Simulated execution for Guided Product Selection Agent", response.getOutput());
        assertEquals("Advisory", response.getMetadata().get("autonomy"));
        assertEquals("cpq-agent-001", response.getMetadata().get("agentId"));
    }

    @Test
    void testStreamReturnsFixedChunks() {
        ChatResponse chatResponse1 = mock(ChatResponse.class);
        ChatResponse chatResponse2 = mock(ChatResponse.class);
        ChatResponse chatResponse3 = mock(ChatResponse.class);
        when(chatResponse1.getResult()).thenReturn(new org.springframework.ai.chat.Generation("Starting stream for Guided Product Selection Agent..."));
        when(chatResponse2.getResult()).thenReturn(new org.springframework.ai.chat.Generation("Processing..."));
        when(chatResponse3.getResult()).thenReturn(new org.springframework.ai.chat.Generation("Done"));

        when(streamingChatClient.stream(any(Prompt.class))).thenReturn(Flux.just(chatResponse1, chatResponse2, chatResponse3));

        Flux<String> flux = agent.stream(mock(AgentRequest.class));

        StepVerifier.create(flux)
                .expectNext("Starting stream for Guided Product Selection Agent...")
                .expectNext("Processing...")
                .expectNext("Done")
                .verifyComplete();
    }
}
