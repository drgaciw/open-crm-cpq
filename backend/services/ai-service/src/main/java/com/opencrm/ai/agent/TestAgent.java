package com.opencrm.ai.agent;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import java.util.Map;

@Service("test-agent")
public class TestAgent implements CpqAgent {

    private final ChatClient chatClient;
    private final StreamingChatClient streamingChatClient;

    public TestAgent(ChatClient chatClient, StreamingChatClient streamingChatClient) {
        this.chatClient = chatClient;
        this.streamingChatClient = streamingChatClient;
    }

    @Override
    public String getAgentId() { return "test-agent"; }

    @Override
    public String getName() { return "Test Agent"; }

    @Override
    public String getDescription() { return "Test"; }

    @Override
    public AgentResponse invoke(AgentRequest request) {
        return null;
    }

    @Override
    public Flux<String> stream(AgentRequest request) {
        return null;
    }
}
