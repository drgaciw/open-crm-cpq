package com.opencrm.crm.lead.scoring;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.crm.lead.Lead;
import com.opencrm.crm.lead.LeadRepository;
import com.opencrm.jev.JevClient;
import com.opencrm.jev.JevProperties;
import com.opencrm.jev.JevResult;
import com.opencrm.jev.advisory.JevAdvisoryRunner;
import com.opencrm.jev.answer.JevAnswer;
import com.opencrm.jev.shadow.JevDecisionLogStore;
import com.opencrm.jev.shadow.JevDecisionRecord;
import com.opencrm.jev.state.JevState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Advisory lead scoring (ADR-015). Fills {@code leads.score}, which nothing computed before, with Jev's
 * estimate of how likely the lead is to qualify. Nothing branches on the score.
 *
 * <p>Features are read on the caller's thread while the entity is managed; the evaluation itself runs
 * after the caller's transaction commits, on the advisory runner. Nothing here can fail the caller.
 */
@Component
@Slf4j
public class LeadScoreAdvisor {

    static final String SUBJECT_TYPE = "lead";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final JevClient jevClient;
    private final JevAdvisoryRunner runner;
    private final JevDecisionLogStore decisionLog;
    private final LeadRepository leadRepository;
    private final boolean enabled;
    private final Clock clock;

    @Autowired
    public LeadScoreAdvisor(JevClient jevClient, JevAdvisoryRunner runner, JevDecisionLogStore decisionLog,
                            LeadRepository leadRepository,
                            @Value("${opencrm.jev.lead-score.enabled:false}") boolean enabled) {
        this(jevClient, runner, decisionLog, leadRepository, enabled, Clock.systemUTC());
    }

    LeadScoreAdvisor(JevClient jevClient, JevAdvisoryRunner runner, JevDecisionLogStore decisionLog,
                     LeadRepository leadRepository, boolean enabled, Clock clock) {
        this.jevClient = jevClient;
        this.runner = runner;
        this.decisionLog = decisionLog;
        this.leadRepository = leadRepository;
        this.enabled = enabled;
        this.clock = clock;
    }

    /** Call after the lead is saved. Never throws. */
    public void requestScore(Lead lead) {
        try {
            if (!enabled || lead == null || lead.getId() == null
                    || !jevClient.active(JevProperties.LEAD_SCORE)) {
                return;
            }
            Long leadId = lead.getId();
            JevState state = LeadScoreFeatures.state(lead, clock.instant());
            runner.submitAfterCommit(JevProperties.LEAD_SCORE, () -> evaluate(leadId, state));
        } catch (Throwable e) {
            log.warn("Lead score evaluation could not be scheduled: {}", e.getMessage());
        }
    }

    private void evaluate(Long leadId, JevState state) {
        JevResult result = jevClient.decide(JevProperties.LEAD_SCORE, state, List.of(LeadScoreFeatures.QUESTION));
        decisionLog.save(JevDecisionRecord.of(JevProperties.LEAD_SCORE, SUBJECT_TYPE, String.valueOf(leadId),
                LeadScoreFeatures.QUESTION, state, result, null,
                JevDecisionRecord.AuthoritativeSource.NONE, true));
        if (!result.usable()) {
            return;
        }
        result.answer(LeadScoreFeatures.QUESTION_ID, JevAnswer.Score.class).ifPresent(answer ->
                leadRepository.applyAdvisoryScore(leadId, toScore(answer),
                        LeadScoreSuggestionDto.SOURCE_JEV,
                        BigDecimal.valueOf(answer.confidence()).setScale(4, RoundingMode.HALF_UP),
                        probabilitiesJson(answer), result.rubricVersion(), result.model(), clock.instant()));
    }

    /** Expected rubric level, scaled to the 0-100 range the {@code score} column has always implied. */
    static int toScore(JevAnswer.Score answer) {
        return (int) Math.round(answer.normalisedExpectation() * 100);
    }

    private static String probabilitiesJson(JevAnswer.Score answer) {
        Map<String, Double> byLevel = new LinkedHashMap<>();
        for (int level = 0; level < answer.probabilities().size(); level++) {
            byLevel.put(Integer.toString(level), answer.probabilities().get(level));
        }
        try {
            return JSON.writeValueAsString(byLevel);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
