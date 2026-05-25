package com.opencrm.ai.agent.product;

import com.opencrm.ai.agent.AgentRequest;
import com.opencrm.ai.agent.AgentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompatibilityRulesAgentTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private StreamingChatClient streamingChatClient;

    private CompatibilityRulesAgent agent;

    @BeforeEach
    void setUp() {
        agent = new CompatibilityRulesAgent(chatClient, streamingChatClient);
    }

    @Test
    void testGetAgentIdAndName() {
        assertEquals("cpq-agent-015", agent.getAgentId());
        assertEquals("Compatibility Rules Agent", agent.getName());
        assertNotNull(agent.getDescription());
    }

    @Test
    void testInvokeSuccess() {
        AgentRequest request = mock(AgentRequest.class);
        when(request.getMessages()).thenReturn(List.of("Test message"));

        ChatResponse mockResponse = mock(ChatResponse.class);
        Generation mockGeneration = mock(Generation.class);
        org.springframework.ai.chat.messages.AssistantMessage mockAssistantMessage = mock(org.springframework.ai.chat.messages.AssistantMessage.class);

        when(chatClient.call(any(Prompt.class))).thenReturn(mockResponse);
        when(mockResponse.getResult()).thenReturn(mockGeneration);
        when(mockGeneration.getOutput()).thenReturn(mockAssistantMessage);
        when(mockAssistantMessage.getContent()).thenReturn("Valid compatibility rules");

        AgentResponse response = agent.invoke(request);

        assertTrue(response.isSuccess());
        assertEquals("Valid compatibility rules", response.getOutput());
        assertEquals("Automated", response.getMetadata().get("autonomy"));
        assertEquals("cpq-agent-015", response.getMetadata().get("agentId"));

        verify(chatClient).call(any(Prompt.class));
    }

    @Test
    void testInvokeError() {
        AgentRequest request = mock(AgentRequest.class);
        when(chatClient.call(any(Prompt.class))).thenThrow(new RuntimeException("LLM failure"));

        AgentResponse response = agent.invoke(request);

        assertFalse(response.isSuccess());
        assertTrue(response.getOutput().contains("Error"));
        assertEquals("Automated", response.getMetadata().get("autonomy"));
        assertEquals("cpq-agent-015", response.getMetadata().get("agentId"));
        assertNotNull(response.getMetadata().get("error"));
    }

    @Test
    void testStreamSuccess() {
        AgentRequest request = mock(AgentRequest.class);

        ChatResponse mockResponse1 = mock(ChatResponse.class);
        Generation mockGen1 = mock(Generation.class);
        org.springframework.ai.chat.messages.AssistantMessage mockMsg1 = mock(org.springframework.ai.chat.messages.AssistantMessage.class);
        when(mockResponse1.getResult()).thenReturn(mockGen1);
        when(mockGen1.getOutput()).thenReturn(mockMsg1);
        when(mockMsg1.getContent()).thenReturn("Part 1");

        ChatResponse mockResponse2 = mock(ChatResponse.class);
        Generation mockGen2 = mock(Generation.class);
        org.springframework.ai.chat.messages.AssistantMessage mockMsg2 = mock(org.springframework.ai.chat.messages.AssistantMessage.class);
        when(mockResponse2.getResult()).thenReturn(mockGen2);
        when(mockGen2.getOutput()).thenReturn(mockMsg2);
        when(mockMsg2.getContent()).thenReturn(" Part 2");

        when(streamingChatClient.stream(any(Prompt.class))).thenReturn(Flux.just(mockResponse1, mockResponse2));

        Flux<String> flux = agent.stream(request);

        StepVerifier.create(flux)
                .expectNext("Part 1")
                .expectNext(" Part 2")
                .verifyComplete();
    }
}
