package com.opencrm.ai.agent.intelligence;

import com.opencrm.ai.agent.AgentRequest;
import com.opencrm.ai.agent.AgentResponse;
import com.opencrm.ai.agent.DefaultAgentRequest;
import com.opencrm.ai.profile.SellingProfileContextProvider;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RecommendationUpsellAgentTest {

    private ChatClient chatClient;
    private StreamingChatClient streamingChatClient;
    private RecommendationUpsellAgent agent;
    private SellingProfileContextProvider sellingProfiles;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        streamingChatClient = mock(StreamingChatClient.class);
        sellingProfiles = mock(SellingProfileContextProvider.class);
        agent = new RecommendationUpsellAgent(chatClient, streamingChatClient, sellingProfiles);
    }

    @Test
    void streamUsesAuthenticatedTenantProfileAsUserReferenceData() {
        var jwt = Jwt.withTokenValue("test").header("alg", "none")
                .subject("actor").claim("tid", "workspace-a").build();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        SecurityContextHolder.setContext(context);
        try {
            String profile = "Workspace selling profile: example products";
            when(sellingProfiles.promptBlock("workspace-a")).thenReturn(profile);
            when(streamingChatClient.stream(any(Prompt.class))).thenReturn(Flux.empty());
            agent.stream(new DefaultAgentRequest("System instructions", List.of("Suggest an upsell"),
                    Map.of("tenantId", "workspace-b")));

            var prompt = ArgumentCaptor.forClass(Prompt.class);
            verify(streamingChatClient).stream(prompt.capture());
            var messages = prompt.getValue().getInstructions();
            assertEquals(3, messages.size());
            assertInstanceOf(SystemMessage.class, messages.get(0));
            assertEquals(profile, assertInstanceOf(UserMessage.class, messages.get(1)).getContent());
            assertEquals("Suggest an upsell", messages.get(2).getContent());
            verify(sellingProfiles).promptBlock("workspace-a");
            verifyNoMoreInteractions(sellingProfiles);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void testGetAgentIdAndName() {
        assertEquals("cpq-agent-017", agent.getAgentId());
        assertEquals("Recommendation & Upsell Agent", agent.getName());
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
        when(assistantMessage.getContent()).thenReturn("Upsell opportunity: Upgrade to Enterprise plan (+$500/mo)");

        AgentRequest request = mock(AgentRequest.class);
        when(request.getSystemPrompt()).thenReturn("System context");
        when(request.getMessages()).thenReturn(List.of("Suggest upsell opportunities"));

        AgentResponse response = agent.invoke(request);

        assertTrue(response.isSuccess());
        assertEquals("Upsell opportunity: Upgrade to Enterprise plan (+$500/mo)", response.getOutput());
    }

    @Test
    void testInvokeFailure() {
        when(chatClient.call(any(Prompt.class))).thenThrow(new RuntimeException("Network error"));

        AgentRequest request = mock(AgentRequest.class);
        when(request.getSystemPrompt()).thenReturn(null);
        when(request.getMessages()).thenReturn(List.of("Suggest upsell opportunities"));

        // RecommendationUpsellAgent does not wrap in try-catch, so exception propagates
        assertThrows(RuntimeException.class, () -> agent.invoke(request));
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
        when(request.getMessages()).thenReturn(List.of("Stream upsell recommendations"));

        Flux<String> result = agent.stream(request);

        StepVerifier.create(result)
                .expectNext("Chunk 1")
                .expectNext("Chunk 2")
                .verifyComplete();
    }
}
