package com.opencrm.quote.approval.suggestion;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.jev.JevClient;
import com.opencrm.jev.JevProperties;
import com.opencrm.jev.JevResult;
import com.opencrm.jev.advisory.JevAdvisoryRunner;
import com.opencrm.jev.shadow.JevDecisionLogStore;
import com.opencrm.jev.shadow.JevDecisionLogStore.StoredDecision;
import com.opencrm.jev.shadow.JevDecisionRecord;
import com.opencrm.jev.state.JevState;
import com.opencrm.quote.quote.Quote;
import com.opencrm.quote.quote.QuoteRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Advisory approval-tier suggestion (ADR-015). Deliberately separate from {@code ApprovalService}: it
 * reads a quote, never writes one, and has no part in the approvals single-writer / dual-read path.
 *
 * <p>Answers are reused per feature state. The first request for a given state starts an evaluation and
 * returns PENDING; later requests read the stored decision. A state that was already evaluated without a
 * usable answer is not retried, so polling cannot spend the call budget.
 */
@Service
@Slf4j
public class ApprovalTierSuggestionService {

    static final String SUBJECT_TYPE = "quote";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Double>> PROBABILITIES = new TypeReference<>() { };

    private final QuoteRepository quoteRepository;
    private final JevClient jevClient;
    private final JevAdvisoryRunner runner;
    private final JevDecisionLogStore decisionLog;
    private final boolean enabled;

    /** How long a started evaluation blocks another for the same state, in case the runner dropped it. */
    static final long IN_FLIGHT_TTL_MS = 120_000;

    /** Feature state to the time its evaluation started, so concurrent polls start only one. */
    private final Map<String, Long> inFlight = new ConcurrentHashMap<>();

    public ApprovalTierSuggestionService(
            QuoteRepository quoteRepository,
            JevClient jevClient,
            JevAdvisoryRunner runner,
            JevDecisionLogStore decisionLog,
            @Value("${opencrm.jev.quote-tier-suggestion.enabled:false}") boolean enabled) {
        this.quoteRepository = quoteRepository;
        this.jevClient = jevClient;
        this.runner = runner;
        this.decisionLog = decisionLog;
        this.enabled = enabled;
    }

    /** Never throws: any problem is reported as UNAVAILABLE. */
    @Transactional(readOnly = true)
    public ApprovalTierSuggestionDto suggest(Long quoteId) {
        try {
            if (!enabled || !jevClient.active(JevProperties.QUOTE_TIER_SUGGESTION)) {
                return ApprovalTierSuggestionDto.unavailable();
            }
            Optional<Quote> quote = quoteRepository.findByIdAndDeletedFalse(quoteId);
            if (quote.isEmpty()) {
                return ApprovalTierSuggestionDto.unavailable();
            }
            JevState state = QuoteTierFeatures.state(quote.get());
            String hash = state.sha256();

            Optional<StoredDecision> stored = decisionLog.findLatest(JevProperties.QUOTE_TIER_SUGGESTION, hash);
            if (stored.isPresent()) {
                return toDto(stored.get());
            }
            if (claim(hash, System.currentTimeMillis())) {
                runner.submitAfterCommit(JevProperties.QUOTE_TIER_SUGGESTION, () -> evaluate(quoteId, state, hash));
            }
            return ApprovalTierSuggestionDto.pending();
        } catch (Throwable e) {
            log.warn("Approval tier suggestion unavailable for quote {}: {}", quoteId, e.getMessage());
            return ApprovalTierSuggestionDto.unavailable();
        }
    }

    /** @return true when the caller should start the evaluation for this state */
    boolean claim(String hash, long now) {
        boolean[] claimed = {false};
        inFlight.compute(hash, (key, startedAt) -> {
            if (startedAt == null || now - startedAt > IN_FLIGHT_TTL_MS) {
                claimed[0] = true;
                return now;
            }
            return startedAt;
        });
        return claimed[0];
    }

    private void evaluate(Long quoteId, JevState state, String hash) {
        try {
            JevResult result = jevClient.decide(JevProperties.QUOTE_TIER_SUGGESTION, state,
                    List.of(QuoteTierFeatures.QUESTION));
            // The user's pick is not known yet; ApprovalTierShadowReconciler fills it in later.
            decisionLog.save(JevDecisionRecord.of(JevProperties.QUOTE_TIER_SUGGESTION, SUBJECT_TYPE,
                    String.valueOf(quoteId), QuoteTierFeatures.QUESTION, state, result, null,
                    JevDecisionRecord.AuthoritativeSource.NONE, true));
        } finally {
            inFlight.remove(hash);
        }
    }

    private static ApprovalTierSuggestionDto toDto(StoredDecision stored) {
        if (!JevResult.Status.OK.name().equals(stored.status()) || stored.answerValue() == null) {
            return ApprovalTierSuggestionDto.unavailable();
        }
        return new ApprovalTierSuggestionDto(ApprovalTierSuggestionDto.Status.READY,
                new ApprovalTierSuggestionDto.Suggestion(stored.answerValue(),
                        stored.confidence() == null ? 0 : stored.confidence(),
                        probabilities(stored.probabilitiesJson()), stored.rubricVersion(),
                        stored.evaluatorModel(), true, stored.createdAt()));
    }

    private static Map<String, Double> probabilities(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return JSON.readValue(json, PROBABILITIES);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
