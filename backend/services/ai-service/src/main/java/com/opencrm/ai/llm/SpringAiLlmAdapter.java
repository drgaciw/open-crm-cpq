package com.opencrm.ai.llm;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Component
public class SpringAiLlmAdapter implements LlmAdapter {

    private final ChatClient chatClient;
    private final StreamingChatClient streamingChatClient;

    public SpringAiLlmAdapter(ChatClient chatClient, StreamingChatClient streamingChatClient) {
        this.chatClient = chatClient;
        this.streamingChatClient = streamingChatClient;
    }

    @Override
    public String generate(String systemPrompt, String userMessage) {
        return chatClient.call(toPrompt(systemPrompt, userMessage))
                .getResult()
                .getOutput()
                .getContent();
    }

    @Override
    public Flux<String> stream(String systemPrompt, String userMessage) {
        return streamingChatClient.stream(toPrompt(systemPrompt, userMessage))
                .filter(response -> response.getResult() != null
                        && response.getResult().getOutput() != null
                        && response.getResult().getOutput().getContent() != null)
                .map(response -> response.getResult().getOutput().getContent());
    }

    private Prompt toPrompt(String systemPrompt, String userMessage) {
        List<Message> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new SystemMessage(systemPrompt));
        }
        messages.add(new UserMessage(userMessage != null ? userMessage : ""));
        return new Prompt(messages);
    }
}
