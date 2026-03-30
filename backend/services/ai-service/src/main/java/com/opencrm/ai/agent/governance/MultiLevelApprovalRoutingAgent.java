package com.opencrm.ai.agent.governance;

import com.opencrm.ai.agent.AgentRequest;
import com.opencrm.ai.agent.AgentResponse;
import com.opencrm.ai.agent.CpqAgent;
import org.springframework.ai.chat.ChatClient;
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

@Service("cpq-agent-024")
public class MultiLevelApprovalRoutingAgent implements CpqAgent {

    private final ChatClient chatClient;

    public MultiLevelApprovalRoutingAgent(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public String getAgentId() {
        return "cpq-agent-024";
    }

    @Override
    public String getName() {
        return "Multi-Level Approval Routing Agent";
    }

    @Override
    public String getDescription() {
        return "Routing quotes for approval based on amount, discount, or margin";
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

        ChatResponse response = chatClient.call(new Prompt(messages));
        String outputContent = response.getResult().getOutput().getContent();

        return new AgentResponse() {
            @Override
            public boolean isSuccess() { return true; }
            @Override
            public String getOutput() { return outputContent; }
            @Override
            public Map<String, Object> getMetadata() {
                return Map.of(
                    "autonomy", "Automated"
                );
            }
        };
    }

    @Override
    public Flux<String> stream(AgentRequest request) {
        return Flux.just("Starting stream for Multi-Level Approval Routing Agent...", "Processing...", "Done");
    }
}
