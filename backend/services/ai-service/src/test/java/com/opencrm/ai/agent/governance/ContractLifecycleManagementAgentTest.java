package com.opencrm.ai.agent.governance;

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
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ContractLifecycleManagementAgentTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private StreamingChatClient streamingChatClient;

    @InjectMocks
    private ContractLifecycleManagementAgent agent;

    @Test
    public void testInvoke() {
        AgentRequest request = mock(AgentRequest.class);
        when(request.getSystemPrompt()).thenReturn("Test prompt");
        when(request.getMessages()).thenReturn(List.of("Message 1"));

        ChatResponse chatResponse = new ChatResponse(List.of(new Generation("AI Response")));
        when(chatClient.call(any(Prompt.class))).thenReturn(chatResponse);

        AgentResponse response = agent.invoke(request);

        assertTrue(response.isSuccess());
        assertEquals("AI Response", response.getOutput());
        assertEquals("Automated", response.getMetadata().get("autonomy"));
    }

    @Test
    public void testStream() {
        AgentRequest request = mock(AgentRequest.class);
        when(request.getSystemPrompt()).thenReturn("Test prompt");
        when(request.getMessages()).thenReturn(List.of("Message 1"));

        ChatResponse response1 = new ChatResponse(List.of(new Generation("AI ")));
        ChatResponse response2 = new ChatResponse(List.of(new Generation("Response")));
        when(streamingChatClient.stream(any(Prompt.class))).thenReturn(Flux.just(response1, response2));

        Flux<String> resultStream = agent.stream(request);

        StepVerifier.create(resultStream)
                .expectNext("AI ")
                .expectNext("Response")
                .verifyComplete();
    }
}
