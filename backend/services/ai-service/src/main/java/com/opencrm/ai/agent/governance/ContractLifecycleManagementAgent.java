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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service("cpq-agent-030")
public class ContractLifecycleManagementAgent implements CpqAgent {

    private final ChatClient chatClient;
    private final StreamingChatClient streamingChatClient;

    @Autowired
    public ContractLifecycleManagementAgent(ChatClient chatClient, StreamingChatClient streamingChatClient) {
        this.chatClient = chatClient;
        this.streamingChatClient = streamingChatClient;
    }

    @Override
    public String getAgentId() {
        return "cpq-agent-030";
    }

    @Override
    public String getName() {
        return "Contract Lifecycle Management Agent";
    }

    @Override
    public String getDescription() {
        return "Manage contract creation, redlining, and signatures";
    }

    @Override
    public AgentResponse invoke(AgentRequest request) {
        List<Message> springAiMessages = new ArrayList<>();

        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()) {
            springAiMessages.add(new SystemMessage(request.getSystemPrompt()));
        }

        if (request.getMessages() != null) {
            for (String msg : request.getMessages()) {
                springAiMessages.add(new UserMessage(msg));
            }
        }

        Prompt prompt = new Prompt(springAiMessages);
        ChatResponse chatResponse = chatClient.call(prompt);

        return new AgentResponse() {
            @Override
            public boolean isSuccess() {
                return true;
            }

            @Override
            public String getOutput() {
                return chatResponse.getResult().getOutput().getContent();
            }

            @Override
            public Map<String, Object> getMetadata() {
                return Map.of("autonomy", "Automated");
            }
        };
    }

    @Override
    public Flux<String> stream(AgentRequest request) {
        List<Message> springAiMessages = new ArrayList<>();

        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()) {
            springAiMessages.add(new SystemMessage(request.getSystemPrompt()));
        }

        if (request.getMessages() != null) {
            for (String msg : request.getMessages()) {
                springAiMessages.add(new UserMessage(msg));
            }
        }

        Prompt prompt = new Prompt(springAiMessages);
        return streamingChatClient.stream(prompt).map(response -> {
            if (response.getResult() != null && response.getResult().getOutput() != null && response.getResult().getOutput().getContent() != null) {
                return response.getResult().getOutput().getContent();
            }
            return "";
        }).filter(content -> !content.isEmpty());
    }
}
