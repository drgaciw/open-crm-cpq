package com.opencrm.ai.agent.governance;

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
import java.util.HashMap;
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
                messages.add(new UserMessage("Execute Competitive Pricing Intelligence task based on the provided context."));
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
                public Map<String, Object> getMetadata() {
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("autonomy", "Advisory");
                    metadata.put("agentId", getAgentId());
                    return metadata;
                }
            };
        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown error";
            return new AgentResponse() {
                @Override
                public boolean isSuccess() { return false; }
                @Override
                public String getOutput() { return "Error executing Competitive Pricing Intelligence: " + errorMsg; }
                @Override
                public Map<String, Object> getMetadata() {
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("autonomy", "Advisory");
                    metadata.put("agentId", getAgentId());
                    metadata.put("error", errorMsg);
                    return metadata;
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
                messages.add(new UserMessage("Execute Competitive Pricing Intelligence task based on the provided context."));
            }

            Prompt prompt = new Prompt(messages);
            return streamingChatClient.stream(prompt)
                    .filter(response -> response.getResult() != null && response.getResult().getOutput() != null && response.getResult().getOutput().getContent() != null)
                    .map(response -> response.getResult().getOutput().getContent())
                    .onErrorResume(e -> Flux.just("Error streaming Competitive Pricing Intelligence: " + (e.getMessage() != null ? e.getMessage() : "Unknown error")));
        } catch (Exception e) {
            return Flux.just("Error streaming Competitive Pricing Intelligence: " + (e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }
}
