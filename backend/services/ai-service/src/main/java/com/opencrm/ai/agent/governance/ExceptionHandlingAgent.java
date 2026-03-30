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
import java.util.List;
import java.util.Map;

@Service("cpq-agent-025")
public class ExceptionHandlingAgent implements CpqAgent {

    private final ChatClient chatClient;
    private final StreamingChatClient streamingChatClient;

    public ExceptionHandlingAgent(ChatClient chatClient, StreamingChatClient streamingChatClient) {
        this.chatClient = chatClient;
        this.streamingChatClient = streamingChatClient;
    }

    @Override
    public String getAgentId() {
        return "cpq-agent-025";
    }

    @Override
    public String getName() {
        return "Exception Handling Agent";
    }

    @Override
    public String getDescription() {
        return "Handle and route pricing policy exceptions";
    }

    private Prompt createPrompt(AgentRequest request) {
        List<Message> messages = new ArrayList<>();

        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()) {
            messages.add(new SystemMessage(request.getSystemPrompt()));
        } else {
            // Default system prompt if none provided
            messages.add(new SystemMessage("You are an AI agent responsible for handling and routing pricing policy exceptions."));
        }

        if (request.getMessages() != null) {
            for (String msg : request.getMessages()) {
                messages.add(new UserMessage(msg));
            }
        }

        if (request.getContext() != null && !request.getContext().isEmpty()) {
            messages.add(new SystemMessage("Context: " + request.getContext().toString()));
        }

        return new Prompt(messages);
    }

    @Override
    public AgentResponse invoke(AgentRequest request) {
        try {
            Prompt prompt = createPrompt(request);
            ChatResponse chatResponse = chatClient.call(prompt);
            String outputContent = chatResponse.getResult().getOutput().getContent();

            return new AgentResponse() {
                @Override
                public boolean isSuccess() { return true; }
                @Override
                public String getOutput() { return outputContent; }
                @Override
                public Map<String, Object> getMetadata() { return Map.of("autonomy", "Automated"); }
            };
        } catch (Exception e) {
            return new AgentResponse() {
                @Override
                public boolean isSuccess() { return false; }
                @Override
                public String getOutput() { return "Error executing LLM call: " + e.getMessage(); }
                @Override
                public Map<String, Object> getMetadata() { return Map.of("error", e.getMessage()); }
            };
        }
    }

    @Override
    public Flux<String> stream(AgentRequest request) {
        try {
            Prompt prompt = createPrompt(request);
            return streamingChatClient.stream(prompt)
                    .map(chatResponse -> {
                        if (chatResponse != null && chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null) {
                            String content = chatResponse.getResult().getOutput().getContent();
                            return content != null ? content : "";
                        }
                        return "";
                    })
                    .filter(content -> !content.isEmpty());
        } catch (Exception e) {
            return Flux.just("Error executing LLM stream: " + e.getMessage());
        }
    }
}
