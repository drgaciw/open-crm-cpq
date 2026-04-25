package com.opencrm.ai.agent.subscription;

import com.opencrm.ai.agent.AgentRequest;
import com.opencrm.ai.agent.AgentResponse;
import com.opencrm.ai.agent.CpqAgent;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Service("cpq-agent-022")
public class PerpetualLicenseAgent implements CpqAgent {

    private final ChatClient chatClient;
    private final StreamingChatClient streamingChatClient;

    @Autowired
    public PerpetualLicenseAgent(ChatClient chatClient, StreamingChatClient streamingChatClient) {
        this.chatClient = chatClient;
        this.streamingChatClient = streamingChatClient;
    }

    @Override
    public String getAgentId() {
        return "cpq-agent-022";
    }

    @Override
    public String getName() {
        return "Perpetual License Agent";
    }

    @Override
    public String getDescription() {
        return "On-prem one-time license pricing and core pack rounding";
    }

    private Prompt buildPrompt(AgentRequest request) {
        List<Message> messages = new ArrayList<>();
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()) {
            messages.add(new SystemMessage(request.getSystemPrompt()));
        }

        if (request.getMessages() != null) {
            for (String msg : request.getMessages()) {
                messages.add(new UserMessage(msg));
            }
        }
        return new Prompt(messages);
    }

    @Override
    public AgentResponse invoke(AgentRequest request) {
        try {
            Prompt prompt = buildPrompt(request);
            ChatResponse response = chatClient.call(prompt);

            String outputContent = "";
            if (response != null && response.getResult() != null && response.getResult().getOutput() != null) {
                outputContent = response.getResult().getOutput().getContent();
            }

            final String finalOutput = outputContent;

            return new AgentResponse() {
                @Override
                public boolean isSuccess() { return true; }
                @Override
                public String getOutput() { return finalOutput; }
                @Override
                public Map<String, Object> getMetadata() {
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("autonomy", "Automated");
                    metadata.put("agentId", getAgentId());
                    metadata.put("agentName", getName());
                    return metadata;
                }
            };
        } catch (Exception e) {
            return new AgentResponse() {
                @Override
                public boolean isSuccess() { return false; }
                @Override
                public String getOutput() { return "Error executing LLM call: " + e.getMessage(); }
                @Override
                public Map<String, Object> getMetadata() {
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("autonomy", "Automated");
                    metadata.put("error", e.getMessage());
                    return metadata;
                }
            };
        }
    }

    @Override
    public Flux<String> stream(AgentRequest request) {
        try {
            Prompt prompt = buildPrompt(request);
            return streamingChatClient.stream(prompt)
                .map(response -> {
                    if (response != null && response.getResult() != null && response.getResult().getOutput() != null) {
                        return response.getResult().getOutput().getContent();
                    }
                    return "";
                })
                .filter(content -> !content.isEmpty());
        } catch (Exception e) {
            return Flux.error(e);
        }
    }
}
