package com.opencrm.ai.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.ai.audit.AgentAuditLogger;
import com.opencrm.ai.llm.LlmAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.UUID;

/**
 * Base class for all 37 CPQ AI agents.
 *
 * <p>Handles the internal {@link LlmAdapter} call pattern, context injection,
 * session ID generation, audit logging, and error recovery so subclasses only
 * need to declare:</p>
 * <ul>
 *   <li>{@link #getAgentId()} – e.g. {@code "cpq-agent-004"}</li>
 *   <li>{@link #getName()} – human-readable label</li>
 *   <li>{@link #getDescription()} – functional description</li>
 *   <li>{@link #autonomyTier()} – Advisory | Semi-autonomous | Automated</li>
 *   <li>{@link #agentSystemPrompt()} – agent-specific system instructions</li>
 * </ul>
 *
 * <p>All agents share the base CPQ system preamble defined in
 * {@link #CPQ_BASE_PREAMBLE}, which establishes guardrails, the human-in-the-loop
 * model, and output format conventions.</p>
 */
public abstract class AbstractCpqAgent implements CpqAgent {

    private static final Logger log = LoggerFactory.getLogger(AbstractCpqAgent.class);

    /**
     * Shared preamble injected into every agent's system prompt.
     * Establishes guardrails, human-in-the-loop model, and output format.
     */
    private static final String CPQ_BASE_PREAMBLE = """
            You are a specialized CPQ (Configure-Price-Quote) AI agent operating inside
            an enterprise CRM/CPQ platform. You MUST follow these guardrails at all times:

            GUARDRAILS:
            - Never autonomously modify prices, discounts, or quote state unless your
              autonomy tier is "Automated with constraints" AND the change is within policy.
            - Always surface your reasoning transparently so a human can review it.
            - If you are uncertain or the request is outside your scope, say so clearly.
            - Never fabricate pricing data, product availability, or customer information.
              Use only the data provided in the context or returned by tool calls.
            - All commercial-impacting suggestions must be marked REQUIRES_APPROVAL.

            OUTPUT FORMAT:
            Respond in JSON with this structure:
            {
              "summary": "One-sentence summary of the action/recommendation",
              "details": "Detailed explanation or reasoning",
              "suggestions": ["Actionable item 1", "..."],
              "requiresApproval": true/false,
              "confidence": "HIGH | MEDIUM | LOW",
              "flags": []
            }
            """;

    @Autowired
    private LlmAdapter llmAdapter;

    @Autowired
    private AgentAuditLogger auditLogger;

    @Autowired
    private ObjectMapper objectMapper;

    // -------------------------------------------------------------------------
    // Abstract contract — subclasses must implement
    // -------------------------------------------------------------------------

    /** Returns the autonomy tier: Advisory | Semi-autonomous | Automated */
    protected abstract String autonomyTier();

    /**
     * Agent-specific system instructions appended after {@link #CPQ_BASE_PREAMBLE}.
     * Describe the agent's specific role, the tools it may use, and any additional
     * constraints relevant to its function.
     */
    protected abstract String agentSystemPrompt();

    // -------------------------------------------------------------------------
    // CpqAgent implementation
    // -------------------------------------------------------------------------

    @Override
    public final AgentResponse invoke(AgentRequest request) {
        String sessionId = extractOrGenerate(request);
        auditLogger.logInvocation(getAgentId(), sessionId, request.getContext());

        try {
            String systemPrompt = buildSystemPrompt(request);
            String userMessage  = buildUserMessage(request);

            String output = llmAdapter.generate(systemPrompt, userMessage);

            auditLogger.logResponse(getAgentId(), sessionId, output);

            return DefaultAgentResponse.success(output, Map.of(
                    "agentId",   getAgentId(),
                    "sessionId", sessionId,
                    "autonomy",  autonomyTier()
            ));

        } catch (Exception ex) {
            log.error("Agent {} invocation failed: {}", getAgentId(), ex.getMessage(), ex);
            return DefaultAgentResponse.failure(
                    "Agent %s failed: %s".formatted(getAgentId(), ex.getMessage()));
        }
    }

    @Override
    public final Flux<String> stream(AgentRequest request) {
        String sessionId = extractOrGenerate(request);
        auditLogger.logInvocation(getAgentId(), sessionId, request.getContext());

        try {
            String systemPrompt = buildSystemPrompt(request);
            String userMessage  = buildUserMessage(request);

            return llmAdapter.stream(systemPrompt, userMessage)
                    .doOnError(ex -> log.error(
                            "Agent {} stream failed: {}", getAgentId(), ex.getMessage(), ex));

        } catch (Exception ex) {
            log.error("Agent {} stream setup failed: {}", getAgentId(), ex.getMessage(), ex);
            return Flux.just("ERROR: " + ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private String buildSystemPrompt(AgentRequest request) {
        // Allow caller to override the system prompt entirely; otherwise compose from base
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            return request.getSystemPrompt();
        }
        return CPQ_BASE_PREAMBLE + "\n\n" +
               "AGENT ROLE: " + getName() + " — " + getDescription() + "\n\n" +
               agentSystemPrompt();
    }

    private String buildUserMessage(AgentRequest request) {
        StringBuilder sb = new StringBuilder();

        // Last (or only) message is the user's current request
        if (!request.getMessages().isEmpty()) {
            sb.append(request.getMessages().get(request.getMessages().size() - 1));
        }

        // Append structured context as JSON so the LLM can reference it via tool calls
        if (!request.getContext().isEmpty()) {
            try {
                String contextJson = objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(request.getContext());
                sb.append("\n\n### CPQ Context\n```json\n").append(contextJson).append("\n```");
            } catch (JsonProcessingException ex) {
                log.warn("Could not serialize agent context to JSON", ex);
                sb.append("\n\nContext: ").append(request.getContext());
            }
        }

        return sb.toString();
    }

    private String extractOrGenerate(AgentRequest request) {
        Object sessionId = request.getContext().get("sessionId");
        return sessionId != null ? sessionId.toString() : UUID.randomUUID().toString();
    }
}
