package com.opencrm.order.advisory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencrm.jev.JevClient;
import com.opencrm.jev.JevProperties;
import com.opencrm.jev.JevResult;
import com.opencrm.jev.answer.JevAnswer;
import com.opencrm.jev.testing.FakeJevTransport;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class OrderAdvisoryQuestionsTest {
    /** Scripted transport validation only: these answers are not predictions or expert labels. */
    @Test
    void offlineScriptedTransportAcceptsEachAdviceAndRejectsForbiddenAction() {
        var properties = new JevProperties();
        properties.setEnabled(true);
        properties.useCase(OrderAdvisoryQuestions.USE_CASE).setEnabled(true);
        var fake = new FakeJevTransport();
        var client = new JevClient(properties, fake);
        for (var code : OrderAdvisoryQuestions.Investigation.values()) {
            fake.reply(scriptedReply(code.name(), 1.0));
            var result = client.decide(OrderAdvisoryQuestions.USE_CASE, OrderAdvisoryFeatures.state(null), List.of(OrderAdvisoryQuestions.triage()));
            assertThat(result.status()).isEqualTo(JevResult.Status.OK);
            assertThat(result.answer(OrderAdvisoryQuestions.QUESTION_ID, JevAnswer.Choice.class).orElseThrow().choice()).isEqualTo(code.name());
            assertThat(result.transport()).isEqualTo("fake");
        }
        fake.reply(scriptedReply("RETRY_UNKNOWN_OPERATION", 1.0));
        assertThat(client.decide(OrderAdvisoryQuestions.USE_CASE, OrderAdvisoryFeatures.state(null), List.of(OrderAdvisoryQuestions.triage())).status())
                .isEqualTo(JevResult.Status.INVALID);
        assertThat(fake.requests()).hasSize(8).allSatisfy(request ->
                assertThat(request.state().values()).allSatisfy((key, value) -> assertThat(value).isEqualTo("UNKNOWN")));
    }

    @Test
    void offlineDisabledAndLowConfidenceOutcomesAreNotUsable() {
        var properties = new JevProperties();
        var fake = new FakeJevTransport();
        var client = new JevClient(properties, fake);
        assertThat(client.decide(OrderAdvisoryQuestions.USE_CASE, OrderAdvisoryFeatures.state(null), List.of(OrderAdvisoryQuestions.triage())).status())
                .isEqualTo(JevResult.Status.DISABLED);
        assertThat(fake.requests()).isEmpty();
        properties.setEnabled(true);
        properties.useCase(OrderAdvisoryQuestions.USE_CASE).setEnabled(true);
        fake.reply(scriptedReply("INSUFFICIENT_INFORMATION", 0.4));
        var result = client.decide(OrderAdvisoryQuestions.USE_CASE, OrderAdvisoryFeatures.state(null), List.of(OrderAdvisoryQuestions.triage()));
        assertThat(result.status()).isEqualTo(JevResult.Status.ABSTAINED);
        assertThat(result.usable()).isFalse();
    }

    private static String scriptedReply(String picked, double confidence) {
        var envelope = new ObjectMapper().createObjectNode();
        var answer = envelope.putObject(OrderAdvisoryQuestions.QUESTION_ID);
        answer.put("type", "choice").put("choice", picked).put("confidence", confidence);
        var probabilities = answer.putObject("probabilities");
        for (var code : OrderAdvisoryQuestions.Investigation.values()) {
            probabilities.put(code.name(), code.name().equals(picked) ? 1.0 : 0.0);
        }
        return envelope.toString();
    }

    @Test
    void investigationContractContainsOnlyApprovedAdviceCodes() {
        var question = OrderAdvisoryQuestions.triage();
        assertThat(question.id()).isEqualTo("investigation");
        assertThat(question.type()).isEqualTo("choice");
        assertThat(question.criteria().keySet()).containsExactly("CHECK_SOURCE", "CHECK_FULFILLMENT_EVIDENCE", "CHECK_ACTIVATION_ACK", "CHECK_FINANCE_ACK", "CHECK_DELIVERY_STATUS", "ESCALATE_INTEGRATION", "INSUFFICIENT_INFORMATION");
        assertThat(question.instructions()).contains("UNKNOWN means missing knowledge", "Do not authorize retries", "Abstain");
        assertThat(OrderAdvisoryQuestions.USE_CASE).isEqualTo("oms-exception-triage");
        assertThat(OrderAdvisoryQuestions.RUBRIC).isEqualTo("oms-exception-triage-v1");
        assertThatThrownBy(() -> question.criteria().put("RETRY", "retry now")).isInstanceOf(UnsupportedOperationException.class);
    }
}
