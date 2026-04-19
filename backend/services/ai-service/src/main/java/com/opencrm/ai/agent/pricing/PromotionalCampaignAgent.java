package com.opencrm.ai.agent.pricing;

import com.opencrm.ai.agent.AgentRequest;
import com.opencrm.ai.agent.AgentResponse;
import com.opencrm.ai.agent.CpqAgent;
import org.springframework.ai.chat.ChatClient;
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

@Service("cpq-agent-009")
public class PromotionalCampaignAgent implements CpqAgent {

    private final ChatClient chatClient;
    private final StreamingChatClient streamingChatClient;

    public PromotionalCampaignAgent(ChatClient chatClient, StreamingChatClient streamingChatClient) {
        this.chatClient = chatClient;
        this.streamingChatClient = streamingChatClient;
    }

    @Override
    public String getAgentId() {
        return "cpq-agent-009";
    }

    @Override
    public String getName() {
        return "Promotional Campaign Agent";
    }

    @Override
    public String getDescription() {
        return "Time-limited promotions and campaign-based pricing";
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
                messages.add(new UserMessage("Apply time-limited promotions and campaign-based pricing rules."));
            }
            Prompt prompt = new Prompt(messages);
            var response = chatClient.call(prompt);
            String output = response.getResult().getOutput().getContent();
            return new AgentResponse() {
                @Override
                public boolean isSuccess() { return true; }
                @Override
                public String getOutput() { return output; }
                @Override
                public Map<String, Object> getMetadata() { return Map.of("autonomy", "Semi-autonomous"); }
            };
        } catch (Exception e) {
            String errorMsg = "Error invoking Promotional Campaign Agent: " + e.getMessage();
            return new AgentResponse() {
                @Override
                public boolean isSuccess() { return false; }
                @Override
                public String getOutput() { return errorMsg; }
                @Override
                public Map<String, Object> getMetadata() { return Map.of(); }
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
                messages.add(new UserMessage("Apply time-limited promotions and campaign-based pricing rules."));
            }
            Prompt prompt = new Prompt(messages);
            return streamingChatClient.stream(prompt)
                    .filter(response -> response.getResult() != null && response.getResult().getOutput() != null)
                    .map(response -> response.getResult().getOutput().getContent())
                    .onErrorResume(e -> Flux.just("Error streaming Promotional Campaign Agent: " + e.getMessage()));
        } catch (Exception e) {
            return Flux.just("Error streaming Promotional Campaign Agent: " + e.getMessage());
        }
    }
}
