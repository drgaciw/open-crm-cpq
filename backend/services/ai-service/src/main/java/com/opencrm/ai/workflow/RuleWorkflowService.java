package com.opencrm.ai.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.ai.agent.AgentResponse;
import com.opencrm.ai.agent.DefaultAgentRequest;
import com.opencrm.ai.agent.rules.RuleCreateAgent;
import com.opencrm.ai.agent.rules.RuleModifyAgent;
import com.opencrm.ai.agent.rules.RuleOptimizeAgent;
import com.opencrm.ai.agent.rules.RulePerformanceAgent;
import com.opencrm.ai.agent.rules.RuleSetEvaluateAgent;
import com.opencrm.ai.registry.CpqAgentToolClients;
import com.opencrm.ai.registry.CpqToolResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Port of Price-Drools AI orchestration workflows into Open CRM/CPQ.
 *
 * <p>Proposals only: drafts may be persisted and validated, but rule-sets are never activated.</p>
 */
@Service
public class RuleWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(RuleWorkflowService.class);
    private static final Pattern DRL_FENCE = Pattern.compile("```(?:drl|java)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    private static final Pattern PROPOSED_DRL_JSON = Pattern.compile("\"proposedDrl\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    private final RuleCreateAgent ruleCreateAgent;
    private final RuleModifyAgent ruleModifyAgent;
    private final RuleSetEvaluateAgent ruleSetEvaluateAgent;
    private final RuleOptimizeAgent ruleOptimizeAgent;
    private final RulePerformanceAgent rulePerformanceAgent;
    private final CpqAgentToolClients toolClients;
    private final ObjectMapper objectMapper;

    public RuleWorkflowService(
            RuleCreateAgent ruleCreateAgent,
            RuleModifyAgent ruleModifyAgent,
            RuleSetEvaluateAgent ruleSetEvaluateAgent,
            RuleOptimizeAgent ruleOptimizeAgent,
            RulePerformanceAgent rulePerformanceAgent,
            CpqAgentToolClients toolClients,
            ObjectMapper objectMapper) {
        this.ruleCreateAgent = ruleCreateAgent;
        this.ruleModifyAgent = ruleModifyAgent;
        this.ruleSetEvaluateAgent = ruleSetEvaluateAgent;
        this.ruleOptimizeAgent = ruleOptimizeAgent;
        this.rulePerformanceAgent = rulePerformanceAgent;
        this.toolClients = toolClients;
        this.objectMapper = objectMapper;
    }

    public RuleWorkflowResult createRule(CreateRuleWorkflowRequest request) {
        try {
            AgentResponse response = ruleCreateAgent.invoke(
                    DefaultAgentRequest.of(request.businessRequirement(), Map.of(
                            "ruleType", request.ruleType(),
                            "businessDomain", request.businessDomain() == null ? "PRICING" : request.businessDomain(),
                            "ruleSetId", request.ruleSetId() == null ? "" : request.ruleSetId()
                    )));
            return finalizeProposal(
                    request.businessRequirement(),
                    response,
                    request.persistDraft(),
                    request.ruleSetId(),
                    request.ruleName() == null ? "AI Draft Rule" : request.ruleName(),
                    request.ruleType(),
                    request.priority());
        } catch (Exception ex) {
            log.error("create-rule workflow failed: {}", ex.getMessage(), ex);
            return RuleWorkflowResult.failure(request.businessRequirement(), ex.getMessage());
        }
    }

    public RuleWorkflowResult modifyRule(long ruleId, ModifyRuleWorkflowRequest request) {
        try {
            CpqToolResponse existing = toolClients.getRule(ruleId);
            if (!existing.success()) {
                return RuleWorkflowResult.failure(request.modificationRequirement(), existing.message());
            }

            Map<String, Object> context = new HashMap<>();
            context.put("ruleId", ruleId);
            context.put("existingRule", existing.data());
            AgentResponse response = ruleModifyAgent.invoke(
                    DefaultAgentRequest.of(request.modificationRequirement(), context));

            String existingContent = textOrEmpty(existing.data(), "content");
            Long ruleSetId = existing.data() != null && existing.data().has("ruleSetId")
                    ? existing.data().get("ruleSetId").asLong()
                    : null;
            String ruleName = textOrEmpty(existing.data(), "name");
            String ruleType = textOrEmpty(existing.data(), "ruleType");
            if (ruleType.isBlank()) {
                ruleType = "PRICING";
            }
            int priority = existing.data() != null && existing.data().has("priority")
                    ? existing.data().get("priority").asInt(500)
                    : 500;

            RuleWorkflowResult proposal = finalizeProposal(
                    request.modificationRequirement(),
                    response,
                    request.persistDraft(),
                    ruleSetId,
                    ruleName.isBlank() ? "AI Modified Rule" : ruleName + " (proposed)",
                    ruleType,
                    priority);

            if (proposal.proposedDrl() == null && !existingContent.isBlank()) {
                return new RuleWorkflowResult(
                        proposal.success(),
                        true,
                        proposal.businessRequirement(),
                        proposal.agentOutput(),
                        existingContent,
                        proposal.draftRuleId(),
                        proposal.draftPersisted(),
                        proposal.draftValidated(),
                        proposal.validationMessage(),
                        proposal.errorMessage(),
                        proposal.suggestions(),
                        proposal.metadata());
            }
            return proposal;
        } catch (Exception ex) {
            log.error("modify-rule workflow failed for {}: {}", ruleId, ex.getMessage(), ex);
            return RuleWorkflowResult.failure(request.modificationRequirement(), ex.getMessage());
        }
    }

    public RuleEvaluationWorkflowResult evaluateRuleSet(long ruleSetId) {
        try {
            CpqToolResponse rules = toolClients.listRulesByRuleSet(ruleSetId);
            Map<String, Object> context = new HashMap<>();
            context.put("ruleSetId", ruleSetId);
            if (rules.success()) {
                context.put("rules", rules.data());
            } else {
                context.put("rulesError", rules.message());
            }

            AgentResponse response = ruleSetEvaluateAgent.invoke(
                    DefaultAgentRequest.of("Evaluate rule set " + ruleSetId, context));
            if (!response.isSuccess()) {
                return RuleEvaluationWorkflowResult.failure(ruleSetId, response.getOutput());
            }

            List<String> findings = extractStringArray(response.getOutput(), "findings");
            Map<String, Object> impactSummary = new HashMap<>();
            impactSummary.put("rulesLoaded", rules.success());
            impactSummary.put("rulesMessage", rules.message());

            return new RuleEvaluationWorkflowResult(
                    true, ruleSetId, response.getOutput(), findings, null, impactSummary);
        } catch (Exception ex) {
            log.error("evaluate-ruleset workflow failed for {}: {}", ruleSetId, ex.getMessage(), ex);
            return RuleEvaluationWorkflowResult.failure(ruleSetId, ex.getMessage());
        }
    }

    public RuleWorkflowResult optimize(OptimizeRuleWorkflowRequest request) {
        try {
            Map<String, Object> context = new HashMap<>();
            if (request.ruleId() != null) {
                CpqToolResponse existing = toolClients.getRule(request.ruleId());
                context.put("ruleId", request.ruleId());
                if (existing.success()) {
                    context.put("existingRule", existing.data());
                }
                CpqToolResponse impact = toolClients.getRuleImpact(request.ruleId());
                if (impact.success()) {
                    context.put("impact", impact.data());
                }
            }
            if (request.ruleContent() != null) {
                context.put("ruleContent", request.ruleContent());
            }
            if (request.pricingContext() != null && !request.pricingContext().isEmpty()) {
                CpqToolResponse simulated = toolClients.simulatePricing(request.pricingContext());
                context.put("simulation", Map.of(
                        "success", simulated.success(),
                        "message", simulated.message(),
                        "data", simulated.data() == null ? Map.of() : simulated.data()
                ));
            }

            AgentResponse response = ruleOptimizeAgent.invoke(
                    DefaultAgentRequest.of("Optimize the pricing rule using the provided context", context));

            Long ruleSetId = null;
            String ruleName = "AI Optimized Rule";
            String ruleType = "PRICING";
            int priority = 500;
            if (context.get("existingRule") instanceof JsonNode existing && existing.has("ruleSetId")) {
                ruleSetId = existing.get("ruleSetId").asLong();
                ruleName = textOrEmpty(existing, "name");
                if (ruleName.isBlank()) {
                    ruleName = "AI Optimized Rule";
                } else {
                    ruleName = ruleName + " (optimized)";
                }
                ruleType = textOrEmpty(existing, "ruleType");
                if (ruleType.isBlank()) {
                    ruleType = "PRICING";
                }
                priority = existing.has("priority") ? existing.get("priority").asInt(500) : 500;
            }

            return finalizeProposal(
                    "optimize",
                    response,
                    request.persistDraft(),
                    ruleSetId,
                    ruleName,
                    ruleType,
                    priority);
        } catch (Exception ex) {
            log.error("optimize workflow failed: {}", ex.getMessage(), ex);
            return RuleWorkflowResult.failure("optimize", ex.getMessage());
        }
    }

    public PerformanceAnalysisResult analyzePerformance(long ruleId) {
        try {
            CpqToolResponse impact = toolClients.getRuleImpact(ruleId);
            Map<String, Object> context = new HashMap<>();
            context.put("ruleId", ruleId);
            if (impact.success()) {
                context.put("impact", impact.data());
            } else {
                context.put("impactError", impact.message());
            }

            AgentResponse response = rulePerformanceAgent.invoke(
                    DefaultAgentRequest.of("Analyze performance for rule " + ruleId, context));
            if (!response.isSuccess()) {
                return PerformanceAnalysisResult.failure(ruleId, response.getOutput());
            }

            Map<String, Object> impactMap = new HashMap<>();
            if (impact.success() && impact.data() != null) {
                impactMap.put("raw", impact.data());
            }

            return new PerformanceAnalysisResult(
                    true,
                    ruleId,
                    response.getOutput(),
                    extractStringArray(response.getOutput(), "issues"),
                    extractStringArray(response.getOutput(), "recommendations"),
                    impactMap,
                    null);
        } catch (Exception ex) {
            log.error("performance analysis failed for {}: {}", ruleId, ex.getMessage(), ex);
            return PerformanceAnalysisResult.failure(ruleId, ex.getMessage());
        }
    }

    private RuleWorkflowResult finalizeProposal(
            String requirement,
            AgentResponse response,
            boolean persistDraft,
            Long ruleSetId,
            String ruleName,
            String ruleType,
            int priority) {
        if (!response.isSuccess()) {
            return RuleWorkflowResult.failure(requirement, response.getOutput());
        }

        String output = response.getOutput();
        String proposedDrl = extractProposedDrl(output);
        List<String> suggestions = extractStringArray(output, "suggestions");
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("agentMetadata", response.getMetadata());

        Long draftRuleId = null;
        boolean draftPersisted = false;
        boolean draftValidated = false;
        String validationMessage = null;

        if (persistDraft && ruleSetId != null && proposedDrl != null && !proposedDrl.isBlank()) {
            Map<String, Object> createBody = new HashMap<>();
            createBody.put("ruleSetId", ruleSetId);
            createBody.put("name", ruleName);
            createBody.put("description", "AI advisory draft — requires human approval before activation");
            createBody.put("ruleType", ruleType);
            createBody.put("priority", Math.min(1000, Math.max(100, priority)));
            createBody.put("content", proposedDrl);
            createBody.put("explanationCode", "AI_DRAFT");

            CpqToolResponse created = toolClients.createDraftRule(createBody);
            metadata.put("createDraft", created.message());
            if (created.success() && created.data() != null && created.data().has("id")) {
                draftPersisted = true;
                draftRuleId = created.data().get("id").asLong();
                CpqToolResponse validated = toolClients.validateRule(draftRuleId);
                draftValidated = validated.success();
                validationMessage = validated.message();
            } else if (!created.success()) {
                validationMessage = created.message();
            }
        }

        return new RuleWorkflowResult(
                true,
                true,
                requirement,
                output,
                proposedDrl,
                draftRuleId,
                draftPersisted,
                draftValidated,
                validationMessage,
                null,
                suggestions,
                metadata);
    }

    static String extractProposedDrl(String agentOutput) {
        if (agentOutput == null || agentOutput.isBlank()) {
            return null;
        }
        Matcher fence = DRL_FENCE.matcher(agentOutput);
        if (fence.find()) {
            return fence.group(1).trim();
        }
        Matcher json = PROPOSED_DRL_JSON.matcher(agentOutput);
        if (json.find()) {
            return json.group(1)
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .trim();
        }
        if (agentOutput.contains("rule \"") && agentOutput.contains("when")) {
            return agentOutput.trim();
        }
        return null;
    }

    private List<String> extractStringArray(String agentOutput, String field) {
        List<String> values = new ArrayList<>();
        if (agentOutput == null || agentOutput.isBlank()) {
            return values;
        }
        try {
            JsonNode root = objectMapper.readTree(stripJsonFence(agentOutput));
            JsonNode arr = root.path(field);
            if (arr.isArray()) {
                arr.forEach(node -> values.add(node.asText()));
            }
        } catch (Exception ignored) {
            // Agent output may be prose; leave suggestions empty.
        }
        return values;
    }

    private static String stripJsonFence(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstNl = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNl > 0 && lastFence > firstNl) {
                return trimmed.substring(firstNl + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    private static String textOrEmpty(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return "";
        }
        return node.get(field).asText("");
    }
}
