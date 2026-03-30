package com.opencrm.ai.agent.subscription;

import com.opencrm.ai.agent.AgentRequest;
import com.opencrm.ai.agent.AgentResponse;
import com.opencrm.ai.agent.CpqAgent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import java.util.Map;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import java.util.List;
import java.util.ArrayList;


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
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()) {
            messages.add(new SystemMessage(request.getSystemPrompt()));
        }
        if (request.getMessages() != null) {
            for (String msg : request.getMessages()) {
                messages.add(new UserMessage(msg));
            }
        }
        Prompt prompt = new Prompt(messages);
        ChatResponse chatResponse = chatClient.call(prompt);
        String output = "";
        if (chatResponse != null && chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null && chatResponse.getResult().getOutput().getContent() != null) {
            output = chatResponse.getResult().getOutput().getContent();
        }

        final String finalOutput = output;

        return new AgentResponse() {
            @Override
            public boolean isSuccess() { return true; }
            @Override
            public String getOutput() { return finalOutput; }
            @Override
            public Map<String, Object> getMetadata() { return Map.of("autonomy", "Automated"); }
        };
    }

    @Override
    public Flux<String> stream(AgentRequest request) {
        List<Message> messages = new ArrayList<>();
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()) {
            messages.add(new SystemMessage(request.getSystemPrompt()));
        }
        if (request.getMessages() != null) {
            for (String msg : request.getMessages()) {
                messages.add(new UserMessage(msg));
            }
        }
        Prompt prompt = new Prompt(messages);
        return streamingChatClient.stream(prompt).map(resp -> {
            if (resp.getResult() != null && resp.getResult().getOutput() != null && resp.getResult().getOutput().getContent() != null) {
                return resp.getResult().getOutput().getContent();
            }
            return "";
        }).filter(content -> !content.isEmpty());
    }
}
