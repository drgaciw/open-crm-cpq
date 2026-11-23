package com.opencrm.ai.experiment;

import com.fasterxml.jackson.databind.JsonNode;
import com.opencrm.ai.experiment.entity.RuleExperiment;
import com.opencrm.ai.experiment.entity.RuleExperimentVariant;
import com.opencrm.ai.experiment.repository.RuleExperimentRepository;
import com.opencrm.ai.experiment.repository.RuleExperimentVariantRepository;
import com.opencrm.ai.registry.CpqAgentToolClients;
import com.opencrm.ai.registry.CpqToolResponse;
import com.opencrm.security.CurrentActor;
import com.opencrm.security.SecurityUtils;
import com.opencrm.security.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Persisted rule A/B experiments (Price-Drools Wave 3 port without MCP / RL).
 *
 * <p>{@link #applyWinningVariant} creates a DRAFT rule only and requires DEAL_DESK or ADMIN.</p>
 */
@Service
public class RuleExperimentService {

    private static final Logger log = LoggerFactory.getLogger(RuleExperimentService.class);

    private final RuleExperimentRepository experimentRepository;
    private final RuleExperimentVariantRepository variantRepository;
    private final CpqAgentToolClients toolClients;
    private final ChallengerVariantGenerator challengerVariantGenerator;
    private final CurrentActor currentActor;

    public RuleExperimentService(
            RuleExperimentRepository experimentRepository,
            RuleExperimentVariantRepository variantRepository,
            CpqAgentToolClients toolClients,
            ChallengerVariantGenerator challengerVariantGenerator,
            CurrentActor currentActor) {
        this.experimentRepository = experimentRepository;
        this.variantRepository = variantRepository;
        this.toolClients = toolClients;
        this.challengerVariantGenerator = challengerVariantGenerator;
        this.currentActor = currentActor;
    }

    @Transactional
    public ExperimentResponse create(CreateExperimentRequest request) {
        CpqToolResponse existing = toolClients.getRule(request.ruleId());
        if (!existing.success() || existing.data() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    existing.message() == null ? "Rule not found: " + request.ruleId() : existing.message());
        }

        JsonNode rule = existing.data();
        String content = textOrEmpty(rule, "content");
        if (content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rule has no DRL content to experiment on");
        }

        RuleExperiment experiment = new RuleExperiment();
        experiment.setName(request.name());
        experiment.setDescription(request.description());
        experiment.setRuleId(request.ruleId());
        experiment.setRuleName(textOrEmpty(rule, "name"));
        if (rule.has("ruleSetId") && !rule.get("ruleSetId").isNull()) {
            experiment.setRuleSetId(rule.get("ruleSetId").asLong());
        }
        experiment.setStatus(RuleExperimentStatus.CREATED);
        experiment.setControlTrafficPct(
                request.controlTrafficPct() == null ? 0.20 : request.controlTrafficPct());
        experiment.setCreatedBy(currentActor.id());
        experiment = experimentRepository.save(experiment);

        List<RuleExperimentVariant> variants = new ArrayList<>();
        RuleExperimentVariant control = newVariant(
                experiment.getId(),
                "control",
                "Baseline / control variant",
                content,
                true);
        variants.add(control);

        if (request.variants() != null && !request.variants().isEmpty()) {
            for (CreateExperimentRequest.VariantSeed seed : request.variants()) {
                variants.add(newVariant(
                        experiment.getId(),
                        seed.name(),
                        seed.description(),
                        seed.ruleContent(),
                        false));
            }
        } else {
            int challengers = request.challengerCount() == null ? 2 : request.challengerCount();
            for (ChallengerVariantGenerator.GeneratedVariant generated
                    : challengerVariantGenerator.generate(content, challengers)) {
                variants.add(newVariant(
                        experiment.getId(),
                        generated.name(),
                        generated.description(),
                        generated.ruleContent(),
                        false));
            }
        }

        if (variants.size() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Experiment requires at least one challenger variant");
        }

        variantRepository.saveAll(variants);
        log.info("Created experiment {} for rule {} with {} variants",
                experiment.getId(), request.ruleId(), variants.size());
        return ExperimentResponse.from(experiment, variants);
    }

    @Transactional
    public ExperimentResponse start(UUID experimentId) {
        RuleExperiment experiment = requireExperiment(experimentId);
        if (experiment.getStatus() != RuleExperimentStatus.CREATED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Experiment must be CREATED to start; was " + experiment.getStatus());
        }
        experiment.setStatus(RuleExperimentStatus.RUNNING);
        experiment.setStartedAt(Instant.now());
        experimentRepository.save(experiment);
        return ExperimentResponse.from(experiment, variantsOf(experimentId));
    }

    @Transactional
    public ExperimentResponse complete(UUID experimentId, UUID winningVariantId) {
        RuleExperiment experiment = requireExperiment(experimentId);
        if (experiment.getStatus() != RuleExperimentStatus.RUNNING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Experiment must be RUNNING to complete; was " + experiment.getStatus());
        }

        List<RuleExperimentVariant> variants = variantsOf(experimentId);
        RuleExperimentVariant winner;
        if (winningVariantId != null) {
            winner = variants.stream()
                    .filter(v -> v.getId().equals(winningVariantId))
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Winning variant not found in experiment: " + winningVariantId));
        } else {
            winner = variants.stream()
                    .max(Comparator.comparingDouble(RuleExperimentVariant::getOverallScore)
                            .thenComparingDouble(RuleExperimentVariant::getRevenueImpact))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Experiment has no variants"));
        }

        experiment.setWinningVariantId(winner.getId());
        experiment.setStatus(RuleExperimentStatus.COMPLETED);
        experiment.setCompletedAt(Instant.now());
        experiment.setConclusion("Winning variant: " + winner.getVariantName()
                + " (score=" + winner.getOverallScore() + ")");
        experimentRepository.save(experiment);
        return ExperimentResponse.from(experiment, variants);
    }

    @Transactional
    public ExperimentResponse updateMetrics(
            UUID experimentId, UUID variantId, UpdateVariantMetricsRequest request) {
        RuleExperiment experiment = requireExperiment(experimentId);
        if (experiment.getStatus() != RuleExperimentStatus.RUNNING
                && experiment.getStatus() != RuleExperimentStatus.CREATED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Metrics can only be updated while CREATED or RUNNING");
        }

        RuleExperimentVariant variant = variantRepository.findByIdAndExperimentId(variantId, experimentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Variant not found: " + variantId));

        if (request.averageExecutionMs() != null) {
            variant.setAverageExecutionMs(request.averageExecutionMs());
        }
        if (request.sampleCount() != null) {
            variant.setSampleCount(request.sampleCount());
        }
        if (request.revenueImpact() != null) {
            variant.setRevenueImpact(request.revenueImpact());
        }
        if (request.conversionRateImpact() != null) {
            variant.setConversionRateImpact(request.conversionRateImpact());
        }
        if (request.averageDiscount() != null) {
            variant.setAverageDiscount(request.averageDiscount());
        }
        if (request.totalRevenue() != null) {
            variant.setTotalRevenue(request.totalRevenue());
        }
        if (request.customerSatisfaction() != null) {
            variant.setCustomerSatisfaction(request.customerSatisfaction());
        }
        if (request.overallScore() != null) {
            variant.setOverallScore(request.overallScore());
        } else {
            variant.setOverallScore(computeOverallScore(variant));
        }
        if (request.metricsJson() != null) {
            variant.setMetricsJson(request.metricsJson());
        }

        variantRepository.save(variant);
        return ExperimentResponse.from(experiment, variantsOf(experimentId));
    }

    /**
     * Persist winning variant as a DRAFT pricing rule. Never activates a rule set.
     * Requires ROLE_DEAL_DESK / DEAL_DESK or ADMIN.
     */
    @Transactional
    public ExperimentResponse applyWinningVariant(UUID experimentId, ApplyExperimentRequest request) {
        assertDealDeskOrAdmin();

        RuleExperiment experiment = requireExperiment(experimentId);
        if (experiment.getStatus() != RuleExperimentStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Experiment must be COMPLETED to apply; was " + experiment.getStatus());
        }
        if (experiment.getWinningVariantId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No winning variant set");
        }

        RuleExperimentVariant winner = variantRepository
                .findByIdAndExperimentId(experiment.getWinningVariantId(), experimentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Winning variant not found"));

        String actor = currentActor.id();
        if (winner.isControlVariant()) {
            experiment.setStatus(RuleExperimentStatus.APPLIED);
            experiment.setAppliedAt(Instant.now());
            experiment.setAppliedBy(actor);
            experiment.setConclusion((experiment.getConclusion() == null ? "" : experiment.getConclusion() + " ")
                    + "Control won — no draft created.");
            experimentRepository.save(experiment);
            return ExperimentResponse.from(experiment, variantsOf(experimentId));
        }

        if (experiment.getRuleSetId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Source rule has no ruleSetId; cannot create draft");
        }

        Map<String, Object> createBody = new HashMap<>();
        createBody.put("ruleSetId", experiment.getRuleSetId());
        createBody.put("name", (experiment.getRuleName() == null ? "Rule" : experiment.getRuleName())
                + " (experiment winner)");
        createBody.put("description",
                "Experiment " + experimentId + " winning variant draft — requires human activation");
        createBody.put("ruleType", "PRICING");
        createBody.put("priority", 500);
        createBody.put("content", winner.getRuleContent());
        createBody.put("explanationCode", "AI_EXPERIMENT_DRAFT");

        CpqToolResponse created = toolClients.createDraftRule(createBody);
        if (!created.success() || created.data() == null || !created.data().has("id")) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    created.message() == null ? "Failed to create draft rule" : created.message());
        }

        long draftId = created.data().get("id").asLong();
        CpqToolResponse validated = toolClients.validateRule(draftId);

        experiment.setAppliedDraftRuleId(draftId);
        experiment.setAppliedAt(Instant.now());
        experiment.setAppliedBy(actor);
        experiment.setStatus(RuleExperimentStatus.APPLIED);
        experiment.setConclusion((experiment.getConclusion() == null ? "" : experiment.getConclusion() + " ")
                + "Draft rule id=" + draftId
                + (validated.success() ? " validated" : " validation: " + validated.message()));
        experimentRepository.save(experiment);

        log.info("Applied experiment {} as draft rule {} by {}", experimentId, draftId, actor);
        return ExperimentResponse.from(experiment, variantsOf(experimentId));
    }

    @Transactional(readOnly = true)
    public ExperimentResponse get(UUID experimentId) {
        RuleExperiment experiment = requireExperiment(experimentId);
        return ExperimentResponse.from(experiment, variantsOf(experimentId));
    }

    @Transactional(readOnly = true)
    public List<ExperimentResponse> listAll() {
        return experimentRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(e -> ExperimentResponse.from(e, variantsOf(e.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ExperimentResponse> listForRule(long ruleId) {
        return experimentRepository.findByRuleIdOrderByCreatedAtDesc(ruleId).stream()
                .map(e -> ExperimentResponse.from(e, variantsOf(e.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ExperimentResponse.VariantResponse> listVariants(UUID experimentId) {
        requireExperiment(experimentId);
        return variantsOf(experimentId).stream()
                .map(ExperimentResponse.VariantResponse::from)
                .toList();
    }

    static void assertDealDeskOrAdmin() {
        Optional<UserContext> user = SecurityUtils.getCurrentUser();
        if (user.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Apply requires authenticated DEAL_DESK or ADMIN");
        }
        UserContext ctx = user.get();
        boolean allowed = ctx.isAdmin()
                || ctx.hasAnyRole("DEAL_DESK", "ROLE_DEAL_DESK", "DealDesk");
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Apply requires DEAL_DESK or ADMIN role");
        }
    }

    private RuleExperiment requireExperiment(UUID experimentId) {
        return experimentRepository.findById(experimentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Experiment not found: " + experimentId));
    }

    private List<RuleExperimentVariant> variantsOf(UUID experimentId) {
        return variantRepository.findByExperimentIdOrderByControlVariantDescCreatedAtAsc(experimentId);
    }

    private static RuleExperimentVariant newVariant(
            UUID experimentId,
            String name,
            String description,
            String content,
            boolean control) {
        RuleExperimentVariant variant = new RuleExperimentVariant();
        variant.setExperimentId(experimentId);
        variant.setVariantName(name);
        variant.setDescription(description);
        variant.setRuleContent(content);
        variant.setControlVariant(control);
        variant.setStatus("ACTIVE");
        return variant;
    }

    private static double computeOverallScore(RuleExperimentVariant variant) {
        return variant.getRevenueImpact() * 0.4
                + variant.getConversionRateImpact() * 0.3
                + variant.getCustomerSatisfaction() * 0.2
                - Math.min(variant.getAverageExecutionMs(), 1000) / 1000.0 * 0.1;
    }

    private static String textOrEmpty(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return "";
        }
        return node.get(field).asText("");
    }
}
