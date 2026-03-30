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

@Service("cpq-agent-031")
public class QuoteDocumentGenerationAgent implements CpqAgent {

    private final ChatClient chatClient;
    private final StreamingChatClient streamingChatClient;

    public QuoteDocumentGenerationAgent(ChatClient chatClient, StreamingChatClient streamingChatClient) {
        this.chatClient = chatClient;
        this.streamingChatClient = streamingChatClient;
    }

    @Override
    public String getAgentId() {
        return "cpq-agent-031";
    }

    @Override
    public String getName() {
        return "Quote Document Generation Agent";
    }

    @Override
    public String getDescription() {
        return "Generating and customizing quote proposal PDFs";
    }

    private Prompt buildPrompt(AgentRequest request) {
        List<Message> messages = new ArrayList<>();
        String systemPrompt = request.getSystemPrompt();
        if (systemPrompt == null || systemPrompt.trim().isEmpty()) {
            systemPrompt = "You are an AI assistant specialized in generating and customizing quote proposal PDFs. Ensure all generated quotes include proper legal language and terms.";
        }
        messages.add(new SystemMessage(systemPrompt));

        if (request.getMessages() != null) {
            for (String msg : request.getMessages()) {
                messages.add(new UserMessage(msg));
            }
        }

        return new Prompt(messages);
    }

    @Override
    public AgentResponse invoke(AgentRequest request) {
        Prompt prompt = buildPrompt(request);
        ChatResponse chatResponse = chatClient.call(prompt);
        String output = chatResponse.getResult().getOutput().getContent();

        return new AgentResponse() {
            @Override
            public boolean isSuccess() {
                return true;
            }

            @Override
            public String getOutput() {
                return output != null ? output : "";
            }

            @Override
            public Map<String, Object> getMetadata() {
                return Map.of("autonomy", "Automated");
            }
        };
    }

    @Override
    public Flux<String> stream(AgentRequest request) {
        Prompt prompt = buildPrompt(request);
        return streamingChatClient.stream(prompt)
                .map(chatResponse -> {
                    if (chatResponse.getResult() != null &&
                            chatResponse.getResult().getOutput() != null &&
                            chatResponse.getResult().getOutput().getContent() != null) {
                        return chatResponse.getResult().getOutput().getContent();
                    }
                    return "";
                })
                .filter(content -> !content.isEmpty());
    }
}
