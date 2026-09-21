package com.opencrm.quote.approval.suggestion;

import com.opencrm.jev.JevProperties;
import com.opencrm.jev.shadow.JevDecisionLogStore;
import com.opencrm.jev.shadow.JevDecisionRecord;
import com.opencrm.quote.approval.ApprovalTier;
import com.opencrm.quote.quote.QuoteRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Shadow bookkeeping (ADR-015): once a user has picked a tier, record it beside what Jev suggested so
 * agreement can be measured. Reads quotes, never writes them, and touches only {@code jev_decision_log}.
 */
@Component
@Slf4j
public class ApprovalTierShadowReconciler {

    private static final int BATCH = 100;
    private static final Set<String> TIERS =
            Arrays.stream(ApprovalTier.values()).map(Enum::name).collect(Collectors.toUnmodifiableSet());

    private final QuoteRepository quoteRepository;
    private final JevDecisionLogStore decisionLog;
    private final boolean enabled;

    public ApprovalTierShadowReconciler(
            QuoteRepository quoteRepository,
            JevDecisionLogStore decisionLog,
            @Value("${opencrm.jev.quote-tier-suggestion.enabled:false}") boolean enabled) {
        this.quoteRepository = quoteRepository;
        this.decisionLog = decisionLog;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${opencrm.jev.quote-tier-suggestion.reconcile-delay-ms:300000}",
            initialDelayString = "${opencrm.jev.quote-tier-suggestion.reconcile-delay-ms:300000}")
    public void reconcile() {
        if (!enabled) {
            return;
        }
        try {
            int recorded = 0;
            for (String subjectId : decisionLog.findSubjectsAwaitingOutcome(
                    JevProperties.QUOTE_TIER_SUGGESTION, ApprovalTierSuggestionService.SUBJECT_TYPE, BATCH)) {
                recorded += reconcile(subjectId);
            }
            if (recorded > 0) {
                log.info("Recorded the user's tier on {} Jev quote-tier decisions", recorded);
            }
        } catch (Throwable e) {
            log.warn("Quote tier shadow reconciliation failed: {}", e.getMessage());
        }
    }

    private int reconcile(String subjectId) {
        long quoteId;
        try {
            quoteId = Long.parseLong(subjectId);
        } catch (NumberFormatException e) {
            return 0;
        }
        // quote.approvalTier is also written from pricing data, which may use another vocabulary; only a
        // real ApprovalTier name is comparable with Jev's answer.
        return quoteRepository.findByIdAndDeletedFalse(quoteId)
                .map(quote -> quote.getApprovalTier())
                .filter(TIERS::contains)
                .map(tier -> decisionLog.recordOutcome(JevProperties.QUOTE_TIER_SUGGESTION,
                        ApprovalTierSuggestionService.SUBJECT_TYPE, subjectId, tier,
                        JevDecisionRecord.AuthoritativeSource.USER))
                .orElse(0);
    }
}
