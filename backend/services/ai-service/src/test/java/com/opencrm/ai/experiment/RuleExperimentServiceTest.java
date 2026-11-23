package com.opencrm.ai.experiment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opencrm.ai.experiment.entity.RuleExperiment;
import com.opencrm.ai.experiment.entity.RuleExperimentVariant;
import com.opencrm.ai.experiment.repository.RuleExperimentRepository;
import com.opencrm.ai.experiment.repository.RuleExperimentVariantRepository;
import com.opencrm.ai.registry.CpqAgentToolClients;
import com.opencrm.ai.registry.CpqToolResponse;
import com.opencrm.security.CurrentActor;
import com.opencrm.security.test.TestCallers;
import com.opencrm.security.test.TestTokens;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuleExperimentServiceTest {

    @Mock RuleExperimentRepository experimentRepository;
    @Mock RuleExperimentVariantRepository variantRepository;
    @Mock CpqAgentToolClients toolClients;

    private RuleExperimentService service;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<RuleExperimentVariant> savedVariants = new ArrayList<>();

    @BeforeEach
    void setUp() {
        savedVariants.clear();
        service = new RuleExperimentService(
                experimentRepository,
                variantRepository,
                toolClients,
                new ChallengerVariantGenerator(),
                new CurrentActor("service:ai-service", false));

        lenient().when(experimentRepository.save(any(RuleExperiment.class))).thenAnswer(inv -> {
            RuleExperiment exp = inv.getArgument(0);
            if (exp.getId() == null) {
                exp.setId(UUID.randomUUID());
            }
            return exp;
        });
        lenient().when(variantRepository.saveAll(anyList())).thenAnswer(inv -> {
            List<RuleExperimentVariant> list = inv.getArgument(0);
            for (RuleExperimentVariant v : list) {
                if (v.getId() == null) {
                    v.setId(UUID.randomUUID());
                }
                savedVariants.add(v);
            }
            return list;
        });
        lenient().when(variantRepository.save(any(RuleExperimentVariant.class))).thenAnswer(inv -> {
            RuleExperimentVariant v = inv.getArgument(0);
            if (v.getId() == null) {
                v.setId(UUID.randomUUID());
            }
            return v;
        });
    }

    @AfterEach
    void tearDown() {
        TestCallers.clear();
    }

    @Test
    void createBuildsControlAndGeneratedChallengers() {
        ObjectNode rule = objectMapper.createObjectNode();
        rule.put("id", 42);
        rule.put("name", "Volume Discount");
        rule.put("ruleSetId", 9);
        rule.put("content", "rule \"V\" when then $q.setDiscount(0.10); end");
        when(toolClients.getRule(42L)).thenReturn(CpqToolResponse.ok(rule, "ok"));

        ExperimentResponse response = service.create(new CreateExperimentRequest(
                42L, "Vol A/B", "desc", 0.25, 2, null));

        assertTrue(response.success());
        assertEquals(RuleExperimentStatus.CREATED, response.status());
        assertEquals(3, response.variants().size());
        assertTrue(response.variants().stream().anyMatch(ExperimentResponse.VariantResponse::controlVariant));
        assertEquals(0.25, response.controlTrafficPct());
    }

    @Test
    void startAndCompleteSelectHighestScoreWinner() {
        UUID experimentId = UUID.randomUUID();
        RuleExperiment experiment = baseExperiment(experimentId, RuleExperimentStatus.CREATED);

        UUID controlId = UUID.randomUUID();
        UUID challengerId = UUID.randomUUID();
        RuleExperimentVariant control = variant(experimentId, controlId, "control", true, 1.0);
        RuleExperimentVariant challenger = variant(experimentId, challengerId, "challenger-1", false, 5.0);

        when(experimentRepository.findById(experimentId)).thenReturn(Optional.of(experiment));
        when(variantRepository.findByExperimentIdOrderByControlVariantDescCreatedAtAsc(experimentId))
                .thenReturn(List.of(control, challenger));

        ExperimentResponse started = service.start(experimentId);
        assertEquals(RuleExperimentStatus.RUNNING, started.status());

        experiment.setStatus(RuleExperimentStatus.RUNNING);
        ExperimentResponse completed = service.complete(experimentId, null);
        assertEquals(RuleExperimentStatus.COMPLETED, completed.status());
        assertEquals(challengerId, completed.winningVariantId());
    }

    @Test
    void applyRequiresDealDeskOrAdmin() {
        UUID experimentId = UUID.randomUUID();
        RuleExperiment experiment = baseExperiment(experimentId, RuleExperimentStatus.COMPLETED);
        experiment.setWinningVariantId(UUID.randomUUID());

        TestCallers.clear();
        ResponseStatusException forbidden = assertThrows(ResponseStatusException.class,
                () -> service.applyWinningVariant(experimentId, new ApplyExperimentRequest()));
        assertEquals(403, forbidden.getStatusCode().value());
    }

    @Test
    void applyCreatesDraftForWinningChallenger() {
        UUID experimentId = UUID.randomUUID();
        UUID winnerId = UUID.randomUUID();
        RuleExperiment experiment = baseExperiment(experimentId, RuleExperimentStatus.COMPLETED);
        experiment.setWinningVariantId(winnerId);
        experiment.setRuleSetId(9L);
        experiment.setRuleName("Volume Discount");

        RuleExperimentVariant winner = variant(experimentId, winnerId, "challenger-1", false, 5.0);
        winner.setRuleContent("rule \"Winner\" when then $q.setDiscount(0.085); end");

        when(experimentRepository.findById(experimentId)).thenReturn(Optional.of(experiment));
        when(variantRepository.findByIdAndExperimentId(winnerId, experimentId))
                .thenReturn(Optional.of(winner));
        when(variantRepository.findByExperimentIdOrderByControlVariantDescCreatedAtAsc(experimentId))
                .thenReturn(List.of(winner));

        ObjectNode created = objectMapper.createObjectNode();
        created.put("id", 777);
        when(toolClients.createDraftRule(anyMap())).thenReturn(CpqToolResponse.ok(created, "created"));
        when(toolClients.validateRule(777L)).thenReturn(CpqToolResponse.ok(created, "valid"));

        TestCallers.signInUser("desk", "DealDesk");
        ExperimentResponse applied = service.applyWinningVariant(
                experimentId, new ApplyExperimentRequest());

        assertEquals(RuleExperimentStatus.APPLIED, applied.status());
        assertEquals(777L, applied.appliedDraftRuleId());
        assertEquals(TestTokens.oidOf("desk"), applied.appliedBy());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
        verify(toolClients).createDraftRule(body.capture());
        assertEquals(9L, body.getValue().get("ruleSetId"));
        assertEquals("AI_EXPERIMENT_DRAFT", body.getValue().get("explanationCode"));
        assertTrue(body.getValue().get("content").toString().contains("Winner"));
    }

    @Test
    void applyControlWinnerSkipsDraft() {
        UUID experimentId = UUID.randomUUID();
        UUID winnerId = UUID.randomUUID();
        RuleExperiment experiment = baseExperiment(experimentId, RuleExperimentStatus.COMPLETED);
        experiment.setWinningVariantId(winnerId);
        experiment.setRuleSetId(9L);

        RuleExperimentVariant winner = variant(experimentId, winnerId, "control", true, 9.0);
        when(experimentRepository.findById(experimentId)).thenReturn(Optional.of(experiment));
        when(variantRepository.findByIdAndExperimentId(winnerId, experimentId))
                .thenReturn(Optional.of(winner));
        when(variantRepository.findByExperimentIdOrderByControlVariantDescCreatedAtAsc(experimentId))
                .thenReturn(List.of(winner));

        TestCallers.signInUser("admin", "Admin");
        ExperimentResponse applied = service.applyWinningVariant(
                experimentId, new ApplyExperimentRequest());

        assertEquals(RuleExperimentStatus.APPLIED, applied.status());
        assertEquals(null, applied.appliedDraftRuleId());
        assertEquals(TestTokens.oidOf("admin"), applied.appliedBy());
        verify(toolClients, never()).createDraftRule(anyMap());
    }

    @Test
    void updateMetricsRecalculatesScoreWhenNotProvided() {
        UUID experimentId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        RuleExperiment experiment = baseExperiment(experimentId, RuleExperimentStatus.RUNNING);
        RuleExperimentVariant variant = variant(experimentId, variantId, "challenger-1", false, 0);

        when(experimentRepository.findById(experimentId)).thenReturn(Optional.of(experiment));
        when(variantRepository.findByIdAndExperimentId(variantId, experimentId))
                .thenReturn(Optional.of(variant));
        when(variantRepository.findByExperimentIdOrderByControlVariantDescCreatedAtAsc(experimentId))
                .thenReturn(List.of(variant));

        ExperimentResponse response = service.updateMetrics(experimentId, variantId,
                new UpdateVariantMetricsRequest(12.0, 100, 10.0, 5.0, 0.1, 1000.0, 8.0, null, null));

        assertNotNull(response);
        assertTrue(variant.getOverallScore() > 0);
        assertEquals(100, variant.getSampleCount());
    }

    private static RuleExperiment baseExperiment(UUID id, RuleExperimentStatus status) {
        RuleExperiment experiment = new RuleExperiment();
        experiment.setId(id);
        experiment.setName("test");
        experiment.setRuleId(42L);
        experiment.setRuleName("Volume Discount");
        experiment.setRuleSetId(9L);
        experiment.setStatus(status);
        experiment.setControlTrafficPct(0.2);
        return experiment;
    }

    private static RuleExperimentVariant variant(
            UUID experimentId, UUID id, String name, boolean control, double score) {
        RuleExperimentVariant variant = new RuleExperimentVariant();
        variant.setId(id);
        variant.setExperimentId(experimentId);
        variant.setVariantName(name);
        variant.setControlVariant(control);
        variant.setRuleContent("rule \"" + name + "\" when then end");
        variant.setOverallScore(score);
        variant.setStatus("ACTIVE");
        return variant;
    }
}
