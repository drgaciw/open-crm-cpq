package com.opencrm.ai.selection.execution;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/** Prevents direct Azure calls from bypassing opt-in policy execution. Never sends requests. */
final class UnavailableLegacyChatClient implements ChatClient, StreamingChatClient {
    static final String REASON = "LEGACY_AI_WORKFLOW_NOT_QUALIFIED";

    @Override public ChatResponse call(Prompt prompt) {
        throw new ExecutionTypes.Unavailable(REASON);
    }

    @Override public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.error(new ExecutionTypes.Unavailable(REASON));
    }
}
