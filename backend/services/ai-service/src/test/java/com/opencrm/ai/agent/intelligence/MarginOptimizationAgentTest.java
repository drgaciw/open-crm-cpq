package com.opencrm.ai.agent.intelligence;

import com.opencrm.ai.agent.AgentRequest;
import com.opencrm.ai.agent.AgentResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MarginOptimizationAgentTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private StreamingChatClient streamingChatClient;

    @InjectMocks
    private MarginOptimizationAgent agent;

    @Test
    void shouldInvokeChatClient() {
        AgentRequest request = new AgentRequest() {
            @Override
            public String getSystemPrompt() {
                return "system prompt";
            }

            @Override
            public List<String> getMessages() {
                return List.of("message 1");
            }

            @Override
            public Map<String, Object> getContext() {
                return Map.of("key", "value");
            }
        };

        ChatResponse mockResponse = new ChatResponse(List.of(new Generation("Optimized Margin!")));
        when(chatClient.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(mockResponse);

        AgentResponse response = agent.invoke(request);

        assertTrue(response.isSuccess());
        assertEquals("Optimized Margin!", response.getOutput());
        assertNotNull(response.getMetadata());
    }

    @Test
    void shouldStreamFromStreamingChatClient() {
        AgentRequest request = new AgentRequest() {
            @Override
            public String getSystemPrompt() {
                return "system prompt";
            }

            @Override
            public List<String> getMessages() {
                return List.of("message 1");
            }

            @Override
            public Map<String, Object> getContext() {
                return Map.of("key", "value");
            }
        };

        ChatResponse mockResponse = new ChatResponse(List.of(new Generation("Optimized Stream!")));
        when(streamingChatClient.stream(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(Flux.just(mockResponse));

        Flux<String> responseFlux = agent.stream(request);

        StringBuilder result = new StringBuilder();
        responseFlux.toIterable().forEach(result::append);

        assertEquals("Optimized Stream!", result.toString());
    }
}
