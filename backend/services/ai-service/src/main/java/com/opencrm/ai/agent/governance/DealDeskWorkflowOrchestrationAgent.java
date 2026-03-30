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

@Service("cpq-agent-037")
public class DealDeskWorkflowOrchestrationAgent implements CpqAgent {

    private final ChatClient chatClient;
    private final StreamingChatClient streamingChatClient;

    public DealDeskWorkflowOrchestrationAgent(ChatClient chatClient, StreamingChatClient streamingChatClient) {
        this.chatClient = chatClient;
        this.streamingChatClient = streamingChatClient;
    }

    @Override
    public String getAgentId() {
        return "cpq-agent-037";
    }

    @Override
    public String getName() {
        return "Deal Desk Workflow Orchestration Agent";
    }

    @Override
    public String getDescription() {
        return "Automate Deal Desk tasks and triage requests";
    }

    private Prompt buildPrompt(AgentRequest request) {
        List<Message> messages = new ArrayList<>();

        if (request.getSystemPrompt() != null && !request.getSystemPrompt().trim().isEmpty()) {
            messages.add(new SystemMessage(request.getSystemPrompt()));
        }

        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            for (String msg : request.getMessages()) {
                messages.add(new UserMessage(msg));
            }
        } else {
            messages.add(new UserMessage("Process the request."));
        }

        return new Prompt(messages);
    }

    @Override
    public AgentResponse invoke(AgentRequest request) {
        Prompt prompt = buildPrompt(request);
        ChatResponse chatResponse = chatClient.call(prompt);
        String outputContent = chatResponse.getResult().getOutput().getContent();

        return new AgentResponse() {
            @Override
            public boolean isSuccess() {
                return true;
            }

            @Override
            public String getOutput() {
                return outputContent;
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
                .map(response -> {
                    if (response != null && response.getResult() != null && response.getResult().getOutput() != null && response.getResult().getOutput().getContent() != null) {
                        return response.getResult().getOutput().getContent();
                    }
                    return "";
                })
                .filter(content -> !content.isEmpty());
    }
}
