package com.opencrm.ai.llm;

import reactor.core.publisher.Flux;

/**
 * Internal LLM seam for CPQ agents.
 *
 * <p>This keeps agents and routers insulated from Spring AI client API changes.</p>
 */
public interface LlmAdapter {

    String generate(String systemPrompt, String userMessage);

    Flux<String> stream(String systemPrompt, String userMessage);
}
