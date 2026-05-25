package com.opencrm.ai.agent.governance;

import com.opencrm.ai.agent.AgentRequest;
import com.opencrm.ai.agent.AgentResponse;
import com.opencrm.ai.agent.DefaultAgentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.StreamingChatClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompetitivePricingIntelligenceAgentTest {

    private CompetitivePricingIntelligenceAgent agent;
    private ChatClient chatClient;
    private StreamingChatClient streamingChatClient;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        streamingChatClient = mock(StreamingChatClient.class);
        agent = new CompetitivePricingIntelligenceAgent(chatClient, streamingChatClient);
    }

    @Test
    void testGetAgentIdAndName() {
        assertEquals("cpq-agent-034", agent.getAgentId());
        assertEquals("Competitive Pricing Intelligence Agent", agent.getName());
        assertNotNull(agent.getDescription());
    }

    @Test
    void testInvokeReturnsActualResponse() {
        ChatResponse mockChatResponse = new ChatResponse(List.of(new Generation("Simulated real response from Competitive Pricing Intelligence Agent")));
        when(chatClient.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(mockChatResponse);

        AgentRequest request = new DefaultAgentRequest(null, List.of("Execute task"), null);

        AgentResponse response = agent.invoke(request);

        assertTrue(response.isSuccess());
        assertEquals("Simulated real response from Competitive Pricing Intelligence Agent", response.getOutput());
        assertEquals("Advisory", response.getMetadata().get("autonomy"));
        assertEquals("cpq-agent-034", response.getMetadata().get("agentId"));
    }

    @Test
    void testStreamReturnsActualResponse() {
        ChatResponse mockResponsePart1 = new ChatResponse(List.of(new Generation("Starting stream...")));
        ChatResponse mockResponsePart2 = new ChatResponse(List.of(new Generation(" Done.")));

        when(streamingChatClient.stream(any(org.springframework.ai.chat.prompt.Prompt.class)))
            .thenReturn(Flux.just(mockResponsePart1, mockResponsePart2));

        AgentRequest request = new DefaultAgentRequest(null, List.of("Execute task"), null);

        Flux<String> flux = agent.stream(request);

        StepVerifier.create(flux)
                .expectNext("Starting stream...")
                .expectNext(" Done.")
                .verifyComplete();
    }
}
