package com.opencrm.approval.event.shadow;

import com.opencrm.common.event.dto.DiscountApprovalRequiredEvent;
import com.opencrm.jev.JevClient;
import com.opencrm.jev.JevProperties;
import com.opencrm.jev.JevResult;
import com.opencrm.jev.advisory.JevAdvisoryRunner;
import com.opencrm.jev.shadow.JevDecisionLogStore;
import com.opencrm.jev.shadow.JevDecisionRecord;
import com.opencrm.jev.state.JevState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Shadow-only comparison of the threshold rule with Jev (ADR-015). The rule stays authoritative: this
 * records what Jev would have said next to what the rule decided, and nothing reads the result back.
 *
 * <p>The Kafka handler that calls this rethrows every exception to force redelivery, so nothing here may
 * throw, and the evaluation runs after the handler's transaction commits, on another thread.
 */
@Component
@Slf4j
public class DiscountTierShadowService {

    static final String SUBJECT_TYPE = "approval_request";

    private final JevClient jevClient;
    private final JevAdvisoryRunner runner;
    private final JevDecisionLogStore decisionLog;
    private final boolean enabled;

    public DiscountTierShadowService(
            JevClient jevClient,
            JevAdvisoryRunner runner,
            JevDecisionLogStore decisionLog,
            @Value("${opencrm.jev.discount-tier-shadow.enabled:false}") boolean enabled) {
        this.jevClient = jevClient;
        this.runner = runner;
        this.decisionLog = decisionLog;
        this.enabled = enabled;
    }

    /**
     * @param approvalRequestId id of the request the rule just created
     * @param ruleTier the authoritative tier
     */
    public void recordAfterCommit(Long approvalRequestId, DiscountApprovalRequiredEvent event, String ruleTier) {
        try {
            if (!enabled || !jevClient.active(JevProperties.DISCOUNT_TIER_SHADOW)) {
                return;
            }
            runner.submitAfterCommit(JevProperties.DISCOUNT_TIER_SHADOW,
                    () -> evaluate(approvalRequestId, event, ruleTier));
        } catch (Throwable e) {
            log.warn("Discount tier shadow could not be scheduled: {}", e.getMessage());
        }
    }

    private void evaluate(Long approvalRequestId, DiscountApprovalRequiredEvent event, String ruleTier) {
        JevState state = DiscountTierFeatures.state(event);
        JevResult result = jevClient.decide(JevProperties.DISCOUNT_TIER_SHADOW, state,
                List.of(DiscountTierFeatures.QUESTION));
        decisionLog.save(JevDecisionRecord.of(JevProperties.DISCOUNT_TIER_SHADOW, SUBJECT_TYPE,
                String.valueOf(approvalRequestId), DiscountTierFeatures.QUESTION, state, result,
                ruleTier, JevDecisionRecord.AuthoritativeSource.RULE, true));
    }
}
