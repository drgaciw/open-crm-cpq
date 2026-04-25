package com.opencrm.ai.agent.intelligence;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.ChatResponse;
import com.opencrm.ai.agent.AgentRequest;
import com.opencrm.ai.agent.AgentResponse;
import com.opencrm.ai.agent.CpqAgent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service("cpq-agent-029")
public class UpgradeDowngradePathAgent implements CpqAgent {

    private final ChatClient chatClient;
    private final StreamingChatClient streamingChatClient;

    public UpgradeDowngradePathAgent(ChatClient chatClient, StreamingChatClient streamingChatClient) {
        this.chatClient = chatClient;
        this.streamingChatClient = streamingChatClient;
    }

    @Override
    public String getAgentId() {
        return "cpq-agent-029";
    }

    @Override
    public String getName() {
        return "Upgrade/Downgrade Path Agent";
    }

    @Override
    public String getDescription() {
        return "Identify upgrade/downgrade opportunities for customers";
    }

    private Prompt buildPrompt(AgentRequest request) {
        List<Message> messages = new ArrayList<>();
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()) {
            messages.add(new SystemMessage(request.getSystemPrompt()));
        } else {
            messages.add(new SystemMessage("You are an AI assistant that suggests upgrade and downgrade paths for customer subscriptions at renewal."));
        }

        if (request.getMessages() != null) {
            for (String msg : request.getMessages()) {
                messages.add(new UserMessage(msg));
            }
        }

        if (request.getContext() != null && !request.getContext().isEmpty()) {
            String contextStr = request.getContext().entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining("\n"));
            messages.add(new UserMessage("Context:\n" + contextStr));
        }

        return new Prompt(messages);
    }

    @Override
    public AgentResponse invoke(AgentRequest request) {
        Prompt prompt = buildPrompt(request);
        ChatResponse response = chatClient.call(prompt);

        String output = "";
        if (response != null && response.getResult() != null && response.getResult().getOutput() != null) {
            output = response.getResult().getOutput().getContent();
        }

        final String finalOutput = output;

        return new AgentResponse() {
            @Override
            public boolean isSuccess() { return true; }
            @Override
            public String getOutput() { return finalOutput; }
            @Override
            public Map<String, Object> getMetadata() { return Map.of("autonomy", "Automated", "model", "azure-openai"); }
        };
    }

    @Override
    public Flux<String> stream(AgentRequest request) {
        Prompt prompt = buildPrompt(request);
        return streamingChatClient.stream(prompt)
                .map(response -> {
                    if (response != null && response.getResult() != null && response.getResult().getOutput() != null && response.getResult().getOutput().getContent() != null) {
                        return response.getResult().getOutput().getContent();
                    }
                    return "";
                })
                .filter(content -> !content.isEmpty());
    }
}
