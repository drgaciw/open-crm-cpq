package com.opencrm.ai.agent.intelligence;

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
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service("cpq-agent-017")
public class RecommendationUpsellAgent implements CpqAgent {

    private final ChatClient chatClient;
    private final StreamingChatClient streamingChatClient;

    public RecommendationUpsellAgent(ChatClient chatClient, StreamingChatClient streamingChatClient) {
        this.chatClient = chatClient;
        this.streamingChatClient = streamingChatClient;
    }

    @Override
    public String getAgentId() {
        return "cpq-agent-017";
    }

    @Override
    public String getName() {
        return "Recommendation & Upsell Agent";
    }

    @Override
    public String getDescription() {
        return "Cross-sell and upsell suggestions based on customer and deal context";
    }

    private Prompt buildPrompt(AgentRequest request) {
        List<Message> messageList = new ArrayList<>();
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isEmpty()) {
            messageList.add(new SystemMessage(request.getSystemPrompt()));
        }

        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            for (String msg : request.getMessages()) {
                messageList.add(new UserMessage(msg));
            }
        }
        return new Prompt(messageList);
    }

    @Override
    public AgentResponse invoke(AgentRequest request) {
        Prompt prompt = buildPrompt(request);
        ChatResponse chatResponse = chatClient.call(prompt);
        String output = chatResponse.getResult().getOutput().getContent();

        return new AgentResponse() {
            @Override
            public boolean isSuccess() { return true; }
            @Override
            public String getOutput() { return output; }
            @Override
            public Map<String, Object> getMetadata() { return Map.of("autonomy", "Advisory"); }
        };
    }

    @Override
    public Flux<String> stream(AgentRequest request) {
        Prompt prompt = buildPrompt(request);
        return streamingChatClient.stream(prompt)
                .map(chatResponse -> {
                    if (chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null) {
                        return chatResponse.getResult().getOutput().getContent();
                    }
                    return "";
                })
                .filter(content -> !content.isEmpty());
    }
}
