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

class ProductSkuManagementAgentTest {

    private ProductSkuManagementAgent agent;
    private ChatClient chatClient;
    private StreamingChatClient streamingChatClient;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        streamingChatClient = mock(StreamingChatClient.class);
        agent = new ProductSkuManagementAgent(chatClient, streamingChatClient);
    }

    @Test
    void testGetAgentIdAndName() {
        assertEquals("cpq-agent-013", agent.getAgentId());
        assertEquals("Product SKU Management Agent", agent.getName());
        assertNotNull(agent.getDescription());
    }

    @Test
    void testInvokeReturnsResponse() {
        Generation generation = new Generation("Simulated execution for Product SKU Management Agent");
        ChatResponse chatResponse = new ChatResponse(List.of(generation));
        when(chatClient.call(any(Prompt.class))).thenReturn(chatResponse);

        AgentResponse response = agent.invoke(mock(AgentRequest.class));

        assertTrue(response.isSuccess());
        assertEquals("Simulated execution for Product SKU Management Agent", response.getOutput());
        assertEquals("Advisory", response.getMetadata().get("autonomy"));
        assertEquals("cpq-agent-013", response.getMetadata().get("agentId"));
    }

    @Test
    void testStreamReturnsChunks() {
        Generation generation1 = new Generation("Starting stream for Product SKU Management Agent...");
        Generation generation2 = new Generation("Processing...");
        Generation generation3 = new Generation("Done");

        ChatResponse chatResponse1 = new ChatResponse(List.of(generation1));
        ChatResponse chatResponse2 = new ChatResponse(List.of(generation2));
        ChatResponse chatResponse3 = new ChatResponse(List.of(generation3));

        when(streamingChatClient.stream(any(Prompt.class)))
                .thenReturn(Flux.just(chatResponse1, chatResponse2, chatResponse3));

        Flux<String> flux = agent.stream(mock(AgentRequest.class));

        StepVerifier.create(flux)
                .expectNext("Starting stream for Product SKU Management Agent...")
                .expectNext("Processing...")
                .expectNext("Done")
                .verifyComplete();
    }
}
