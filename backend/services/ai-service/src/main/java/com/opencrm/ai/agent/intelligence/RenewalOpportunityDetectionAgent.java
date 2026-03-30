package com.opencrm.ai.agent.intelligence;

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

@Service("cpq-agent-028")
public class RenewalOpportunityDetectionAgent implements CpqAgent {

    private final ChatClient chatClient;
    private final StreamingChatClient streamingChatClient;

    public RenewalOpportunityDetectionAgent(ChatClient chatClient, StreamingChatClient streamingChatClient) {
        this.chatClient = chatClient;
        this.streamingChatClient = streamingChatClient;
    }

    @Override
    public String getAgentId() {
        return "cpq-agent-028";
    }

    @Override
    public String getName() {
        return "Renewal Opportunity Detection Agent";
    }

    @Override
    public String getDescription() {
        return "Identifying upcoming renewals and assessing risk";
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
        } else {
            messages.add(new UserMessage("Analyze upcoming renewal risks and opportunities."));
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
        } else {
            messages.add(new UserMessage("Analyze upcoming renewal risks and opportunities."));
        }

        Prompt prompt = new Prompt(messages);
        return streamingChatClient.stream(prompt)
                .filter(response -> response.getResult() != null && response.getResult().getOutput() != null && response.getResult().getOutput().getContent() != null)
                .map(response -> response.getResult().getOutput().getContent());
    }
}
