package com.opencrm.ai.agent.subscription;

import com.opencrm.ai.agent.AgentRequest;
import com.opencrm.ai.agent.AgentResponse;
import com.opencrm.ai.agent.CpqAgent;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service("cpq-agent-021")
public class UsageBasedPricingAgent implements CpqAgent {

    private final ChatClient chatClient;
    private final StreamingChatClient streamingChatClient;

    public UsageBasedPricingAgent(ChatClient chatClient, StreamingChatClient streamingChatClient) {
        this.chatClient = chatClient;
        this.streamingChatClient = streamingChatClient;
    }

    @Override
    public String getAgentId() {
        return "cpq-agent-021";
    }

    @Override
    public String getName() {
        return "Usage-Based Pricing Agent";
    }

    @Override
    public String getDescription() {
        return "Metered/usage pricing and overage calculations";
    }

    @Override
    public AgentResponse invoke(AgentRequest request) {
        List<Message> messages = new ArrayList<>();
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            messages.add(new SystemMessage(request.getSystemPrompt()));
        }
        if (request.getMessages() != null) {
            for (String msg : request.getMessages()) {
                messages.add(new UserMessage(msg));
            }
        }

        Prompt prompt = new Prompt(messages);
        ChatResponse response = chatClient.call(prompt);
        String output = response.getResult().getOutput().getContent();

        return new AgentResponse() {
            @Override
            public boolean isSuccess() { return true; }
            @Override
            public String getOutput() { return output; }
            @Override
            public Map<String, Object> getMetadata() { return Map.of("autonomy", "Automated"); }
        };
    }

    @Override
    public Flux<String> stream(AgentRequest request) {
        List<Message> messages = new ArrayList<>();
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            messages.add(new SystemMessage(request.getSystemPrompt()));
        }
        if (request.getMessages() != null) {
            for (String msg : request.getMessages()) {
                messages.add(new UserMessage(msg));
            }
        }

        Prompt prompt = new Prompt(messages);
        return streamingChatClient.stream(prompt)
                .map(response -> (response.getResult() != null && response.getResult().getOutput() != null && response.getResult().getOutput().getContent() != null)
                        ? response.getResult().getOutput().getContent() : "");
    }
}
