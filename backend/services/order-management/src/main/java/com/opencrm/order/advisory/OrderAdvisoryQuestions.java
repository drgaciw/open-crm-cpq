package com.opencrm.order.advisory;

import com.opencrm.jev.question.JevQuestion;
import com.opencrm.jev.question.JevQuestions;
import java.util.LinkedHashMap;

/** Versioned investigation rubric; every answer is advice requiring operator review. */
public final class OrderAdvisoryQuestions {
    public static final String USE_CASE = "oms-exception-triage";
    public static final String QUESTION_ID = "investigation";
    public static final String RUBRIC = "oms-exception-triage-v1";

    private OrderAdvisoryQuestions() {}

    public enum Investigation {
        CHECK_SOURCE, CHECK_FULFILLMENT_EVIDENCE, CHECK_ACTIVATION_ACK, CHECK_FINANCE_ACK,
        CHECK_DELIVERY_STATUS, ESCALATE_INTEGRATION, INSUFFICIENT_INFORMATION
    }

    public static JevQuestion.Choice triage() {
        var criteria = new LinkedHashMap<String, String>();
        criteria.put(Investigation.CHECK_SOURCE.name(), "Inspect approved source revision and source constraints.");
        criteria.put(Investigation.CHECK_FULFILLMENT_EVIDENCE.name(), "Inspect authoritative fulfillment evidence and unresolved conflicts.");
        criteria.put(Investigation.CHECK_ACTIVATION_ACK.name(), "Inspect authoritative activation acknowledgment and prerequisite state.");
        criteria.put(Investigation.CHECK_FINANCE_ACK.name(), "Inspect authoritative finance acknowledgment and cumulative discrepancies.");
        criteria.put(Investigation.CHECK_DELIVERY_STATUS.name(), "Inspect referenced shipment and delivery evidence.");
        criteria.put(Investigation.ESCALATE_INTEGRATION.name(), "Suggest specialist investigation of unresolved external effects or exhausted delivery attempts.");
        criteria.put(Investigation.INSUFFICIENT_INFORMATION.name(), "Abstain when the available structured facts do not support a specific investigation.");
        return JevQuestions.choice(QUESTION_ID, "Suggest one investigation step using only the supplied structured facts. "
                + "UNKNOWN means missing knowledge, never false. Consider all known facts, not category alone. "
                + "Do not authorize retries, evidence acceptance, contradiction resolution, hold release, "
                + "fulfillment, finance completion or cancellation. Unknown external effects require authoritative review. "
                + "Advice cannot establish whether an external effect happened. Abstain when facts are insufficient.", criteria);
    }
}
