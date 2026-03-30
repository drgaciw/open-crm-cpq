package com.opencrm.ai.agent.intelligence;

import com.opencrm.ai.agent.AgentRequest;
import com.opencrm.ai.agent.AgentResponse;
import com.opencrm.ai.agent.CpqAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service("cpq-agent-027")
public class MarginOptimizationAgent implements CpqAgent {

    private static final Logger log = LoggerFactory.getLogger(MarginOptimizationAgent.class);

    private final ChatClient chatClient;
    private final StreamingChatClient streamingChatClient;

    public MarginOptimizationAgent(ChatClient chatClient, StreamingChatClient streamingChatClient) {
        this.chatClient = chatClient;
        this.streamingChatClient = streamingChatClient;
    }

    @Override
    public String getAgentId() {
        return "cpq-agent-027";
    }

    @Override
    public String getName() {
        return "Margin Optimization Agent";
    }

    @Override
    public String getDescription() {
        return "Suggesting price adjustments for better margins";
    }

    @Override
    public AgentResponse invoke(AgentRequest request) {
        try {
            List<Message> messages = new ArrayList<>();
            if (request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()) {
                messages.add(new SystemMessage(request.getSystemPrompt()));
            }

            if (request.getMessages() != null && !request.getMessages().isEmpty()) {
                for (String msg : request.getMessages()) {
                    messages.add(new UserMessage(msg));
                }
            } else {
                messages.add(new UserMessage("Suggest optimal deal margins and pricing levers."));
            }

            Prompt prompt = new Prompt(messages);
            ChatResponse chatResponse = chatClient.call(prompt);
            String output = chatResponse.getResult().getOutput().getContent();

            return new AgentResponse() {
                @Override
                public boolean isSuccess() { return true; }
                @Override
                public String getOutput() { return output; }
                @Override
                public Map<String, Object> getMetadata() { return Map.of("autonomy", "Automated"); }
            };
        } catch (Exception e) {
            log.error("Error executing Margin Optimization Agent (invoke): {}", e.getMessage(), e);
            String errorMessage = e.getMessage() != null ? e.getMessage() : "Unknown error occurred";
            return new AgentResponse() {
                @Override
                public boolean isSuccess() { return false; }
                @Override
                public String getOutput() { return "Failed to execute Margin Optimization Agent. Error: " + errorMessage; }
                @Override
                public Map<String, Object> getMetadata() { return Map.of("autonomy", "Automated", "error", errorMessage); }
            };
        }
    }

    @Override
    public Flux<String> stream(AgentRequest request) {
        try {
            List<Message> messages = new ArrayList<>();
            if (request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()) {
                messages.add(new SystemMessage(request.getSystemPrompt()));
            }

            if (request.getMessages() != null && !request.getMessages().isEmpty()) {
                for (String msg : request.getMessages()) {
                    messages.add(new UserMessage(msg));
                }
            } else {
                messages.add(new UserMessage("Suggest optimal deal margins and pricing levers."));
            }

            Prompt prompt = new Prompt(messages);
            return streamingChatClient.stream(prompt)
                    .filter(response -> response.getResult() != null && response.getResult().getOutput() != null && response.getResult().getOutput().getContent() != null)
                    .map(response -> response.getResult().getOutput().getContent())
                    .onErrorResume(e -> {
                        log.error("Error executing Margin Optimization Agent (stream): {}", e.getMessage(), e);
                        String errorMessage = e.getMessage() != null ? e.getMessage() : "Unknown error occurred";
                        return Flux.just("Error during Margin Optimization Agent streaming: " + errorMessage);
                    });
        } catch (Exception e) {
            log.error("Error setting up Margin Optimization Agent stream: {}", e.getMessage(), e);
            String errorMessage = e.getMessage() != null ? e.getMessage() : "Unknown error occurred";
            return Flux.just("Failed to start Margin Optimization Agent stream. Error: " + errorMessage);
        }
    }
}
