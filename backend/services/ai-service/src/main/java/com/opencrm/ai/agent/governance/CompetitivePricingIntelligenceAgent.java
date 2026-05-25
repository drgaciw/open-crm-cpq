package com.opencrm.ai.agent.governance;

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

@Service("cpq-agent-034")
public class CompetitivePricingIntelligenceAgent implements CpqAgent {

    private final ChatClient chatClient;
    private final StreamingChatClient streamingChatClient;

    public CompetitivePricingIntelligenceAgent(ChatClient chatClient, StreamingChatClient streamingChatClient) {
        this.chatClient = chatClient;
        this.streamingChatClient = streamingChatClient;
    }

    @Override
    public String getAgentId() {
        return "cpq-agent-034";
    }

    @Override
    public String getName() {
        return "Competitive Pricing Intelligence Agent";
    }

    @Override
    public String getDescription() {
        return "Competitive pricing analysis and positioning";
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
                messages.add(new UserMessage("Execute Competitive Pricing Intelligence task."));
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
                public Map<String, Object> getMetadata() {
                    return Map.of("autonomy", "Advisory", "agentId", getAgentId());
                }
            };
        } catch (Exception e) {
            String errorMsg = "Error invoking Competitive Pricing Intelligence Agent: " + e.getMessage();
            return new AgentResponse() {
                @Override
                public boolean isSuccess() { return false; }
                @Override
                public String getOutput() { return errorMsg; }
                @Override
                public Map<String, Object> getMetadata() {
                    return Map.of("autonomy", "Advisory", "agentId", getAgentId(), "error", e.getMessage() != null ? e.getMessage() : "Unknown error");
                }
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
                messages.add(new UserMessage("Execute Competitive Pricing Intelligence task."));
            }
            Prompt prompt = new Prompt(messages);
            return streamingChatClient.stream(prompt)
                    .filter(response -> response.getResult() != null && response.getResult().getOutput() != null)
                    .map(response -> response.getResult().getOutput().getContent())
                    .onErrorResume(e -> Flux.just("Error streaming Competitive Pricing Intelligence Agent: " + e.getMessage()));
        } catch (Exception e) {
            return Flux.just("Error streaming Competitive Pricing Intelligence Agent: " + e.getMessage());
        }
    }
}
