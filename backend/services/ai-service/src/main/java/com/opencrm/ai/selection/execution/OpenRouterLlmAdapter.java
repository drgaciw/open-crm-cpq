package com.opencrm.ai.selection.execution;

import com.opencrm.ai.llm.LlmAdapter;
import com.opencrm.ai.selection.execution.ExecutionTypes.Request;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import java.util.List;

/** Existing string agents use a complete buffered result, with no partial output or token replay. */
public final class OpenRouterLlmAdapter implements LlmAdapter {
    private final PolicyExecutor executor;
    public OpenRouterLlmAdapter(PolicyExecutor executor) { this.executor = executor; }
    @Override public String generate(String systemPrompt, String userMessage) {
        return executor.execute(new Request(systemPrompt, userMessage,
                JsonNodeFactory.instance.objectNode().put("type", "json_object"), List.of(), false)).content();
    }
    /** Flux compatibility, deliberately not provider token streaming. */
    @Override public Flux<String> stream(String systemPrompt, String userMessage) {
        return Mono.fromCallable(() -> generate(systemPrompt, userMessage)).subscribeOn(Schedulers.boundedElastic()).flux();
    }
}
