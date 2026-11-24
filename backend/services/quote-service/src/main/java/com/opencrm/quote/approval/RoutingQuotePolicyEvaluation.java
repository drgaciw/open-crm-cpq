package com.opencrm.quote.approval;

import com.opencrm.quote.client.ApprovalRoutingClient;
import com.opencrm.quote.client.QuotePolicyEvaluationRequest;
import com.opencrm.quote.client.QuotePolicyVerdictDto;
import com.opencrm.quote.quote.Quote;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seller path into EvaluateQuote. The quote BFF calls approval-routing; it does not write the verdict locally.
 */
@Component
public class RoutingQuotePolicyEvaluation implements QuotePolicyEvaluation {

    private final ApprovalRoutingClient approvalRoutingClient;
    private final QuoteVersionFactsFactory factsFactory;

    public RoutingQuotePolicyEvaluation(
            ApprovalRoutingClient approvalRoutingClient,
            QuoteVersionFactsFactory factsFactory) {
        this.approvalRoutingClient = approvalRoutingClient;
        this.factsFactory = factsFactory;
    }

    @Override
    public QuotePolicyVerdict evaluate(Quote quote) {
        QuotePolicyEvaluationRequest request = factsFactory.from(quote);
        QuotePolicyVerdictDto verdict = approvalRoutingClient.evaluateQuote(request);
        List<QuotePolicyVerdict.Breach> breaches = verdict.getBreaches() == null
                ? List.of()
                : verdict.getBreaches().stream()
                        .map(breach -> new QuotePolicyVerdict.Breach(
                                breach.getPolicyId(),
                                breach.getPolicyVersion(),
                                breach.getThresholdType(),
                                breach.getActual(),
                                breach.getLimit()))
                        .toList();
        return new QuotePolicyVerdict(true, verdict.getVerdict(), verdict.getQuoteVersion(), breaches);
    }
}
