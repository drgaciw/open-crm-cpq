package com.opencrm.ai.llm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAiLlmAdapterTest {

    private ChatClient chatClient;
    private StreamingChatClient streamingChatClient;
    private SpringAiLlmAdapter adapter;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        streamingChatClient = mock(StreamingChatClient.class);
        adapter = new SpringAiLlmAdapter(chatClient, streamingChatClient);
    }

    @Test
    void generateBuildsPromptAndReturnsContent() {
        when(chatClient.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation("adapter output"))));

        String output = adapter.generate("system rules", "user task");

        assertEquals("adapter output", output);

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatClient).call(promptCaptor.capture());
        Prompt prompt = promptCaptor.getValue();
        assertEquals(2, prompt.getInstructions().size());
        assertInstanceOf(SystemMessage.class, prompt.getInstructions().get(0));
        assertInstanceOf(UserMessage.class, prompt.getInstructions().get(1));
    }

    @Test
    void streamReturnsResponseContentChunks() {
        when(streamingChatClient.stream(any(Prompt.class))).thenReturn(Flux.just(
                new ChatResponse(List.of(new Generation("first "))),
                new ChatResponse(List.of(new Generation("second")))
        ));

        StepVerifier.create(adapter.stream("system rules", "user task"))
                .expectNext("first ")
                .expectNext("second")
                .verifyComplete();
    }
}
